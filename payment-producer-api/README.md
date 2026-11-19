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
- **Centralized error handling:** a single `@RestControllerAdvice` maps validation, not-found, conflict, malformed-request, access-denied, and publish-failure cases to consistent 400/403/404/409/503/500 responses.
- **Method-level authorization:** the endpoints are protected with `@PreAuthorize` and the role names live in `Roles`; an ArchUnit rule fails the build if a public controller method has no `@PreAuthorize`. See [Authentication and roles](#authentication-and-roles).

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
com/joaopbessa/payments/producer
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

## Authentication and roles

The API is an OAuth2 resource server: every request needs `Authorization: Bearer <JWT>`, issued by [`payment-auth-server`](../payment-auth-server/README.md). The application checks the signature (keys fetched from the issuer's JWKS endpoint, `jwk-set-uri`, on first use and then cached), the issuer (`issuer-uri`, compared with the `iss` claim only, with no OIDC discovery) and the expiry, and turns the `roles` claim into `ROLE_*` authorities. Each controller method declares the role it needs with `@PreAuthorize`; there is no role hierarchy, so a token needs exactly the role the endpoint names.

| Role | Grants |
| :--- | :--- |
| `ACCOUNT_READ` | `GET` on accounts |
| `ACCOUNT_WRITE` | `POST`, `PATCH`, `DELETE` on accounts |
| `PAYMENT_CREATE` | `POST /api/v1/payments` |
| `METRICS_READ` | `/actuator/prometheus` (`/actuator/health` is open) |

```bash
TOKEN=$(scripts/get-token.sh producer-client producer-client-demo)   # from the repository root
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/v1/accounts?page=0&size=10"
```

| Situation | Answer |
| :--- | :--- |
| No token on a read, or a token that is invalid, expired or from another issuer | `401` |
| No token on a request that changes state | `403`: CSRF protection is on, and a request without a Bearer token fails that check before authentication. With a Bearer header the request is exempt, because browsers do not send it on their own. |
| A valid token without the required role | `403` in the usual error format |

The authorization is evaluated after the arguments are bound and validated, so a caller without the role who sends an invalid request gets the `400` validation error rather than a `403`. The authorization server does not have to be up for the application to start, and the health check keeps answering. But while the application has no signing key and cannot fetch one, a request that carries a token is answered with `503`, without a body (a request without a token still gets `401`); once a key is cached, short outages of the issuer go unnoticed. Because the issuer's key is generated at startup, restarting the authorization server invalidates the tokens issued before it.

## API Endpoints

All endpoints are versioned under `/api/v1`.

### Accounts (`/api/v1/accounts`)

| Method | Endpoint | Role | Description | Success | Errors |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/accounts/` *(note the trailing slash)* | `ACCOUNT_WRITE` | Create an account. Returns a `Location` header. | `201` | `400`, `409` |
| `PATCH` | `/api/v1/accounts/{accountNumber}` | `ACCOUNT_WRITE` | Rename the account. | `204` | `400`, `404` |
| `DELETE` | `/api/v1/accounts/{accountNumber}` | `ACCOUNT_WRITE` | Soft-delete the account (`active = false`). | `204` | `404` |
| `GET` | `/api/v1/accounts/{accountNumber}` | `ACCOUNT_READ` | Look up an active account. Cached. | `200` | `404` |
| `GET` | `/api/v1/accounts` | `ACCOUNT_READ` | Paginated list with `Specification`-based dynamic filtering (`account_number`, `account_name`, `active`, `page`, `size`). | `200` | `400` |

### Payments (`/api/v1/payments`)

| Method | Endpoint | Role | Description | Success | Errors |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/payments` | `PAYMENT_CREATE` | Validates the request, checks both accounts exist (cached), and publishes an Avro-encoded event to Kafka. Returns a generated transaction code (also the Kafka record key). | `202` | `400`, `404`, `503` |

All endpoints can also answer `401` and `403` as described above.

## Observability

`/actuator/health` (open) and `/actuator/prometheus` (needs the `METRICS_READ` role) are exposed on the application port (8081). Every meter carries an `application` tag, HTTP request histograms include 0.5 s and 2 s buckets (the Apdex thresholds), and traces are fully sampled and sent to Zipkin. `/actuator/**` calls are excluded from metrics and traces.

Custom metrics:

| Metric | Meaning |
|---|---|
| `payments_publish_seconds{outcome}` | Time until the broker confirms the event; `outcome` is `success`, `timeout` or `error` |
| `payments_accepted_total{payment_method}` | Payments accepted and published |
| `payments_amount` | Distribution of the accepted amounts |
| `cache_gets_total{cache="accounts",result}` | Account cache hits and misses |

The Kafka template is observed, so the trace context travels to the consumer in the `traceparent` header. Run with `-Dspring-boot.run.profiles=json-logs` for ECS JSON logs (SQL printing is switched off in that profile so every line stays JSON). Dashboards, Prometheus and Zipkin are described in the [root README](../README.md#observability).

## Testing

- **Slice tests** (`@WebMvcTest`) for both controllers: `AccountControllerTest`, `PaymentControllerTest`, `AccessControlTest`, which checks every endpoint against every role (no token, bad tokens, wrong or missing role, the right role, the 403 error format), and `FrameworkErrorsTest` (an unknown path, a method or media type the endpoint does not support answer 404, 405 and 415 instead of 500). They authenticate with tokens signed by the `TestJwtIssuer` from `common-libraries`, so no authorization server is needed.
- **Unit tests**: `AccountServiceTest`, `PaymentServiceTest` — plain Mockito, no Spring context.
- **Integration tests**: `PaymentProducerApiApplicationTests` boots the full context against Testcontainers-managed Kafka, Redis, Zipkin and Apicurio containers (the registry URL reaches the context through a `DynamicPropertyRegistrar`, so the dynamic port is wired automatically); `PaymentPublishingIntegrationTest` publishes over real HTTP (with a token) and confirms the exact Avro event round-trips through a real Kafka broker and Apicurio Registry, and that the secured endpoints refuse callers without a token, from another issuer or without the role, while the health stays open and the metrics need `METRICS_READ`.

```bash
mvn test      # unit + slice tests
mvn verify    # also runs the Testcontainers-backed integration test (requires Docker)
```

## Roadmap

- Alerting on top of the metrics and a Kafka monitoring UI are not part of the stack yet.
