# Codebase map — spring-kafka-payment-processor

Static map of the repository as of branch `feat/dlt-replay` (stacked on `feat/dlt-viewer` and `feat/per-app-db-credentials`). Built by reading Java `package`/`import` declarations and the Maven, Compose and migration files; no code was run. The generated Avro classes (`common.avro.*`) are not in the source tree and are therefore left out of the package graphs (their usage is listed in §6).

## 1. At a glance

| Module | Kind | Main classes (lines) | Test classes (lines) | Responsibility |
|---|---|---|---|---|
| `common-libraries` | jar | 2 (69) | 3 (126) | `PaymentMethod` enum, `PaymentEvent.avsc` (Avro schema → generated `PaymentEvent`), `ActuatorObservationPredicate`, shared test helper `PrometheusText` (test-jar) |
| `payment-db-migration` | jar (SQL only) | – | – | Flyway migrations V1–V4; applied by the `db-migrate` compose service, test dependency of the consumer |
| `payment-producer-api` | Spring Boot, :8081 | 26 (924) | 12 (1,478) | Account REST API (JPA + Redis cache) and the payment endpoint that publishes Avro events to Kafka |
| `payment-consumer-api` | Spring Boot, :8082 | 36 (1,383) | 29 (3,832) | Consumes `payment.events`, persists payments, dead-letters failures, exposes the DLT view and replay |

Totals: 153 tracked files, 64 main + 44 test Java classes (2,376 / 5,436 lines). Infrastructure lives in `docker-compose.yml`, `scripts/` (Postgres roles, Vault seed) and `monitoring/` (Prometheus, Grafana dashboards).

## 2. Runtime architecture

```mermaid
flowchart LR
  client([HTTP client])

  subgraph apps["Spring Boot apps (host JVMs)"]
    producer["payment-producer-api<br/>:8081"]
    consumer["payment-consumer-api<br/>:8082"]
  end

  subgraph infra["docker compose"]
    pg[("Postgres<br/>payments_db / schema payment")]
    redis[("Redis<br/>account cache")]
    vault["Vault (dev)<br/>per-app secrets"]
    kafka{{"Kafka<br/>payment.events<br/>payment.events.DLT"}}
    apicurio["Apicurio Registry<br/>Avro schemas"]
    migrate["db-migrate<br/>Flyway, one-shot"]
    kexp["kafka-exporter"]
    prom["Prometheus"]
    graf["Grafana"]
    zipkin["Zipkin"]
  end

  client -->|"POST /api/v1/accounts, /payments"| producer
  client -->|"GET dead-letters, POST replay"| consumer

  producer -->|"JPA as payment-producer-app<br/>(ACCOUNT only)"| pg
  producer -->|"@Cacheable account lookup"| redis
  producer -->|"Avro, key = transaction_id"| kafka
  producer -->|"register / validate schema"| apicurio
  producer -->|"secret/payment-producer-api"| vault

  kafka -->|"payment.events"| consumer
  consumer -->|"JPA as payment-consumer-app<br/>(PAYMENT only)"| pg
  consumer -->|"dead letters, replays, truncation"| kafka
  consumer -->|"decode Avro"| apicurio
  consumer -->|"secret/payment-consumer-api"| vault

  migrate -->|"V1–V4 as payment-admin"| pg
  kexp --> kafka
  prom -.->|"scrape /actuator/prometheus"| producer
  prom -.-> consumer
  prom -.-> kexp
  graf --> prom
  graf --> zipkin
  producer -.->|"traces"| zipkin
  consumer -.->|"traces"| zipkin
```

## 3. Maven modules

```mermaid
flowchart TD
  parent["spring-kafka-payment-processor<br/>(pom aggregator, BOMs, Lombok)"]
  common["common-libraries"]
  migration["payment-db-migration<br/>(Flyway SQL)"]
  producer["payment-producer-api"]
  consumer["payment-consumer-api"]

  parent --- common
  parent --- migration
  parent --- producer
  parent --- consumer
  producer -->|compile| common
  consumer -->|compile| common
  consumer -.->|test scope| migration
  consumer -.->|"test-jar (PrometheusText)"| common
```

## 4. Package layering

Arrows are "imports from" between packages of the same module, derived from the source.

### 4.1 payment-producer-api

```mermaid
flowchart TD
  controllers["controllers<br/>AccountController, PaymentController"]
  services["services<br/>AccountService, PaymentService"]
  dtoreq["api.dto.request"]
  dtores["api.dto.response"]
  exceptions["exceptions<br/>GlobalExceptionHandler + 3 exceptions"]
  repositories["repositories<br/>AccountRepository"]
  specs["repositories.specifications<br/>AccountSpecification"]
  entities["domain.entities<br/>Account"]
  config["config<br/>KafkaProducerConfig, RedisCacheConfig,<br/>KafkaProducerProperties, JPAConfiguration,<br/>ObservabilityConfig"]
  metrics["metrics<br/>PaymentMetrics, PublishOutcome"]
  common["common-libraries"]

  controllers --> services
  controllers --> dtoreq
  controllers --> dtores
  services --> repositories
  services --> specs
  services --> entities
  services --> dtoreq
  services --> dtores
  services --> exceptions
  services --> metrics
  services --> config
  repositories --> entities
  specs --> entities
  specs --> dtoreq
  exceptions --> dtores
  dtoreq --> entities
  dtores --> entities
  dtoreq --> common
  metrics --> common
  config --> common
```

### 4.2 payment-consumer-api

```mermaid
flowchart TD
  listeners["listeners<br/>PaymentEventListener"]
  services["services<br/>PaymentPersistenceService"]
  mappers["mappers<br/>PaymentEventMapper"]
  repositories["repositories<br/>PaymentRepository"]
  entities["domain.entities<br/>Payment"]
  controllers["controllers<br/>DeadLetterController,<br/>DeadLetterExceptionHandler"]
  dto["api.dto.request / response"]
  dlt["dlt<br/>reader, decoder, replayer,<br/>truncator, services, scheduler"]
  config["config<br/>KafkaConsumerErrorHandlingConfig,<br/>DeadLetterViewConfig, DeadLetterReplayConfig,<br/>*Properties, ObservabilityConfig"]
  metrics["metrics<br/>PaymentConsumerMetrics,<br/>RetryMetricsListener"]
  common["common-libraries"]

  listeners --> services
  services --> mappers
  services --> repositories
  services --> entities
  services --> metrics
  mappers --> entities
  repositories --> entities
  controllers --> dlt
  controllers --> dto
  dlt --> dto
  dlt --> metrics
  dlt --> config
  dto --> dlt
  config --> dlt
  config --> metrics
  config --> common
  dto --> common
  mappers --> common
  entities --> common
```

`dto → dlt` and `config → dlt` close two package cycles (see §7).

## 5. Main flows

### 5.1 Payment, happy path

```mermaid
sequenceDiagram
  autonumber
  participant C as HTTP client
  participant PC as PaymentController
  participant PS as PaymentService
  participant AS as AccountService (@Cacheable)
  participant K as Kafka payment.events
  participant L as PaymentEventListener
  participant PP as PaymentPersistenceService
  participant DB as Postgres PAYMENT

  C->>PC: POST /api/v1/payments
  PC->>PS: publishPayment(request)
  PS->>AS: findByNumber(source) / findByNumber(target)
  Note over AS: Redis cache-aside, 404 if missing
  PS->>K: send(key = transaction_id, Avro PaymentEvent)
  K-->>PS: ack (bounded by send-timeout)
  PS-->>C: 202 + transaction_code
  K->>L: PaymentEvent
  L->>PP: persist(event)
  PP->>DB: existsByTransactionId, then saveAndFlush
  Note over PP,DB: unique transaction_id makes it idempotent
```

### 5.2 Failure, dead letter, replay

```mermaid
flowchart TD
  ev["PaymentEvent on payment.events"] --> listener["PaymentEventListener"]
  listener -->|ok| paid[("PAYMENT row")]
  listener -->|"exception"| handler["DefaultErrorHandler<br/>exponential backoff"]
  handler -->|"DataIntegrityViolation<br/>(not retried)"| recoverer["DeadLetterPublishingRecoverer"]
  handler -->|"retries exhausted"| recoverer
  desr["Deserialization failure<br/>(poison pill, raw bytes)"] --> recoverer
  recoverer --> dlt{{"payment.events.DLT"}}

  dlt --> view["GET /api/v1/dead-letters<br/>10 oldest (DeadLetterReader)"]
  dlt --> daily["Scheduled replay, daily<br/>DeadLetterReplayScheduler"]
  dlt --> forced["POST /api/v1/dead-letters/replay<br/>list of transaction_ids"]
  daily --> replayer["DeadLetterReplayer<br/>republish key, bytes, non-DLT headers"]
  forced --> replayer
  replayer --> ev
  daily --> trunc["DeadLetterTruncator<br/>Admin.deleteRecords of the replayed prefix"]
  trunc --> dlt
```

## 6. Class inventory

### common-libraries
| Class | Role |
|---|---|
| `common.domain.PaymentMethod` | REST/DB enum with a mapping to the Avro enum |
| `common.observability.ActuatorObservationPredicate` | Keeps `/actuator*` out of HTTP metrics and traces |
| `PaymentEvent.avsc` (resource) | Avro schema; generates `common.avro.PaymentEvent` / `PaymentMethod`, used by 10 main classes |

### payment-producer-api
| Package | Classes |
|---|---|
| `controllers` | `AccountController` (CRUD, list with filters), `PaymentController` (POST payments) |
| `services` | `AccountService` (JPA, `@Cacheable`/`@CacheEvict`), `PaymentService` (validate accounts, build and publish the event) |
| `repositories` | `AccountRepository`, `specifications.AccountSpecification` |
| `domain.entities` | `Account` |
| `api.dto.request` | `CreateAccountRequestDTO`, `UpdateAccountRequestDTO`, `PageableAccountFilterRequestDTO`, `PaymentRequestDTO` |
| `api.dto.response` | `AccountResponseDTO`, `PaymentResponseDTO`, `ErrorResponse` |
| `exceptions` | `GlobalExceptionHandler`, `AccountNotFoundException`, `DuplicatedAccountException`, `PaymentPublishException` |
| `config` | `KafkaProducerConfig`, `KafkaProducerProperties`, `RedisCacheConfig`, `JPAConfiguration`, `ObservabilityConfig` |
| `metrics` | `PaymentMetrics`, `PublishOutcome` |

### payment-consumer-api
| Package | Classes |
|---|---|
| `listeners` / `services` / `mappers` | `PaymentEventListener` → `PaymentPersistenceService` → `PaymentEventMapper` |
| `repositories` / `domain.entities` | `PaymentRepository`, `Payment` |
| `config` | `KafkaConsumerErrorHandlingConfig` (error handler, DLT producers, topic), `KafkaConsumerProperties`, `DeadLetterViewConfig`, `DeadLetterReplayConfig`, `DeadLetterReplayProperties`, `ObservabilityConfig` |
| `dlt` (13 classes) | View: `DeadLetterReader`, `DeadLetterPayloadDecoder`, `DeadLetterPayload`, `DeadLetterService`. Replay: `DeadLetterReplayer`, `DeadLetterTruncator`, `DeadLetterReplayService`, `DeadLetterReplayScheduler`, `ReplayResult`, `ReplayOutcome`, `ReplaySummary`. Errors: `DeadLetterReadException`, `DeadLetterReplayException` |
| `controllers` | `DeadLetterController` (GET list, POST replay), `DeadLetterExceptionHandler` (503 problem detail) |
| `api.dto` | `ReplayDeadLettersRequest`; `DeadLetterMessageResponse`, `PaymentEventResponse`, `DeadLetterReplayResponse`, `DeadLetterReplayResultResponse` |
| `metrics` | `PaymentConsumerMetrics`, `RetryMetricsListener`, `DeadLetterKind`, `ReplayTrigger` |

### Data and infrastructure
| Artifact | What it defines |
|---|---|
| `V1`, `V2` | `ACCOUNT` (unique `number`, soft-delete `active` column); producer role gets CRUD on it |
| `V3` | `PAYMENT`: unique `transaction_id`, FKs from source and target account to `ACCOUNT(number)`, `amount > 0` |
| `V4` | consumer role gets `SELECT, INSERT` on `PAYMENT` |
| `scripts/init-db.sql` | roles `payment-admin`, `payment-producer-app`, `payment-consumer-app` and the `payment` schema |
| `scripts/init-vault.sh` | one secret per app, `secret/payment-producer-api` and `secret/payment-consumer-api` |
| `monitoring/` | Prometheus scrape config, 3 Grafana dashboards, traffic generator and screenshot scripts |

## 7. Structural notes

These come straight from the graphs above; they are observations, not a code review.

1. **Package cycles in the consumer.** `dlt ↔ api.dto.response` (`DeadLetterMessageResponse.of` takes a `DeadLetterPayload`, while `DeadLetterService` returns response DTOs) and `config ↔ dlt` (`DeadLetterReplayService` reads `DeadLetterReplayProperties`, while `DeadLetterReplayConfig` builds `dlt` beans).
2. **Persistence depends on the API layer in the producer.** `AccountSpecification` imports `PageableAccountFilterRequestDTO`; DTOs in turn import the `Account` entity (`toEntity`, `from`).
3. **`ObservabilityConfig` is duplicated.** The producer and consumer copies are identical except for the `package` line; both only register `ActuatorObservationPredicate`, which already lives in `common-libraries`.
4. **Base-package casing differs by module.** Apps use `com.joaoPBessa…`, `common-libraries` uses `com.joaopBessa…`.
5. **Size is concentrated in the consumer.** It holds 36 of the 64 main classes, 13 of them in `dlt`, and 3,832 of the 5,436 test lines (tests are about 2.8× the main code there, about 1.6× in the producer).
6. **One schema ties the modules together.** The Avro `PaymentEvent` is used by 10 main classes across both apps; changing `PaymentEvent.avsc` affects the producer, the listener, the DLT view and the replay.

## 8. Tests as intent signals

Tests were read as descriptions of intent, not reviewed.

| Module | Test classes | What they establish |
|---|---|---|
| `common-libraries` | `ActuatorObservationPredicateTest`, `PaymentMethodTest` | enum mapping and metric filtering rules |
| `payment-producer-api` | `AccountControllerTest`, `PaymentControllerTest`, `AccountServiceTest`, `PaymentServiceTest`, `PaymentMetricsTest`, `KafkaProducerBrokerDownTest`, `PaymentPublishingIntegrationTest`, `PaymentProducerApiApplicationTests`, `JsonLogsProfileTest`, `VaultBootstrapConfigTest` | REST contract and error mapping, publish-with-timeout behavior, key = transaction id, end-to-end Avro round trip, per-app Vault secret |
| `payment-consumer-api` | `PaymentEventListenerTest`, `PaymentPersistenceServiceTest`, `PaymentEventMapperTest`, `KafkaConsumerErrorHandlingConfigTest`, `KafkaConsumerPropertiesTest`, `PaymentConsumerMetricsTest`, `RetryMetricsListenerTest`, `PaymentRepositoryTest`, `PaymentTableMigrationTest`, `DatabasePrivilegesTest`, `PaymentConsumerIntegrationTest` | idempotent persistence, retry and dead-letter rules, constraints in the real schema, least-privilege roles, listener-to-database behavior under the consumer role |
| `payment-consumer-api` (DLT) | `DeadLetterReaderTest`, `DeadLetterPayloadDecoderTest`, `DeadLetterServiceTest`, `DeadLetterControllerTest`, `DeadLetterReplayerTest`, `DeadLetterTruncatorTest`, `DeadLetterReplayServiceTest`, `DeadLetterReplaySchedulerTest`, `DeadLetterReplayPropertiesTest`, `DeadLetterEndpointIntegrationTest`, `DeadLetterReplayIntegrationTest` | read-only view of the 10 oldest, daily replay with truncation, forced replay by id, scheduling and configuration |

Test infrastructure classes (not tests): `TestcontainersConfiguration`, `TestPaymentProducerApiApplication`, `PostgresContainerConfiguration`, `KafkaInfrastructureConfiguration`, `ConsumerDatabaseRoleConfiguration`, `KafkaTestClients`, `PrometheusText`.

## 9. Where to start reading

- **Request path:** `PaymentController` → `PaymentService` → `KafkaProducerConfig`.
- **Processing path:** `PaymentEventListener` → `PaymentPersistenceService` → `KafkaConsumerErrorHandlingConfig`.
- **Operations path:** `DeadLetterController` → `DeadLetterReplayService` → `DeadLetterReader` / `DeadLetterReplayer` / `DeadLetterTruncator`.
- **Contracts:** `common-libraries/src/main/avro/PaymentEvent.avsc` and `payment-db-migration/src/main/resources/db/migration/`.
- **Project rules and status:** `CLAUDE.md` (§5 lists what is implemented, §8 the known debt).

## 10. Limits of this map

- Package edges come from `import` statements of project code only; Spring wiring by type or by name (beans, `@Value`, events) is described from the configuration classes, not computed.
- Class-level call graphs and endpoint traces were not built (the Cartographer scripts do not parse Java).
- Line counts include blank lines and imports.
