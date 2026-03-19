# Payment Producer API 💳

[![Java Version](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Distributed-lightgrey)](https://kafka.apache.org/)

## 📝 Overview
The **Payment Producer API** is a high-performance microservice designed to manage customer accounts and orchestrate payment events. Built with a focus on financial consistency and scalability, it serves as the entry point for the payment processing ecosystem.

This service validates account statuses, manages balances in a dedicated PostgreSQL schema, and publishes validated payment events to **Apache Kafka** using **Avro** serialization.

---

## 🛠 Tech Stack
- **Runtime:** Java 17
- **Framework:** Spring Boot 3.x
- **Database:** PostgreSQL 15 (Targeting specific `payment` schema)
- **Migration:** Flyway
- **Messaging:** Apache Kafka with Confluent Schema Registry
- **Security:** HashiCorp Vault (Secrets Management)
- **Caching:** Redis
- **Observability:** Prometheus & Grafana
- **Testing:** JUnit 5, Mockito, and Cucumber (BDD)

---

## 🏗 Architecture Highlights
- **Schema Isolation:** Implements a dedicated `payment` schema to separate business logic from public data.
- **Auditing:** Fully automated entity auditing (`created_at`, `updated_at`) using Spring Data JPA.
- **RESTful Design:** Uses semantic HTTP verbs (e.g., `PATCH` for partial updates) and returns proper `Location` headers.
- **Security-First:** Integration with Vault ensures no sensitive credentials (DB, Kafka) are stored in plain text.



---

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose
- JDK 17
- Maven 3.8+

### 1. Environment Setup
The project relies on several infrastructure components. Start them using the provided `docker-compose.yml`:

```bash
docker-compose up -d
```

### 2. Database Initialization
The project uses **Flyway** for database versioning and migration. The initialization process is split between a Docker bootstrap script and Flyway migrations.

#### Infrastructure Setup (Automated)
When you run `docker-compose up`, the `init-db.sql` script is executed to:
* Create the `payments_db` database.
* Create two distinct users: `payment-admin` (for migrations) and `payment-producer-app` (for the application).
* Create the `payment` schema and set up proper permissions.

#### Schema Migration
Upon application startup, Flyway connects using the `payment-admin` credentials to execute scripts located in `src/main/resources/db/migration`.
* **V1__create_accounts_table.sql**: Creates the `accounts` table within the `payment` schema and grants DML permissions to the application user.

> **Note:** To manually trigger migrations via Maven, use:
> ```bash
> mvn flyway:migrate -Dflyway.user=payment-admin -Dflyway.password=your_admin_password
> ```

---

### 3. Running the Application
There are two primary ways to run the service locally:

#### Option A: Maven (Best for Development)
Ensure your Docker infrastructure is up, then run:
```bash
mvn spring-boot:run
```
---

## 📂 Project Structure
The project follows a **Clean Architecture** approach, ensuring clear separation of concerns and high testability.

```text
src/main/java/com/yourdomain/payment
├── config                  # Infrastructure & Bean configurations (Vault, Kafka, JPA)
│   ├── JpaConfig.java      # @EnableJpaAuditing and persistence settings
│   ├── KafkaConfig.java    # Producer/Consumer and Avro settings
│   └── RedisConfig.java    # Caching strategies
├── controller              # REST Entry points (Account, Payment)
│   └── v1                  # Versioned API controllers
├── dto                     # Data Transfer Objects (Records)
│   ├── request             # Input validation schemas
│   └── response            # API output contracts
├── entity                  # JPA Entities mapped to 'payment' schema
├── exception               # Global Exception Handler and Custom Exceptions
│   ├── GlobalHandler.java  # @ControllerAdvice implementation
│   └── BusinessException.java
├── mapper                  # MapStruct interfaces for DTO <-> Entity conversion
├── repository              # Spring Data JPA repositories (Persistence layer)
└── service                 # Core Business Logic & Orchestration
    ├── impl                # Implementation of business rules
    └── validation          # Custom validation components

```
---

## 🔗 API Endpoints (Quick Reference)

The API follows RESTful principles, utilizing standard HTTP verbs and status codes. All endpoints are versioned under `/api/v1`.

### 🏦 Accounts Resource
Manage customer accounts, balances, and profiles.

| Method | Endpoint | Description | Success Code | Error Codes |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/accounts` | Register a new account. Returns `Location` header. | `201` | `400`, `409` |
| `GET` | `/accounts` | Pageable list with Specification-based filters. | `200` | `400` |
| `GET` | `/accounts/{number}` | Retrieve full details of a specific account. | `200` | `404` |
| `PATCH` | `/accounts/{number}/owner` | Partially update the account holder's name. | `200` | `400`, `404` |
| `DELETE` | `/accounts/{number}` | Deactivate an account (Soft Delete). | `204` | `404` |

### 💸 Payment Resource
Orchestrate and produce payment events to the Kafka cluster.

| Method | Endpoint | Description | Success Code | Error Codes |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/payments` | Process a new payment transaction (Async). | `202` | `400`, `422` |
| `GET` | `/payments/status/{id}` | Check the status of a processed transaction. | `200` | `404` |

### 🛠 Operational & Monitoring
Endpoints provided by **Spring Boot Actuator** for health and metrics.

| Method | Endpoint | Description | Use Case |
| :--- | :--- | :--- | :--- |
| `GET` | `/actuator/health` | Check application, DB, and Kafka health. | Liveness/Readiness probes |

---

## 📊 Monitoring & Observability
This microservice is built with a "production-ready" mindset, exposing deep insights into its internal state and performance.

### 🧩 Observability Stack
We use the standard industry stack for cloud-native monitoring:
* **Micrometer:** Instrumentation library that powers Spring Actuator.
* **Prometheus:** Time-series database that scrapes metrics from the app.
* **Grafana:** Visualization platform for metrics and alerts.
* **Confluent Control Center:** Specialized UI for monitoring Kafka throughput and lag.

### 📈 Available Dashboards
Once the infrastructure is running via Docker Compose, access these URLs:

| Tool | URL | Credentials | Purpose |
| :--- | :--- | :--- | :--- |
| **Grafana** | `http://localhost:3000` | `admin/admin` | JVM Health, Throughput, Latency |
| **Prometheus** | `http://localhost:9090` | N/A | Raw metrics query & exploration |
| **Control Center** | `http://localhost:9021` | N/A | Kafka Topics and Consumer Lag |
| **Actuator** | `http://localhost:8080/actuator` | N/A | Service health and runtime info |

### 🛠 Custom Business Metrics
Beyond standard JVM metrics (CPU, Memory), we expose custom **Micrometer Counters** to track business value:
- `payments_produced_total`: Total count of payment events sent to Kafka.
- `accounts_created_total`: Total number of new accounts registered.
- `payment_errors_total`: Count of failed payment attempts (validation or broker errors).


### 🪵 Structured Logging
Logs are formatted in **JSON** (in production profiles) to be easily ingested by ELK Stack (Elasticsearch, Logstash, Kibana) or Splunk. Each log entry includes a `traceId` and `spanId` for distributed tracing.

---

## 🧪 Testing Strategy
To ensure the reliability of financial transactions and account management, this project implements a multi-layered testing strategy based on the **Testing Pyramid**.

### 📐 Test Categories

| Layer | Tools | Focus |
| :--- | :--- | :--- |
| **Unit Tests** | JUnit 5, Mockito, AssertJ | Business logic, Mappers, and Service layer isolation. |
| **Integration Tests** | Spring Context, H2/Testcontainers | Repository mapping, Database constraints, and Kafka Producers. |
| **BDD / Acceptance** | Cucumber, RestAssured | Validating business scenarios from the user's perspective. |



### ⚙️ How to Run Tests

**Run all tests:**
```bash
mvn test
```

---

## 🗺 Roadmap & Next Steps
This project is under active development. We follow a modular evolution plan, prioritizing core stability before expanding to advanced observability and security features.

### 🚩 Current Milestones
- [x] **Phase 1: Foundation** - Base API, PostgreSQL Schema isolation, and JPA Auditing.
- [x] **Phase 2: Documentation** - RESTful contract definition and comprehensive README.
- [ ] **Phase 3: Core CRUD** - Implementation of Account Service, Patch updates, and Soft Delete.
- [ ] **Phase 4: Resilience** - Redis Caching, Exception Handling, and Bean Validation batching.
- [ ] **Phase 5: Messaging** - Kafka Integration with Avro Schemas and Schema Registry.
- [ ] **Phase 6: Hardening** - HashiCorp Vault integration and Full Observability stack.

### 🔍 Deep Dive
For a detailed list of tasks, including technical debt, unit testing coverage goals, and specific implementation details for each layer, please check our dedicated roadmap file:

👉 **[Detailed ROADMAP.md](./ROADMAP.md)**



### 💡 Future Considerations
- **Circuit Breaker:** Implementation of Resilience4j for Kafka publishing.
- **Multi-Tenancy:** Expanding the schema isolation to support multiple business units.
- **Cloud Native:** Preparation for Kubernetes deployment (Helm Charts and Resource Quotas).