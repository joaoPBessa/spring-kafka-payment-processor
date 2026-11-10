package com.joaoPBessa.payments.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;

import com.joaoPBessa.payments.consumer.metrics.PaymentConsumerMetrics;
import com.joaoPBessa.payments.consumer.metrics.ReplayTrigger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dead letter replayer")
class DeadLetterReplayerTest {

    private static final String EVENTS_TOPIC = "payment.events";
    private static final String DLT_TOPIC = "payment.events.DLT";
    private static final String TRACE_PARENT = "traceparent";
    private static final Duration SEND_TIMEOUT = Duration.ofMillis(200);
    private static final byte[] VALUE = {0, 0, 0, 0, 7, 42};

    @Mock
    private KafkaTemplate<String, byte[]> template;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PaymentConsumerMetrics metrics = new PaymentConsumerMetrics(meterRegistry);

    private DeadLetterReplayer replayer() {
        return new DeadLetterReplayer(template, EVENTS_TOPIC, SEND_TIMEOUT, metrics);
    }

    private double replays(String result) {
        return meterRegistry.get(PaymentConsumerMetrics.REPLAYED_COUNTER)
                .tag(PaymentConsumerMetrics.TRIGGER_TAG, ReplayTrigger.MANUAL.tagValue())
                .tag(PaymentConsumerMetrics.RESULT_TAG, result).counter().count();
    }

    private ConsumerRecord<String, byte[]> deadLetter(RecordHeaders headers) {
        return new ConsumerRecord<>(DLT_TOPIC, 2, 17L, 1_700_000_000_000L, TimestampType.CREATE_TIME, 0, 0,
                "tx-1", VALUE, headers, Optional.empty());
    }

    private RecordHeaders headersOfAFailedDelivery() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(TRACE_PARENT, "00-abc-def-01".getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, EVENTS_TOPIC.getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_EXCEPTION_FQCN, "SomeException".getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE, "trace".getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    private void givenSendSucceeds() {
        SendResult<String, byte[]> sendResult = mock();
        when(template.send(recordCaptor.capture())).thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Test
    @DisplayName("Should republish the original key and bytes to the events topic")
    void shouldRepublishTheOriginalKeyAndBytesToTheEventsTopic() {
        givenSendSucceeds();

        replayer().replay(deadLetter(headersOfAFailedDelivery()), ReplayTrigger.MANUAL);

        ProducerRecord<String, byte[]> sent = recordCaptor.getValue();
        assertThat(sent.topic()).isEqualTo(EVENTS_TOPIC);
        assertThat(sent.key()).isEqualTo("tx-1");
        assertThat(sent.value()).isEqualTo(VALUE);
        assertThat(sent.partition()).isNull();
        verify(template, times(1)).send(sent);
        verifyNoMoreInteractions(template);
    }

    @Test
    @DisplayName("Should count a replay that succeeds")
    void shouldCountAReplayThatSucceeds() {
        givenSendSucceeds();

        replayer().replay(deadLetter(new RecordHeaders()), ReplayTrigger.MANUAL);

        assertThat(replays("success")).isEqualTo(1.0);
        assertThat(replays("failure")).isZero();
    }

    @Test
    @DisplayName("Should drop the dead letter headers and keep the others")
    void shouldDropTheDeadLetterHeadersAndKeepTheOthers() {
        givenSendSucceeds();

        replayer().replay(deadLetter(headersOfAFailedDelivery()), ReplayTrigger.MANUAL);

        assertThat(recordCaptor.getValue().headers().toArray()).extracting(Header::key)
                .containsExactly(TRACE_PARENT);
    }

    @Test
    @DisplayName("Should report a send that fails")
    void shouldReportASendThatFails() {
        when(template.send(recordCaptor.capture()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> replayer().replay(deadLetter(new RecordHeaders()), ReplayTrigger.MANUAL))
                .isInstanceOf(DeadLetterReplayException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(replays("failure")).isEqualTo(1.0);
        assertThat(replays("success")).isZero();
    }

    @Test
    @DisplayName("Should report a send that is not confirmed in time")
    void shouldReportASendThatIsNotConfirmedInTime() {
        when(template.send(recordCaptor.capture())).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> replayer().replay(deadLetter(new RecordHeaders()), ReplayTrigger.MANUAL))
                .isInstanceOf(DeadLetterReplayException.class);
    }

    @Test
    @DisplayName("Should report a send that is rejected before it starts")
    void shouldReportASendThatIsRejectedBeforeItStarts() {
        when(template.send(recordCaptor.capture())).thenThrow(new IllegalStateException("producer closed"));

        assertThatThrownBy(() -> replayer().replay(deadLetter(new RecordHeaders()), ReplayTrigger.MANUAL))
                .isInstanceOf(DeadLetterReplayException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

}
