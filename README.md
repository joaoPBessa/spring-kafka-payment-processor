# spring-kafka-payment-processor

A multi-module payment-processing backend, built to explore account management and event-driven payment patterns with Spring Boot 4 and Kafka.

[![CI](https://github.com/joaoPBessa/spring-kafka-payment-processor/actions/workflows/maven.yml/badge.svg)](https://github.com/joaoPBessa/spring-kafka-payment-processor/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-brightgreen)](https://spring.io/projects/spring-boot)

## What this project demonstrates

- A multi-module Maven layout with a clean dependency direction (app depends on shared domain types and a dedicated migration module, not the other way around).
- Dynamic, paginated queries with Spring Data JPA `Specification`, driven by request DTOs.
- Cache-aside with Redis (`@Cacheable`/`@CacheEvict`) and centralized exception handling.
- Externalized configuration and secrets via HashiCorp Vault (Spring Cloud Vault).
- Slice tests (`@WebMvcTest`) and full-context integration tests backed by Testcontainers.
- Built on Spring Boot 4.0's newly modularized starters (`spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, etc.), released only recently.

## Modules

| Module | Type | What it is |
|---|---|---|
| [`payment-producer-api`](payment-producer-api/) | Spring Boot app | The actual service — REST API, JPA persistence, Redis cache, Vault-backed config. See its own [README](payment-producer-api/README.md) for endpoints and setup. |
| `common-libraries` | jar | Shared domain types with no framework dependency beyond Jackson annotations and Bean Validation. Currently just the `PaymentMethod` enum, kept separate so it can be reused by future consumer modules. |
| `payment-db-migration` | jar | Owns the Flyway SQL migrations and the `flyway-maven-plugin` binding, decoupled from the runtime app module so schema versioning doesn't require touching `payment-producer-api`. |

## Feature status

### Implemented
- Full account CRUD (`payment-producer-api`): create, rename, soft-delete, lookup, and paginated/filtered listing.
- Redis cache-aside on account lookups, with eviction on writes.
- Centralized validation and error handling — structured 400/404/409/500 responses.
- Flyway-managed schema migrations, decoupled into their own module.
- Vault-backed externalized configuration.
- Testcontainers-backed integration tests alongside `@WebMvcTest` slice tests (28 tests total, see [Testing](#testing)).

### Roadmap / In progress
- **Kafka payment publishing** — `POST /api/v1/payments` currently validates the request and returns `202 Accepted`, but does not yet publish to Kafka. This is the next planned milestone for the project (the one the repo is named after).
- Avro schemas + Schema Registry for the payment event contract.
- Observability dashboards — Actuator and Micrometer/Zipkin tracing dependencies are already in place, but no custom metrics or dashboards are wired up yet.

## Tech stack

| Concern | Choices |
|---|---|
| Language / Framework | Java 21, Spring Boot 4.0.0, Spring Cloud 2025.1.1 |
| Persistence | Spring Data JPA, PostgreSQL 15, Flyway |
| Cache | Spring Data Redis |
| Messaging | Apache Kafka broker running via `docker-compose.yml`; client dependency in place, producer not yet implemented |
| Config / Secrets | Spring Cloud Vault |
| Validation | Jakarta Bean Validation |
| Observability | Spring Boot Actuator, Micrometer tracing (Brave), Zipkin |
| Testing | JUnit 5/6, Mockito, AssertJ, Testcontainers (Kafka, Zipkin), H2 |

## Getting started

Prerequisites: JDK 21, Maven, Docker (for local infrastructure and for Testcontainers).

```bash
git clone https://github.com/joaoPBessa/spring-kafka-payment-processor.git
cd spring-kafka-payment-processor

# Start local infrastructure: Postgres, Redis, Vault, and Kafka
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

Current coverage in `payment-producer-api`: 15 tests on `AccountController`, 12 on `PaymentController`, 9 on `AccountService`, plus a full-context startup test — 37 in total.

## CI

GitHub Actions runs `mvn clean verify` on every push to `main`/`develop` and on every pull request. See [`.github/workflows/maven.yml`](.github/workflows/maven.yml).

## Author

João Pedro Béssa — [github.com/joaoPBessa](https://github.com/joaoPBessa)
