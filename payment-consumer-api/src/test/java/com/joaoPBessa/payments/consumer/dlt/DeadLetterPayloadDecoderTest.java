package com.joaoPBessa.payments.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.avro.PaymentMethod;

@DisplayName("Dead letter payload decoder")
class DeadLetterPayloadDecoderTest {

    private static final String TOPIC = "payment.events.DLT";
    private static final byte[] AVRO_BYTES = {0, 0, 0, 0, 1, 42};
    private static final byte[] NOT_UTF8_BYTES = {(byte) 0xC3, (byte) 0x28, (byte) 0xFF, 0x00};

    private final PaymentEvent event = PaymentEvent.newBuilder()
            .setTransactionId("tx-1")
            .setEventTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS))
            .setSourceAccount("100001")
            .setTargetAccount("100002")
            .setAmount(new BigDecimal("12.50"))
            .setCurrency("USD")
            .setPaymentMethod(PaymentMethod.PIX)
            .build();

    private DeadLetterPayloadDecoder decoderThatReads(Deserializer<PaymentEvent> deserializer) {
        return new DeadLetterPayloadDecoder(deserializer, TOPIC);
    }

    private Deserializer<PaymentEvent> failingDeserializer() {
        return (topic, data) -> {
            throw new SerializationException("not an avro payload");
        };
    }

    @Test
    @DisplayName("Should decode a valid event")
    void shouldDecodeAValidEvent() {
        DeadLetterPayload payload = decoderThatReads((topic, data) -> event).decode(AVRO_BYTES);

        assertThat(payload.event()).isEqualTo(event);
        assertThat(payload.rawPayload()).isNull();
    }

    @Test
    @DisplayName("Should hand the topic and the original bytes to the deserializer")
    void shouldHandTheTopicAndTheOriginalBytesToTheDeserializer() {
        DeadLetterPayload payload = decoderThatReads(
                (topic, data) -> topic.equals(TOPIC) && data == AVRO_BYTES ? event : null).decode(AVRO_BYTES);

        assertThat(payload.event()).isEqualTo(event);
    }

    @Test
    @DisplayName("Should fall back to the UTF-8 text when the bytes are not a valid event")
    void shouldFallBackToTheUtf8TextWhenTheBytesAreNotAValidEvent() {
        byte[] poisonPill = "this is not an avro payload".getBytes(StandardCharsets.UTF_8);

        DeadLetterPayload payload = decoderThatReads(failingDeserializer()).decode(poisonPill);

        assertThat(payload.event()).isNull();
        assertThat(payload.rawPayload()).isEqualTo("this is not an avro payload");
    }

    @Test
    @DisplayName("Should fall back to Base64 when the undecodable bytes are not UTF-8 text")
    void shouldFallBackToBase64WhenTheUndecodableBytesAreNotUtf8Text() {
        DeadLetterPayload payload = decoderThatReads(failingDeserializer()).decode(NOT_UTF8_BYTES);

        assertThat(payload.event()).isNull();
        assertThat(payload.rawPayload()).isEqualTo(Base64.getEncoder().encodeToString(NOT_UTF8_BYTES));
    }

    @Test
    @DisplayName("Should return an empty payload for a record without a value")
    void shouldReturnAnEmptyPayloadForARecordWithoutAValue() {
        DeadLetterPayload payload = decoderThatReads(failingDeserializer()).decode(null);

        assertThat(payload.event()).isNull();
        assertThat(payload.rawPayload()).isNull();
    }

}
