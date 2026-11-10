# Payment Consumer API

The Spring Boot service that closes the payment pipeline: it consumes the Avro `PaymentEvent`s published by `payment-producer-api`, stores each payment in Postgres, and sends anything it cannot store to a Dead Letter Topic (DLT). Its HTTP surface is Actuator plus a read-only view of the DLT, on port 8082, so it does not compete for a port with the producer (8081) or with Apicurio (8080).

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

## Observability

The consumer exposes Actuator on port **8082** (`/actuator/health`, `/actuator/prometheus`). Besides the standard JVM, Hikari, repository and Kafka listener/client metrics, it publishes:

| Metric | Meaning |
|---|---|
| `payments_persisted_total{result}` | Events processed, `success` or `duplicate` |
| `kafka_consumer_failed_deliveries_total` | Failed processing attempts, retries included |
| `payments_dead_lettered_total{kind}` | Records sent to the DLT, `event` or `poison_pill` |
| `payments_dead_letter_failures_total{kind}` | Records that could not be published to the DLT |

Traces continue from the producer: the Kafka listener and the dead letter sends are observed, so the `traceparent` header travels from the HTTP request through the topic, the consumer and the DLT. Run with `-Dspring-boot.run.profiles=json-logs` for ECS JSON logs that carry the trace id. The dashboards and the Zipkin view are described in the [root README](../README.md#observability).

A prolonged outage of the database or the registry will move the records it touches into the DLT, so the DLT is meant to be replayable: see [Replaying the DLT](#replaying-the-dlt).

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `app.kafka.topics.payment-events` | `payment.events` | Topic to consume |
| `app.kafka.topics.payment-events-dlt` | `payment.events.DLT` | Dead Letter Topic (created on startup) |
| `app.kafka.retry.max-attempts` | `4` | Total deliveries, including the first |
| `app.kafka.retry.initial-interval` / `multiplier` / `max-interval` | `1s` / `2.0` / `10s` | Exponential backoff |
| `spring.kafka.consumer.group-id` | `payment-persistence` | Consumer group |
| `app.dlt-replay.enabled` | `true` | Turns the daily replay on or off (the endpoint always works) |
| `app.dlt-replay.cron` / `zone` | `0 0 3 * * *` / `UTC` | When the daily replay runs (Spring cron, six fields) |
| `app.dlt-replay.max-batch-size` | `500` | Most messages replayed per partition in one run |
| `app.dlt-replay.send-timeout` | `5s` | How long to wait for the broker to confirm a replayed message or the truncation |

The properties are validated at startup (`max-attempts >= 1`, `multiplier >= 1`, `initial-interval <= max-interval`, topic names not blank, a valid `cron`, `max-batch-size >= 1`).

## Running locally

```bash
docker compose up -d --wait          # Postgres, db-migrate, Redis, Vault, Kafka, Apicurio
docker compose ps -a db-migrate      # must show "Exited (0)"
mvn -pl payment-consumer-api spring-boot:run
```

The consumer has its own database role, `payment-consumer-app`, read from its own Vault secret (`secret/payment-consumer-api`). The role can only `SELECT` and `INSERT` on `payment.payment`. It does not run Flyway: the `db-migrate` service applies the migrations, so start the application after it has finished. `init-db.sql` runs only on an empty volume; to pick up a new role on an existing setup, recreate the Postgres volume (`docker compose rm -sf postgres && docker volume rm spring-kafka-payment-processor_postgres_data`). Publish a payment through the producer (`POST /api/v1/payments`) and the row appears in `payment.payment`.

## Inspecting the DLT

```bash
curl localhost:8082/api/v1/dead-letters
```

Returns the **10 oldest** messages in `payment.events.DLT`, oldest first (an empty array when there is none), so an operator can look at what failed before deciding to retry or to register a missing account first:

```json
[
  {
    "partition": 0,
    "offset": 3,
    "key": "3640dcbd-e6d6-4a93-92ba-12844f3bcab6",
    "timestamp": "2026-11-09T11:56:10.123Z",
    "payload": {
      "transaction_id": "3640dcbd-e6d6-4a93-92ba-12844f3bcab6",
      "event_timestamp": "2026-11-09T11:56:09.900Z",
      "source_account": "000000",
      "target_account": "910002",
      "amount": 12.50,
      "currency": "USD",
      "payment_method": "pix"
    },
    "raw_payload": null
  }
]
```

- `partition` and `offset` identify the message in the DLT.
- A record that is not a valid event (a poison pill) has `payload: null` and its original bytes in `raw_payload`, as text, or Base64 when the bytes are not UTF-8 text.
- The endpoint only shows the payload; it does not say why the message failed or whether the accounts exist (check with `GET /api/v1/accounts/{number}` on the producer).
- It reads the topic directly (assigned partitions, no consumer group, nothing committed), so calling it never affects the consumer. It answers `503` when Kafka cannot be reached.
- There is no authentication: it exposes payment data, so keep it to local or operational use.

To see the raw records with their `kafka_dlt-*` headers (original topic, partition, offset, exception class, message and stack trace):

```bash
docker exec payment-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment.events.DLT \
  --from-beginning --property print.headers=true
```

## Replaying the DLT

Once the cause of a failure is fixed (the missing account was registered, the database or the registry is back), the dead letters can be sent back to `payment.events`. A replay republishes the **original key, bytes and headers** (minus the `kafka_dlt-*` ones), so it also works for poison pills whose only problem was a registry outage. The pipeline is idempotent (a repeated `transaction_id` is skipped), so a message that is replayed twice never produces two payments.

Events are published with the **`transaction_id` as the record key**, which is how a dead letter is found by id. Messages published before that change are keyed by the source account and cannot be replayed by id (they are still replayed by the daily job).

**Daily replay.** Once a day (`app.dlt-replay.cron`, default 03:00 UTC) the consumer reads the DLT backlog, republishes it in offset order and then **deletes from the DLT what it replayed** (Kafka `deleteRecords`), so the DLT always holds only what is still pending and `GET /api/v1/dead-letters` shows exactly that. A message that fails again is dead-lettered again as a new record with the new error, and is retried the next day; there is no attempt limit yet. In a partition the run stops at the first message it cannot republish (nothing after it is deleted). At most `max-batch-size` messages per partition are replayed per run. There is no distributed lock, so run it on a single instance (a second one would only repeat the replay, harmlessly).

**Forced replay.**

```bash
curl -X POST localhost:8082/api/v1/dead-letters/replay   -H 'Content-Type: application/json'   -d '{"transaction_ids": ["3640dcbd-e6d6-4a93-92ba-12844f3bcab6", "unknown-id"]}'
```

```json
{
  "results": [
    { "transaction_id": "3640dcbd-e6d6-4a93-92ba-12844f3bcab6", "status": "replayed", "partition": 0, "offset": 3 },
    { "transaction_id": "unknown-id", "status": "not_found", "partition": null, "offset": null }
  ]
}
```

- Takes 1 to 100 ids and answers `202` with one result per id, in the order requested: `replayed` (republished; `partition` and `offset` are where it was in the DLT), `not_found` (no dead letter has that key) or `failed` (the broker did not confirm). A `failed` id does not stop the others. `400` for an invalid body, `503` when the DLT cannot be read.
- `202` means "sent back for processing", not "persisted": if it fails again it reappears in the DLT, and `payments_persisted_total` counts the successes.
- If an id was dead-lettered more than once, the most recent record is replayed.
- The forced replay **does not delete** anything (a record cannot be removed from the middle of a log): the message stays in the DLT until the next daily run, which sends it again (skipped as a duplicate) and then removes it.
- Like the viewer, it has no authentication.

Replays are counted in `payments_dead_letter_replayed_total{trigger="scheduled|manual",result="success|failure"}`.

## Testing

```bash
mvn -pl payment-consumer-api -am verify   # requires Docker for Testcontainers
```

- **Unit:** `PaymentEventMapperTest`, `PaymentPersistenceServiceTest`, `PaymentEventListenerTest`, `KafkaConsumerPropertiesTest`, `KafkaConsumerErrorHandlingConfigTest`, and for the DLT view `DeadLetterReaderTest` (Kafka `MockConsumer`), `DeadLetterPayloadDecoderTest`, `DeadLetterServiceTest` `DeadLetterControllerTest` (`@WebMvcTest`), and for the replay `DeadLetterReplayerTest`, `DeadLetterTruncatorTest`, `DeadLetterReplayServiceTest`, `DeadLetterReplaySchedulerTest` and `DeadLetterReplayPropertiesTest`.
- **Database (real Postgres):** `PaymentTableMigrationTest` applies V1–V3 and checks every constraint; `PaymentRepositoryTest` checks the mapping, which Hibernate also validates against the schema at startup.
- **End to end (real Kafka, Apicurio, Postgres):** `PaymentConsumerIntegrationTest` covers a valid event, a duplicate, an unknown account, a non-positive amount, a transient failure that exhausts the retries, a transient failure that recovers, and a poison pill followed by a valid event. `DeadLetterEndpointIntegrationTest` has its own broker and checks the DLT view: empty, capped at the ten oldest in order with decoded payloads, and no consumer group left behind. `DeadLetterReplayIntegrationTest` (own broker) covers the forced replay by id, the daily run that clears what it replayed and sends back what still fails, no `kafka_dlt-*` headers piling up, and the topic emptying once the cause is fixed.
