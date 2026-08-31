# spring-kafka-payment-processor

A multi-module payment-processing backend, built to explore account management and event-driven payment patterns with Spring Boot 4 and Kafka.

[![CI](https://github.com/joaoPBessa/spring-kafka-payment-processor/actions/workflows/maven.yml/badge.svg)](https://github.com/joaoPBessa/spring-kafka-payment-processor/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-brightgreen)](https://spring.io/projects/spring-boot)

## What this project demonstrates

- A multi-module Maven layout with a clean dependency direction (app depends on shared domain types and a dedicated migration module, not the other way around).
- Dynamic, paginated queries with Spring Data JPA `Specification`, driven by request DTOs.
- Cache-aside with Redis (`@Cacheable`/`@CacheEvict`) and centralized exception handling.
- Schema-governed event publishing: Avro-generated types, Apicurio Registry validation, and a synchronous, timeout-bounded Kafka producer.
- Externalized configuration and secrets via HashiCorp Vault (Spring Cloud Vault).
- Slice tests (`@WebMvcTest`) and full-context integration tests backed by Testcontainers.
- Built on Spring Boot 4.0's newly modularized starters (`spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, etc.), released only recently.

## Modules

| Module | Type | What it is |
|---|---|---|
| [`payment-producer-api`](payment-producer-api/) | Spring Boot app | The actual service — REST API, JPA persistence, Redis cache, Vault-backed config. See its own [README](payment-producer-api/README.md) for endpoints and setup. |
| `common-libraries` | jar | Shared domain types: the `PaymentMethod` enum and the `PaymentEvent` Avro schema (the Kafka wire contract), kept separate so both can be reused by future consumer modules. |
| `payment-db-migration` | jar | Owns the Flyway SQL migrations and the `flyway-maven-plugin` binding, decoupled from the runtime app module so schema versioning doesn't require touching `payment-producer-api`. |

## Feature status

### Implemented
- Full account CRUD (`payment-producer-api`): create, rename, soft-delete, lookup, and paginated/filtered listing.
- Redis cache-aside on account lookups, with eviction on writes.
- Centralized validation and error handling — structured 400/404/409/503/500 responses.
- Flyway-managed schema migrations, decoupled into their own module.
- Vault-backed externalized configuration.
- **Kafka payment publishing** — `POST /api/v1/payments` validates both accounts exist (via the same Redis cache as account lookups), publishes an Avro-encoded event to Kafka, validated against an Apicurio Registry schema, and only returns `202 Accepted` once the broker confirms the write.
- Testcontainers-backed integration tests alongside `@WebMvcTest`/unit tests (46 tests total, see [Testing](#testing)).

### Roadmap / In progress
- Observability dashboards — Actuator and Micrometer/Zipkin tracing dependencies are already in place, but no custom metrics or dashboards are wired up yet.

## Tech stack

| Concern | Choices |
|---|---|
| Language / Framework | Java 21, Spring Boot 4.0.0, Spring Cloud 2025.1.1 |
| Persistence | Spring Data JPA, PostgreSQL 15, Flyway |
| Cache | Spring Data Redis |
| Messaging | Apache Kafka + Apicurio Registry (Avro), both running via `docker-compose.yml`; synchronous producer in `payment-producer-api` |
| Config / Secrets | Spring Cloud Vault |
| Validation | Jakarta Bean Validation |
| Observability | Spring Boot Actuator, Micrometer tracing (Brave), Zipkin |
| Testing | JUnit 5/6, Mockito, AssertJ, Testcontainers (Kafka, Zipkin), H2 |

## Getting started

Prerequisites: JDK 21, Maven, Docker (for local infrastructure and for Testcontainers).

```bash
git clone https://github.com/joaoPBessa/spring-kafka-payment-processor.git
cd spring-kafka-payment-processor

# Start local infrastructure: Postgres, Redis, Vault, Kafka, and Apicurio Registry
docker-compose up -d
```

> The credentials in `docker-compose.yml` are throwaway local-development values only — not meant for any real environment.

```bash
# Build and test everything
mvn -B clean verify

# Or run the API directly
cd payment-producer-api
mvn spring-boot:run
```

## Testing

```bash
mvn test      # unit + slice tests
mvn verify    # also runs the Testcontainers-backed integration test (requires Docker running)
```

Current coverage in `payment-producer-api`: 15 tests on `AccountController`, 14 on `PaymentController`, 9 on `AccountService`, 5 on `PaymentService`, a full-context startup test, and a 2-test Kafka+Apicurio integration suite — 46 in total.

## CI

GitHub Actions runs `mvn clean verify` on every push to `main`/`develop` and on every pull request. See [`.github/workflows/maven.yml`](.github/workflows/maven.yml).

## Author

João Pedro Béssa — [github.com/joaoPBessa](https://github.com/joaoPBessa)
