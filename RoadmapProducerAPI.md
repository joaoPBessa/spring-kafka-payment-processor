# 🚀 Implementation Roadmap - Payment Processor

## 💳 Account Management (CRUD & Core)
- [ ] **Account Controller**
    - [x] Implement `POST /save` (with 201 Created & Location Header)
    - [x] Implement `PATCH /{accountNumber}/name` (Update only the account name)
    - [x] Implement `DELETE /{accountNumber}` (Physical deletion initially)
    - [x] Implement `GET /{accountNumber}` (Find by Number)
    - [x] Implement `GET /all` with **Pageable** and **Specification Factory** for dynamic filtering
- [ ] **Business Rules & Auditing**
    - [x] Implement **Soft Delete** (`active` flag or `deleted_at` column)
    - [x] Create **Global Exception Handler** (`@ControllerAdvice`)
    - [ ] Customize and internationalize **Bean Validation** messages
    - [x] Configure batch validation (return all errors at once in the JSON response)
- [x] **Cache & Performance**
    - [x] Spin up **Redis** instance in `docker-compose.yml`
    - [x] Implement caching layer (`@Cacheable`) for lookups
    - [x] Implement strategic **Cache Evict** on write operations (Update, Delete)

## 🏗️ Infrastructure, Security & Observability
- [x] **Security with HashiCorp Vault**
    - [x] Spin up **Vault** in `docker-compose.yml`
    - [x] Migrate credentials from `application.yaml` to Vault (Secrets Management)
- [x] **Observability & Telemetry**
    - [x] Add **Structured Logging** (`json-logs` profile) and traceability (traces span producer, Kafka and consumer in **Zipkin**)
    - [x] Configure **Micrometer/Actuator** to export metrics
    - [x] Spin up **Grafana**, **Prometheus**, Zipkin and a Kafka exporter in Docker
    - [x] Create Dashboards:
        - [x] JVM/App health metrics, Apdex and latency
        - [x] Business metrics (payments, accounts created, validation errors), Kafka lag and database query speed

## 📨 Payment Engine (Kafka Integration)
- [ ] **Kafka Ecosystem Configuration**
    - [x] Add **Kafka** and **Schema Registry** (Apicurio) to `docker-compose.yml`
    - [ ] Add a visual cluster monitoring UI (Kafka UI or Redpanda Console)
- [ ] **Producer Development**
    - [x] Define **Avro** schemas for payment events
    - [ ] Create `PaymentController` with balance/limit validations
    - [x] Implement synchronous Kafka posting with callback handling (Success/Failure)

- [ ] **Consumer Development** (`payment-consumer-api`)
    - [x] Consume `payment.events` and persist each payment (idempotent by transaction id)
    - [x] Retry with exponential backoff and publish failures to a **Dead Letter Topic**
    - [ ] Replay tool / reprocessing flow for the DLT
    - [x] Metrics and dashboard panels for DLT volume (alerting is out of scope)

## 🧪 Quality & Testing
- [x] **Unit Testing (JUnit 5 & Mockito)**
    - [x] Cover Controllers, Services, and Mappers
    - [x] Repository tests with **TestContainers** (real Postgres, in the consumer module)
- [ ] **Integration Testing & BDD**
    - [ ] Implement acceptance scenarios with **Cucumber**
    - [ ] Validate end-to-end flow (API -> DB -> Kafka)