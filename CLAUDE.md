# CLAUDE.md

Guidance for Claude Code when working in this repository. This reflects the **actual state of the code**; the READMEs were brought in line with it.

Before implementing anything, load the `plan-activity` skill (`.claude/skills/plan-activity/SKILL.md`) — it defines the TDD workflow and quality bar (readability, no duplication, ≤4 params per method, green build) that all work in this repo must follow.

## 1. What this project is

A multi-module Maven monorepo simulating a payment-processing platform: account management (implemented) + an event-driven payment engine over Kafka (implemented — Avro-serialized events, Apicurio Registry-validated, account-existence checked via cache before publishing). It is a **portfolio project** aimed at demonstrating senior-level Java/Spring backend skills for an international audience — treat naming, comments, commit messages, and log output as public-facing artifacts, not throwaway dev scratch.

Root Maven coordinates: `com.joaopbessa.payments:spring-kafka-payment-processor:1.0.0-SNAPSHOT` (`pom.xml`, `packaging=pom`).

## 2. Module map

| Module | Type | Responsibility |
|---|---|---|
| `common-libraries` | jar | Shared domain primitives: `PaymentMethod` enum (Jackson-annotated, and now also carrying a mapping to its Avro-generated counterpart) plus `PaymentEvent.avsc` — the Avro schema for the Kafka payment event, compiled via `avro-maven-plugin` into `target/generated-sources/avro` (never committed) — and `ActuatorObservationAutoConfiguration`, a Spring Boot auto-configuration (registered in `META-INF/spring/…AutoConfiguration.imports`) that gives both apps the predicate keeping `/actuator*` out of HTTP metrics and traces, so neither app declares it. |
| `payment-db-migration` | jar (Flyway scripts + plugin config) | Owns Flyway SQL migrations (`src/main/resources/db/migration`) and the `flyway-maven-plugin` binding, decoupled from the runtime app module. Locally the `db-migrate` compose service applies them with `payment-admin`; the apps never run Flyway (only the consumer's tests do, with this module on the test classpath). |
| `payment-producer-api` | Spring Boot app | REST API, JPA persistence, Redis cache, Vault-backed config, Avro-serialized Kafka producer. |
| `payment-consumer-api` | Spring Boot app (Actuator + read-only DLT view, port 8082) | Consumes `payment.events`, persists payments to the `PAYMENT` table idempotently, retries with exponential backoff and dead-letters to `payment.events.DLT`. Has its own Vault secret (`secret/payment-consumer-api`) and database role (`payment-consumer-app`, SELECT/INSERT on `PAYMENT` only); the producer's are `secret/payment-producer-api` and `payment-producer-app` (CRUD on `ACCOUNT` only). |

Dependency direction: both apps depend on `common-libraries`; `payment-db-migration` is only a test dependency of the consumer. The Spring Cloud BOM, Apicurio version and Lombok compiler config live in the root `pom.xml`.

## 3. Tech stack (as pinned in POMs, not as advertised in the README)

- **Java 21**, Spring Boot **4.0.0** (parent `pom.xml`)
- Spring Cloud **2025.1.1** (root `pom.xml`, imported as a BOM for both apps)
- Persistence: Spring Data JPA + PostgreSQL 15, Flyway migrations, dedicated `payment` schema
- Cache: Spring Data Redis + `spring-boot-starter-cache`, manual `RedisCacheManager` bean (JSON value serialization, 10-minute TTL, null-caching disabled)
- Config/secrets: Spring Cloud Vault (`spring-cloud-starter-vault-config` + `spring-cloud-starter-bootstrap`, token auth, KV v2 backend) — see `bootstrap.yml`
- Messaging: `spring-boot-starter-kafka` + Apicurio's `apicurio-registry-avro-serde-kafka` (3.0.8) client. `PaymentController` synchronously publishes Avro-encoded `PaymentEvent`s (schema in `common-libraries/src/main/avro/PaymentEvent.avsc`) to the `payment.events` topic via `PaymentService`/`KafkaProducerConfig`, backed by a single-node Kafka broker (`apache/kafka:4.1.1`, KRaft) and an Apicurio Registry (in-memory) container, both in `docker-compose.yml` (see §5)
- Observability: Actuator + `micrometer-registry-prometheus` in both apps (`/actuator/prometheus`, tag `application`), tracing via Brave exported to Zipkin with the Kafka hop propagated through Spring Kafka observation (`traceparent`), `json-logs` profile for ECS structured logs. The `/actuator*` exclusion comes from the shared `ActuatorObservationPredicate` (see §2). `docker-compose.yml` runs Prometheus, Grafana (dashboards in `monitoring/grafana/dashboards`), Zipkin and a Kafka exporter. In `@SpringBootTest` Brave only creates its propagation factory while trace export is enabled, so tests that need propagation use `@AutoConfigureTracing` and turn off only `management.tracing.export.zipkin`.
- Validation: Jakarta Bean Validation (`spring-boot-starter-validation`)
- Lombok for entity/boilerplate reduction (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`)
- Tests: JUnit **6** (via `junit-jupiter` 6.0.1, managed by the Spring Boot BOM), Mockito, AssertJ, `spring-boot-testcontainers`, `testcontainers-kafka`, H2 (in-memory, for lighter contexts), `@WebMvcTest` slice tests
- Jackson: global `SNAKE_CASE` property naming strategy (`application.yaml`) — all JSON request/response fields are snake_case even though Java fields are camelCase

## 4. Runtime configuration

- `application.yaml`: datasource points at `payments_db` on `payment` schema via Hikari, JPA `show-sql`/`format_sql` on, Redis on `localhost:6379`. Neither app runs Flyway at startup (migrations come from the `db-migrate` compose service), so an app started before the migrations finish fails on its first database access.
- `bootstrap.yml`: Vault client config (`localhost:8200`, dev token `root-token-local`, KV backend `secret`) — loaded before `application.yaml` per Spring Cloud bootstrap phase. Each app sets `spring.cloud.vault.application-name` to its own name, so it reads only its own secret (`secret/payment-producer-api`, `secret/payment-consumer-api`); a missing secret does not fail startup (`fail-fast` is off) and the driver falls back to the OS user, which shows up as `role "<os user>" does not exist`.
- `src/test/resources/application-test.yaml`: H2 in-memory Postgres-compatibility mode, `ddl-auto: update` (schema generated by Hibernate for tests instead of Flyway).
- `docker-compose.yml` at repo root brings up **Postgres, Redis, Vault (+ a `vault-init` sidecar that seeds secrets), Kafka** (`apache/kafka:4.1.1`, single-node KRaft broker with dual `PLAINTEXT_HOST`/`PLAINTEXT` listeners for host and container access, no auth, no topics pre-created), **and Apicurio Registry** (`apicurio/apicurio-registry:3.3.2`, in-memory storage — no persistence, resets on restart). It also provisions **Zipkin, a Kafka exporter, Prometheus and Grafana** (see §3); Prometheus scrapes the apps on the host via `host.docker.internal`.

## 5. Feature status — what actually works vs. what's a stub

### Implemented (Account domain, `AccountController` / `AccountService`)
- `POST /api/v1/accounts/` — create account, 201 + `Location` header, duplicate-number check (`DuplicatedAccountException` → 409)
- `PATCH /api/v1/accounts/{accountNumber}` — rename only, 204 (note: **not** `/accounts/{number}/owner` as the README claims)
- `DELETE /api/v1/accounts/{accountNumber}` — soft delete via bulk `UPDATE ... SET active = false`, 204, 404 if no rows affected
- `GET /api/v1/accounts/{accountNumber}` — active accounts only, `@Cacheable`
- `GET /api/v1/accounts` — paginated + `Specification`-based dynamic filtering (`AccountSpecification`), snake_case query params bound via `@BindParam`
- Redis cache-aside on lookup, evicted on rename/delete (`RedisCacheConfig.ACCOUNT_NAME` / `ACCOUNT_KEY`)
- Global exception handling (`GlobalExceptionHandler`): validation errors → structured field-level 400, `AccountNotFoundException` → 404, `DuplicatedAccountException` → 409, `PaymentPublishException` → 503, catch-all → 500

### Implemented (Payment domain, `PaymentController` / `PaymentService`)
- `POST /api/v1/payments` — validates the request, checks both `sourceAccount` and `targetAccount` exist via `AccountService#findByNumber` (reusing its `@Cacheable` lookup — no fresh query, no new cache), builds an Avro `PaymentEvent`, and publishes it synchronously (bounded by `app.kafka.producer.send-timeout`) to the `payment.events` topic. Returns 202 with a generated transaction id; 404 if either account doesn't exist; 503 if the broker doesn't confirm in time.
- The `KafkaTemplate<String, PaymentEvent>` is typed to the Avro-generated class, so nothing but a schema-conformant `PaymentEvent` can ever reach `.send(...)` from this codebase — enforcement happens both at compile time (the generic type) and at serialization time (Apicurio's `AvroKafkaSerializer` encodes strictly against the registered schema).
- `PaymentMethod` (domain enum, `common-libraries`) carries its Avro-generated counterpart per constant (`toAvroPaymentMethod()`) — the REST↔wire mapping lives in the enum itself, not in service code.

### Implemented (Payment consumer, `payment-consumer-api`)
- `PaymentEventListener` → `PaymentPersistenceService.persist` (checks `existsByTransactionId`, `saveAndFlush`, treats a unique-violation race as a duplicate). `PAYMENT` (Flyway V3) enforces unique `transaction_id`, FKs to `ACCOUNT(NUMBER)` for both accounts and `amount > 0`.
- `KafkaConsumerErrorHandlingConfig`: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (templates keyed by value type: `PaymentEvent` via Avro, `byte[]` for poison pills), `ExponentialBackOffWithMaxRetries(maxAttempts - 1)`, `DataIntegrityViolationException` not retryable. Retry settings live in `KafkaConsumerProperties` (`app.kafka.*`, validated).
- Custom Kafka producers must apply `KafkaConnectionDetails` themselves (see `KafkaProducerConfig` and this config), otherwise Testcontainers' `@ServiceConnection` broker is ignored. `spring.kafka.bootstrap-servers` and the datasource URL are gated to the `!test` profile in the consumer. The consumer's `application-test.yaml` turns Flyway on and Vault off; its integration test runs the app pool as `payment-consumer-app` (`SET ROLE` through Hikari's `connection-init-sql`) while Flyway and fixtures use the container admin (`ConsumerDatabaseRoleConfiguration`). Consumer Testcontainers boot Postgres from the real `scripts/init-db.sql`, so roles and grants are the production ones; `DatabasePrivilegesTest` pins the privilege matrix.

- Events are published with **key = `transaction_id`** (`PaymentService`), so the key of a dead letter identifies the payment; events published before that change are keyed by the source account.
- The Avro namespace is `com.joaopbessa.payments.common.avro` (it used to be `com.joaopBessa…`). Messages written under the old namespace cannot be decoded any more, because Apicurio resolves the specific reader class by the writer schema's full name (Avro aliases do not help here). After pulling that change, reset the local Kafka volume (`docker compose down`, then remove `spring-kafka-payment-processor_kafka_data`) and flush Redis, whose cached values embed class names.
- `GET /api/v1/dead-letters` (consumer, port 8082) — returns the 10 oldest messages of `payment.events.DLT` (partition, offset, key, timestamp, decoded `payload` or `raw_payload` for poison pills), snake_case JSON (`spring.jackson.property-naming-strategy` is set in the consumer too). `DeadLetterReader` reads with `assign` + `seekToBeginning` on a throwaway `KafkaConsumer` (no group, no commits, `allow.auto.create.topics=false`), `DeadLetterPayloadDecoder` decodes through Apicurio, `DeadLetterService` returns domain `DeadLetterMessage`s (the controller maps them to `DeadLetterMessageResponse`), `DeadLetterViewConfig` wires the reader and decoder (bootstrap from `KafkaConnectionDetails.getConsumer()`). No authentication; no error classification and no account lookup by design. Read failures → 503 `ProblemDetail` (`DeadLetterExceptionHandler`).
- DLT replay (consumer): the DLT is the **backlog of pending messages**. `DeadLetterReplayScheduler` (`app.dlt-replay.*`, default `0 0 3 * * *` UTC, off in tests) calls `DeadLetterReplayService.replayPending()`: `DeadLetterReader.pending` snapshot → `DeadLetterReplayer` republishes original key/bytes/non-`kafka_dlt-*` headers to `payment.events` in offset order, stopping a partition at its first failure → `DeadLetterTruncator` (`Admin.deleteRecords`) removes the replayed prefix. A message that fails again returns to the DLT as a new record; no attempt limit yet. `POST /api/v1/dead-letters/replay` (`{"transaction_ids": [...]}`, 1–100 ids, 202 with `replayed`/`not_found`/`failed` per id, 400/503 as `ProblemDetail`, enabled via `spring.mvc.problemdetails.enabled`) republishes the most recent DLT record whose key is each id, reading the topic once; it does not truncate (the record is cleared by the next daily run, replayed again as an idempotent duplicate). Replays are counted in `payments_dead_letter_replayed_total{trigger,result}`. `DeadLetterReplayService` takes the batch size as a plain `int`, and its bean (with the replayer and truncator) is declared in `DeadLetterReplayConfig`. Single-instance assumption (no distributed lock).

### Stub / not implemented
- No mapper layer (README describes a `mapper/` package with MapStruct; in reality DTOs do their own mapping via static factory methods / instance `toEntity()` — MapStruct is not even a dependency).
- No service interface + impl split (README describes `service/impl`; actual code is a single concrete `AccountService` class).
- No alerting (Alertmanager) and no Kafka monitoring UI.
- No Cucumber/BDD tests despite being in the roadmap and README testing-strategy table.

## 6. Package & naming conventions (and inconsistencies to watch)

- Base package and Maven `groupId` are `com.joaopbessa.payments…` in every module, all lower case (the Java and Maven convention). `PackageNamingTest` (one per module, backed by the `PackageNaming` helper in the `common-libraries` test-jar) fails if a main, generated or test package gets an upper-case letter.
- Layering inside `payment-producer-api`: `api.dto.request` / `api.dto.response`, `controllers`, `services`, `domain.entities`, `repositories` (+ `repositories.specifications`), `exceptions`, `config`. This is a flatter layout than the `controller/v1`, `service/impl`, `mapper` structure documented in the README — the README describes a target structure, not the current one.
- No package cycles (checked from the imports): in the consumer the direction is `controllers → api.dto → dlt` and `config → dlt`, and `dlt` imports neither `config` nor `api`. Keep it that way: map to DTOs in the controller layer, and pass configuration values into `dlt` classes instead of importing `*Properties`.
- Source, logs, comments, scripts and `.gitignore` are in English.
- DTOs are Java `record`s; entities are Lombok classes (`@Data @Builder @AllArgsConstructor @NoArgsConstructor`) — standard, intentional split between immutable transport objects and mutable JPA entities.
- Route inconsistency: `POST /api/v1/accounts/` has a trailing slash; every other account route does not.
- Custom runtime exceptions (`AccountNotFoundException`, `DuplicatedAccountException`) both declare an unused `serialVersionUID` on a `RuntimeException` subtype — harmless but not idiomatic (that field matters for `Serializable`, not for exceptions used purely in-process).

## 7. Build, run, test

```bash
# From repo root — build all modules
mvn -B clean verify

# Run the apps locally (requires `docker compose up -d --wait` first: postgres, db-migrate, redis, vault, kafka, apicurio;
# confirm migrations with `docker compose ps -a db-migrate` — `up -d` returns 0 even if the one-shot migration fails)
# `init-db.sql` only runs on an empty volume; to pick up new roles: docker compose rm -sf postgres && docker volume rm spring-kafka-payment-processor_postgres_data
# The producer listens on 8081 (8080 is Apicurio's host port); the consumer listens on 8082 (Actuator and the dead letter endpoints)
cd payment-producer-api && mvn spring-boot:run
cd payment-consumer-api && mvn spring-boot:run

# Run tests only
mvn test
```

CI (`.github/workflows/maven.yml`): GitHub Actions on push to `main`/`develop` and on PRs, `ubuntu-latest`, Java 21 (Temurin), 15-minute timeout, `TZ=UTC`, runs `mvn -B clean verify` with `SPRING_CLOUD_VAULT_ENABLED=false` and `SPRING_CLOUD_BOOTSTRAP_ENABLED=false` (Vault is not available there). Docker is preinstalled on the runner, so Testcontainers works without extra setup. To reproduce CI locally, stop `docker compose` first (a running local Kafka/Redis can mask Testcontainers wiring bugs) and export the same env vars.

### Test conventions in place
- Do not write test counts in the READMEs or here: they go stale. The CI job summary (`scripts/test-summary.sh`, which reads the surefire reports) publishes the real totals of every run; run it locally after `mvn verify` to see them.
- `@WebMvcTest` slices for controllers, mocking the service layer with `@MockitoBean`, asserting on `MockMvc` + `jsonPath`
- Producer `TestcontainersConfiguration` provisions Kafka (`apache/kafka:4.1.1`) and Redis via `@ServiceConnection`, plus a `GenericContainer` for Apicurio Registry whose URL is exposed through a `DynamicPropertyRegistrar` bean (no Boot `ConnectionDetails` factory exists for Apicurio). The consumer has its own `PostgresContainerConfiguration` and `KafkaInfrastructureConfiguration` with the same pattern.
- Integration tests must read broker addresses from `KafkaConnectionDetails`, never from `spring.kafka.bootstrap-servers` (the Testcontainers broker is not a property).
- H2 (`application-test.yaml`) used for lighter contexts that don't need a real Postgres/Testcontainers instance
- `TestPaymentProducerApiApplication` — a Testcontainers-friendly bootstrap entrypoint for local dev (`SpringApplication.from(...).with(TestcontainersConfiguration.class).run(...)`)

## 8. Known issues / technical debt (flagged for a senior-level review)

1. **Secrets committed to git and already pushed to the public GitHub remote** (`origin` = `github.com/joaoPBessa/spring-kafka-payment-processor`): `docker-compose.yml` has a plaintext Postgres password, `scripts/init-db.sql` has plaintext passwords for three DB users (admin, producer, consumer), `scripts/init-vault.sh` has the Vault dev root token and re-embeds the same DB passwords. Known and deliberately deferred for now (docker-local-only values) — do not "fix" this unless asked.
2. **Project's namesake feature (Kafka) is now implemented** — `PaymentController` publishes Avro-encoded events through a real broker and Schema Registry (§5). Remaining gaps in this area: no topic-creation/partition-count tuning beyond the broker's `auto.create.topics.enable` default, no Confluent Control Center-equivalent monitoring UI. The Apicurio dynamic port is now wired through a `DynamicPropertyRegistrar` in the test configuration.
3. **Producer persistence depends on the API layer**: `AccountSpecification` imports `PageableAccountFilterRequestDTO`, and the request/response DTOs import the `Account` entity (`toEntity`, `from`). Not a cycle, but the repository layer should not know a request DTO.
4. **Nothing enforces the dependency rules** (no ArchUnit or similar): only the package casing is guarded by a test; the absence of package cycles was verified by reading the imports, so it can regress silently.

## 9. Roadmap snapshot (`RoadmapProducerAPI.md`)

Done: core Account CRUD, pageable/specification filtering, soft delete.
Not started: global exception handler is listed as not-done in the roadmap checklist but **is actually implemented** — the roadmap checklist itself is stale in places, cross-check against code before trusting it. Outstanding: Bean Validation i18n, Redis in docker-compose (done), Vault→docker-compose (done), Kafka broker + Apicurio Registry in docker-compose (done — see §4), Avro producer with account-existence validation (done — see §5), structured logging/Micrometer/Grafana/Prometheus, BDD test coverage expansion.
