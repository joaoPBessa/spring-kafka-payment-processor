# Payment Producer API

The Spring Boot service in this repository: an account-management REST API and an Avro-serialized Kafka payment-event publisher.

## Tech Stack

- **Runtime:** Java 21
- **Framework:** Spring Boot 4.0.0, Spring Cloud 2025.1.1
- **Database:** PostgreSQL 15, targeting a dedicated `payment` schema
- **Migration:** Flyway (owned by the sibling `payment-db-migration` module)
- **Messaging:** Apache Kafka broker + Apicurio Registry (Avro), both provisioned via `docker-compose.yml`; synchronous producer in `PaymentService`
- **Secrets:** HashiCorp Vault (Spring Cloud Vault)
- **Caching:** Redis (Spring Data Redis)
- **Observability:** Spring Boot Actuator, Micrometer with a Prometheus registry, tracing (Brave) exported to Zipkin, Grafana dashboards, `json-logs` profile for structured logs
- **Testing:** JUnit 5/6, Mockito, AssertJ, Testcontainers (Kafka, Zipkin), H2

## Architecture Highlights

- **Dedicated schema:** the datasource targets a `payment` schema, separate from `public`.
- **JPA auditing:** `created_at`/`updated_at` on `Account` are populated automatically via `@EnableJpaAuditing` and `@EntityListeners(AuditingEntityListener.class)` — no manual timestamp handling.
- **Cache-aside:** account lookups are `@Cacheable`, with eviction on rename/delete — also reused by `PaymentService` to validate that both accounts on a payment exist before publishing anything.
- **Schema-governed publishing:** `PaymentService` builds an Avro-generated `PaymentEvent` and publishes it synchronously via a typed `KafkaTemplate<String, PaymentEvent>`, so only schema-conformant events can ever reach the topic. Each event is keyed by its `transaction_id`, which identifies it in the topic and in the consumer's dead letter topic.
- **Centralized error handling:** a single `@RestControllerAdvice` maps validation, not-found, conflict, malformed-request, and publish-failure cases to consistent 400/404/409/503/500 responses.

## Getting Started

### Prerequisites
- JDK 21
- Maven
- Docker & Docker Compose (for local infrastructure and for the Testcontainers-backed test)

### 1. Start local infrastructure

```bash
docker compose up -d --wait
docker compose ps -a db-migrate   # the one-shot migration must show "Exited (0)"
```

This starts Postgres, the one-shot `db-migrate` step, Redis, Vault, Kafka, and Apicurio Registry (in-memory, non-persistent). The broker comes up with no topics pre-created — `payment.events` is created on first publish (`auto.create.topics.enable`, the broker's default).

> The credentials in `docker-compose.yml` are throwaway local-development values only — not meant for any real environment.

### 2. Database initialization

Flyway owns schema versioning, split between a Docker bootstrap step and the migrations themselves. The application never migrates the database and holds no admin credential:

- **Infrastructure setup (automated):** `docker compose up` runs `scripts/init-db.sql` on an empty volume, which creates the `payments_db` database, three distinct users (`payment-admin` for migrations, `payment-producer-app` and `payment-consumer-app` for the applications), and the `payment` schema with the appropriate grants.
- **Schema migration:** the `db-migrate` compose service (official Flyway image) connects as `payment-admin` and runs the scripts in `payment-db-migration/src/main/resources/db/migration`, including the grants that confine this application to `SELECT/INSERT/UPDATE/DELETE` on `ACCOUNT`.
- **Credentials:** the application reads `payment-producer-app` from its own Vault secret, `secret/payment-producer-api` (seeded by `scripts/init-vault.sh`).

### 3. Run the application

```bash
mvn spring-boot:run
# or, using the module-local wrapper:
./mvnw spring-boot:run
```

## Project Structure

```text
com/joaoPBessa/payments/producer
├── PaymentProducerApiApplication.java
├── api/dto/
│   ├── request/    # CreateAccountRequestDTO, UpdateAccountRequestDTO, PageableAccountFilterRequestDTO, PaymentRequestDTO
│   └── response/   # AccountResponseDTO, PaymentResponseDTO, ErrorResponse
├── config/         # JPAConfiguration (@EnableJpaAuditing), RedisCacheConfig, KafkaProducerConfig, KafkaProducerProperties
├── controllers/    # AccountController, PaymentController
├── domain/entities/# Account
├── exceptions/     # AccountNotFoundException, DuplicatedAccountException, PaymentPublishException, GlobalExceptionHandler
├── repositories/   # AccountRepository, specifications/AccountSpecification
└── services/       # AccountService, PaymentService
```

## API Endpoints

All endpoints are versioned under `/api/v1`.

### Accounts (`/api/v1/accounts`)

| Method | Endpoint | Description | Success | Errors |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/accounts/` *(note the trailing slash)* | Create an account. Returns a `Location` header. | `201` | `400`, `409` |
| `PATCH` | `/api/v1/accounts/{accountNumber}` | Rename the account. | `204` | `400`, `404` |
| `DELETE` | `/api/v1/accounts/{accountNumber}` | Soft-delete the account (`active = false`). | `204` | `404` |
| `GET` | `/api/v1/accounts/{accountNumber}` | Look up an active account. Cached. | `200` | `404` |
| `GET` | `/api/v1/accounts` | Paginated list with `Specification`-based dynamic filtering (`account_number`, `account_name`, `active`, `page`, `size`). | `200` | `400` |

### Payments (`/api/v1/payments`)

| Method | Endpoint | Description | Success | Errors |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/payments` | Validates the request, checks both accounts exist (cached), and publishes an Avro-encoded event to Kafka. Returns a generated transaction code (also the Kafka record key). | `202` | `400`, `404`, `503` |

## Observability

`/actuator/health` and `/actuator/prometheus` are exposed on the application port (8081). Every meter carries an `application` tag, HTTP request histograms include 0.5 s and 2 s buckets (the Apdex thresholds), and traces are fully sampled and sent to Zipkin. `/actuator/**` calls are excluded from metrics and traces.

Custom metrics:

| Metric | Meaning |
|---|---|
| `payments_publish_seconds{outcome}` | Time until the broker confirms the event; `outcome` is `success`, `timeout` or `error` |
| `payments_accepted_total{payment_method}` | Payments accepted and published |
| `payments_amount` | Distribution of the accepted amounts |
| `cache_gets_total{cache="accounts",result}` | Account cache hits and misses |

The Kafka template is observed, so the trace context travels to the consumer in the `traceparent` header. Run with `-Dspring-boot.run.profiles=json-logs` for ECS JSON logs (SQL printing is switched off in that profile so every line stays JSON). Dashboards, Prometheus and Zipkin are described in the [root README](../README.md#observability).

## Testing

- **Slice tests** (`@WebMvcTest`) for both controllers: `AccountControllerTest` (15 tests), `PaymentControllerTest` (14 tests).
- **Unit tests**: `AccountServiceTest` (9 tests), `PaymentServiceTest` (5 tests) — plain Mockito, no Spring context.
- **Integration tests**: `PaymentProducerApiApplicationTests` boots the full context against Testcontainers-managed Kafka, Redis, Zipkin and Apicurio containers (the registry URL reaches the context through a `DynamicPropertyRegistrar`, so the dynamic port is wired automatically); `PaymentPublishingIntegrationTest` (2 tests) publishes over real HTTP and confirms the exact Avro event round-trips through a real Kafka broker and Apicurio Registry.

```bash
mvn test      # unit + slice tests
mvn verify    # also runs the Testcontainers-backed integration test (requires Docker)
```

## Roadmap

- Alerting on top of the metrics and a Kafka monitoring UI are not part of the stack yet.
