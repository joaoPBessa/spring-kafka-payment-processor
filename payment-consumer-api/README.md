# Payment Consumer API

The Spring Boot service that closes the payment pipeline: it consumes the Avro `PaymentEvent`s published by `payment-producer-api`, stores each payment in Postgres, and sends anything it cannot store to a Dead Letter Topic (DLT). It has no web layer, so it does not compete for a port with the producer or with Apicurio.

## How it works

```text
payment.events ──► PaymentEventListener ──► PaymentPersistenceService ──► PAYMENT table
   (Avro)                  │                          │
                           └── failure ──► DefaultErrorHandler ──► payment.events.DLT
```

- **Deserialization:** `ErrorHandlingDeserializer` wraps Apicurio's `AvroKafkaDeserializer`, so an undeserializable record becomes a handled failure instead of an endless poll loop.
- **Idempotent persistence:** a payment whose `transaction_id` already exists is logged and skipped; a unique-constraint race between two deliveries is treated as a duplicate as well.
- **Integrity in the database:** the `PAYMENT` table (Flyway `V3`) has a unique transaction id, foreign keys from both accounts to `ACCOUNT(NUMBER)` and a positive-amount check, so these rules hold no matter who writes the row. An account that disappears between publish and consume therefore ends up in the DLT instead of producing an orphan payment. A soft-deleted (inactive) account still exists, so a payment to it is accepted.
- **Retry and DLT:** failures are retried in place (ordering is preserved) with an exponential, bounded backoff, then published to `payment.events.DLT`. `DataIntegrityViolationException` and deserialization errors are never retried because repeating them cannot succeed.
- **DLT contents:** valid events are re-serialized through Apicurio; poison pills keep their original bytes. The standard `kafka_dlt-*` headers carry the original topic, partition, offset and the exception class, message and stack trace.

A prolonged outage of the database or the registry will move the records it touches into the DLT, so the DLT is meant to be replayable. A replay tool is not part of this module yet.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `app.kafka.topics.payment-events` | `payment.events` | Topic to consume |
| `app.kafka.topics.payment-events-dlt` | `payment.events.DLT` | Dead Letter Topic (created on startup) |
| `app.kafka.retry.max-attempts` | `4` | Total deliveries, including the first |
| `app.kafka.retry.initial-interval` / `multiplier` / `max-interval` | `1s` / `2.0` / `10s` | Exponential backoff |
| `spring.kafka.consumer.group-id` | `payment-persistence` | Consumer group |

The properties are validated at startup (`max-attempts >= 1`, `multiplier >= 1`, `initial-interval <= max-interval`, topic names not blank).

## Running locally

```bash
docker compose up -d                 # Postgres, Redis, Vault, Kafka, Apicurio
mvn -pl payment-consumer-api spring-boot:run
```

The consumer reads its database credentials from Vault, like the producer, and runs the Flyway migrations on startup (safe alongside the producer: Flyway takes a database lock). Publish a payment through the producer (`POST /api/v1/payments`) and the row appears in `payment.payment`. To inspect the DLT:

```bash
docker exec payment-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment.events.DLT \
  --from-beginning --property print.headers=true
```

## Testing

```bash
mvn -pl payment-consumer-api -am verify   # requires Docker for Testcontainers
```

- **Unit:** `PaymentEventMapperTest`, `PaymentPersistenceServiceTest`, `PaymentEventListenerTest`, `KafkaConsumerPropertiesTest`, `KafkaConsumerErrorHandlingConfigTest`.
- **Database (real Postgres):** `PaymentTableMigrationTest` applies V1–V3 and checks every constraint; `PaymentRepositoryTest` checks the mapping, which Hibernate also validates against the schema at startup.
- **End to end (real Kafka, Apicurio, Postgres):** `PaymentConsumerIntegrationTest` covers a valid event, a duplicate, an unknown account, a non-positive amount, a transient failure that exhausts the retries, a transient failure that recovers, and a poison pill followed by a valid event.
