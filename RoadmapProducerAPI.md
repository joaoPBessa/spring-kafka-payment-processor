# 🚀 Implementation Roadmap - Payment Processor

## 💳 Account Management (CRUD & Core)
- [ ] **Account Controller**
    - [x] Implement `POST /save` (with 201 Created & Location Header)
    - [x] Implement `PATCH /{accountNumber}/name` (Update only the account name)
    - [ ] Implement `DELETE /{accountNumber}` (Physical deletion initially)
    - [ ] Implement `GET /{accountNumber}` (Find by Number)
    - [ ] Implement `GET /all` with **Pageable** and **Specification Factory** for dynamic filtering
- [ ] **Business Rules & Auditing**
    - [ ] Implement **Soft Delete** (`active` flag or `deleted_at` column)
    - [ ] Create **Global Exception Handler** (`@ControllerAdvice`)
    - [ ] Customize and internationalize **Bean Validation** messages
    - [ ] Configure batch validation (return all errors at once in the JSON response)
- [ ] **Cache & Performance**
    - [ ] Spin up **Redis** instance in `docker-compose.yml`
    - [ ] Implement caching layer (`@Cacheable`) for lookups
    - [ ] Implement strategic **Cache Evict** on write operations (Save, Update, Delete)

## 🏗️ Infrastructure, Security & Observability
- [ ] **Security with HashiCorp Vault**
    - [ ] Spin up **Vault** in `docker-compose.yml`
    - [ ] Migrate credentials from `application.yaml` to Vault (Secrets Management)
- [ ] **Observability & Telemetry**
    - [ ] Add **Structured Logging** and traceability
    - [ ] Configure **Micrometer/Actuator** to export metrics
    - [ ] Spin up **Grafana** and **Prometheus** in Docker
    - [ ] Create Dashboards:
        - [ ] JVM/App health metrics
        - [ ] Business metrics (accounts created, validation errors)

## 📨 Payment Engine (Kafka Integration)
- [ ] **Kafka Ecosystem Configuration**
    - [ ] Add **Kafka** and **Schema Registry** to `docker-compose.yml`
    - [ ] Configure **Confluent Control Center** for visual cluster monitoring
- [ ] **Producer Development**
    - [ ] Define **Avro** schemas for payment events
    - [ ] Create `PaymentController` with balance/limit validations
    - [ ] Implement synchronous Kafka posting with callback handling (Success/Failure)

## 🧪 Quality & Testing
- [ ] **Unit Testing (JUnit 5 & Mockito)**
    - [ ] Cover Controllers, Services, and Mappers
    - [ ] Repository tests with **H2 In-Memory** (or TestContainers for realism)
- [ ] **Integration Testing & BDD**
    - [ ] Implement acceptance scenarios with **Cucumber**
    - [ ] Validate end-to-end flow (API -> DB -> Kafka)