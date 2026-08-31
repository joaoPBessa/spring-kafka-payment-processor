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
- **Observability:** Spring Boot Actuator, Micrometer tracing (Brave), Zipkin — dependencies are in place, but no custom metrics or dashboards are configured yet
- **Testing:** JUnit 5/6, Mockito, AssertJ, Testcontainers (Kafka, Zipkin), H2

## Architecture Highlights

- **Dedicated schema:** the datasource targets a `payment` schema, separate from `public`.
- **JPA auditing:** `created_at`/`updated_at` on `Account` are populated automatically via `@EnableJpaAuditing` and `@EntityListeners(AuditingEntityListener.class)` — no manual timestamp handling.
- **Cache-aside:** account lookups are `@Cacheable`, with eviction on rename/delete — also reused by `PaymentService` to validate that both accounts on a payment exist before publishing anything.
- **Schema-governed publishing:** `PaymentService` builds an Avro-generated `PaymentEvent` and publishes it synchronously via a typed `KafkaTemplate<String, PaymentEvent>`, so only schema-conformant events can ever reach the topic.
- **Centralized error handling:** a single `@RestControllerAdvice` maps validation, not-found, conflict, malformed-request, and publish-failure cases to consistent 400/404/409/503/500 responses.

## Getting Started

### Prerequisites
- JDK 21
- Maven
- Docker & Docker Compose (for local infrastructure and for the Testcontainers-backed test)

### 1. Start local infrastructure

```bash
docker-compose up -d
```

This starts Postgres, Redis, Vault, Kafka, and Apicurio Registry (in-memory, non-persistent). The broker comes up with no topics pre-created — `payment.events` is created on first publish (`auto.create.topics.enable`, the broker's default).

> The credentials in `docker-compose.yml` are throwaway local-development values only — not meant for any real environment.

### 2. Database initialization

Flyway owns schema versioning, split between a Docker bootstrap step and the migrations themselves:

- **Infrastructure setup (automated):** `docker-compose up` runs `scripts/init-db.sql`, which creates the `payments_db` database, two distinct users (`payment-admin` for migrations, `payment-producer-app` for the application), and the `payment` schema with the appropriate grants.
- **Schema migration:** on application startup, Flyway connects as `payment-admin` and runs the scripts in `payment-db-migration/src/main/resources/db/migration` (`V1__create_account_table.sql`, `V2__create_active_account_column.sql`).

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
| `POST` | `/api/v1/payments` | Validates the request, checks both accounts exist (cached), and publishes an Avro-encoded event to Kafka. Returns a generated transaction code. | `202` | `400`, `404`, `503` |

## Observability

`spring-boot-starter-actuator`, Micrometer tracing (Brave), and Zipkin are on the classpath, but nothing beyond Spring Boot's defaults is configured — no custom metrics, no dashboards. Wiring these up is part of the [roadmap](#roadmap).

## Testing

- **Slice tests** (`@WebMvcTest`) for both controllers: `AccountControllerTest` (15 tests), `PaymentControllerTest` (14 tests).
- **Unit tests**: `AccountServiceTest` (9 tests), `PaymentServiceTest` (5 tests) — plain Mockito, no Spring context.
- **Integration tests**: `PaymentProducerApiApplicationTests` boots the full context against Testcontainers-managed Kafka and Zipkin containers; `PaymentPublishingIntegrationTest` (2 tests) publishes over real HTTP and confirms the exact Avro event round-trips through a real Kafka broker and Apicurio Registry.

```bash
mvn test      # unit + slice tests
mvn verify    # also runs the Testcontainers-backed integration test (requires Docker)
```

## Roadmap

- Wire up custom Micrometer metrics and dashboards on top of the observability dependencies already in place.
- `TestPaymentProducerApiApplication`'s Apicurio Testcontainers bean has no `@ServiceConnection` (Spring Boot ships no `ConnectionDetails` factory for Apicurio), so its dynamic port isn't auto-wired for local dev — `docker-compose.yml`'s fixed port remains the primary local path.
