package com.joaoPBessa.payments.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.avro.PaymentMethod;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dead letter service")
class DeadLetterServiceTest {

    private static final String TOPIC = "payment.events.DLT";
    private static final Instant EVENT_TIME = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final long RECORD_TIME = 1_700_000_000_000L;

    @Mock
    private DeadLetterReader reader;

    @Mock
    private DeadLetterPayloadDecoder decoder;

    @InjectMocks
    private DeadLetterService service;

    private PaymentEvent event(String transactionId) {
        return PaymentEvent.newBuilder()
                .setTransactionId(transactionId)
                .setEventTimestamp(EVENT_TIME)
                .setSourceAccount("000000")
                .setTargetAccount("910002")
                .setAmount(new BigDecimal("12.50"))
                .setCurrency("USD")
                .setPaymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
    }

    private ConsumerRecord<String, byte[]> record(int partition, long offset, byte[] value) {
        return new ConsumerRecord<>(TOPIC, partition, offset, RECORD_TIME + offset, TimestampType.CREATE_TIME, 0, 0,
                "key-" + offset, value, new RecordHeaders(), java.util.Optional.empty());
    }

    @Test
    @DisplayName("Should return nothing when the dead letter topic is empty")
    void shouldReturnNothingWhenTheDeadLetterTopicIsEmpty() {
        when(reader.oldest()).thenReturn(List.of());

        assertThat(service.oldestMessages()).isEmpty();

        verify(reader, times(1)).oldest();
        verifyNoMoreInteractions(reader, decoder);
    }

    @Test
    @DisplayName("Should expose the position, key, time and decoded event of each message in order")
    void shouldExposeThePositionKeyTimeAndDecodedEventOfEachMessageInOrder() {
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".getBytes(StandardCharsets.UTF_8);
        when(reader.oldest()).thenReturn(List.of(record(0, 4, first), record(1, 7, second)));
        when(decoder.decode(first)).thenReturn(DeadLetterPayload.decoded(event("tx-1")));
        when(decoder.decode(second)).thenReturn(DeadLetterPayload.decoded(event("tx-2")));

        List<DeadLetterMessage> messages = service.oldestMessages();

        assertThat(messages).hasSize(2);
        DeadLetterMessage firstMessage = messages.get(0);
        assertThat(firstMessage.partition()).isZero();
        assertThat(firstMessage.offset()).isEqualTo(4);
        assertThat(firstMessage.key()).isEqualTo("key-4");
        assertThat(firstMessage.timestamp()).isEqualTo(Instant.ofEpochMilli(RECORD_TIME + 4));
        assertThat(firstMessage.payload().rawPayload()).isNull();
        assertThat(firstMessage.payload().event().getTransactionId()).isEqualTo("tx-1");
        assertThat(firstMessage.payload().event().getEventTimestamp()).isEqualTo(EVENT_TIME);
        assertThat(firstMessage.payload().event().getSourceAccount()).isEqualTo("000000");
        assertThat(firstMessage.payload().event().getTargetAccount()).isEqualTo("910002");
        assertThat(firstMessage.payload().event().getAmount()).isEqualByComparingTo("12.50");
        assertThat(firstMessage.payload().event().getCurrency()).isEqualTo("USD");
        assertThat(firstMessage.payload().event().getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(messages.get(1).partition()).isEqualTo(1);
        assertThat(messages.get(1).payload().event().getTransactionId()).isEqualTo("tx-2");
    }

    @Test
    @DisplayName("Should expose the raw payload of a message that is not a valid event")
    void shouldExposeTheRawPayloadOfAMessageThatIsNotAValidEvent() {
        byte[] poisonPill = "garbage".getBytes(StandardCharsets.UTF_8);
        when(reader.oldest()).thenReturn(List.of(record(0, 0, poisonPill)));
        when(decoder.decode(poisonPill)).thenReturn(DeadLetterPayload.undecodable("garbage"));

        List<DeadLetterMessage> messages = service.oldestMessages();

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).payload().event()).isNull();
        assertThat(messages.get(0).payload().rawPayload()).isEqualTo("garbage");
    }

}
