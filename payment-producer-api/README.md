# Payment Producer API

The Spring Boot service in this repository: an account-management REST API with a payments endpoint that is intentionally ahead of its Kafka integration (see [Roadmap](#roadmap)).

## Tech Stack

- **Runtime:** Java 21
- **Framework:** Spring Boot 4.0.0, Spring Cloud 2025.1.1
- **Database:** PostgreSQL 15, targeting a dedicated `payment` schema
- **Migration:** Flyway (owned by the sibling `payment-db-migration` module)
- **Messaging:** Apache Kafka broker provisioned via `docker-compose.yml`; client dependency present, no producer implemented yet
- **Secrets:** HashiCorp Vault (Spring Cloud Vault)
- **Caching:** Redis (Spring Data Redis)
- **Observability:** Spring Boot Actuator, Micrometer tracing (Brave), Zipkin — dependencies are in place, but no custom metrics or dashboards are configured yet
- **Testing:** JUnit 5/6, Mockito, AssertJ, Testcontainers (Kafka, Zipkin), H2

## Architecture Highlights

- **Dedicated schema:** the datasource targets a `payment` schema, separate from `public`.
- **JPA auditing:** `created_at`/`updated_at` on `Account` are populated automatically via `@EnableJpaAuditing` and `@EntityListeners(AuditingEntityListener.class)` — no manual timestamp handling.
- **Cache-aside:** account lookups are `@Cacheable`, with eviction on rename/delete.
- **Centralized error handling:** a single `@RestControllerAdvice` maps validation, not-found, conflict, and malformed-request failures to consistent 400/404/409/500 responses.

## Getting Started

### Prerequisites
- JDK 21
- Maven
- Docker & Docker Compose (for local infrastructure and for the Testcontainers-backed test)

### 1. Start local infrastructure

```bash
docker-compose up -d
```

This starts Postgres, Redis, Vault, and Kafka. The broker comes up empty — no topics exist yet, since the producer itself isn't implemented (see [Roadmap](#roadmap)).

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
├── config/         # JPAConfiguration (@EnableJpaAuditing), RedisCacheConfig
├── controllers/    # AccountController, PaymentController
├── domain/entities/# Account
├── exceptions/     # AccountNotFoundException, DuplicatedAccountException, GlobalExceptionHandler
├── repositories/   # AccountRepository, specifications/AccountSpecification
└── services/       # AccountService
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
| `POST` | `/api/v1/payments` | **Stub.** Validates the request and returns an echoed response with a generated transaction code — does not yet publish to Kafka. | `202` | `400` |

## Observability

`spring-boot-starter-actuator`, Micrometer tracing (Brave), and Zipkin are on the classpath, but nothing beyond Spring Boot's defaults is configured — no custom metrics, no dashboards. Wiring these up is part of the [roadmap](#roadmap).

## Testing

- **Slice tests** (`@WebMvcTest`) for both controllers: `AccountControllerTest` (15 tests), `PaymentControllerTest` (12 tests).
- **Unit tests**: `AccountServiceTest` (9 tests, plain Mockito — no Spring context).
- **Integration test**: `PaymentProducerApiApplicationTests` boots the full context against Testcontainers-managed Kafka and Zipkin containers.

```bash
mvn test      # unit + slice tests
mvn verify    # also runs the Testcontainers-backed integration test (requires Docker)
```

## Roadmap

The project's namesake feature — publishing payment events to Kafka — is the next milestone:

- Implement a `PaymentService` that actually publishes to Kafka from `PaymentController`.
- Define Avro schemas and wire up Schema Registry for the payment event contract.
- Wire up custom Micrometer metrics and dashboards on top of the observability dependencies already in place.
