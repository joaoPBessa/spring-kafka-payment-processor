package com.joaoPBessa.payments.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.joaoPBessa.payments.consumer.metrics.ReplayTrigger;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dead letter replay service")
class DeadLetterReplayServiceTest {

    private static final String DLT = "payment.events.DLT";
    private static final int MAX_BATCH_SIZE = 500;
    private static final long BASE_TIMESTAMP = 1_700_000_000_000L;
    private static final TopicPartition PARTITION_0 = new TopicPartition(DLT, 0);
    private static final TopicPartition PARTITION_1 = new TopicPartition(DLT, 1);

    @Mock
    private DeadLetterReader reader;

    @Mock
    private DeadLetterReplayer replayer;

    @Mock
    private DeadLetterTruncator truncator;

    private DeadLetterReplayService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterReplayService(reader, replayer, truncator, MAX_BATCH_SIZE);
    }

    private ConsumerRecord<String, byte[]> record(String key, int partition, long offset, long timestamp) {
        return new ConsumerRecord<>(DLT, partition, offset, timestamp, TimestampType.CREATE_TIME, 0, 0, key,
                "payload".getBytes(StandardCharsets.UTF_8), new RecordHeaders(), Optional.empty());
    }

    private ConsumerRecord<String, byte[]> record(String key, int partition, long offset) {
        return record(key, partition, offset, BASE_TIMESTAMP + offset);
    }

    @Test
    @DisplayName("Should do nothing when the dead letter topic is empty")
    void shouldDoNothingWhenTheDeadLetterTopicIsEmpty() {
        when(reader.pending(MAX_BATCH_SIZE)).thenReturn(List.of());

        ReplaySummary summary = service.replayPending();

        assertThat(summary).isEqualTo(new ReplaySummary(0, 0));
        verify(truncator, times(1)).truncate(Map.of());
        verifyNoInteractions(replayer);
    }

    @Test
    @DisplayName("Should replay the backlog in order and truncate each partition after its last replayed offset")
    void shouldReplayTheBacklogInOrderAndTruncateEachPartitionAfterItsLastReplayedOffset() {
        ConsumerRecord<String, byte[]> first = record("tx-1", 0, 0);
        ConsumerRecord<String, byte[]> second = record("tx-2", 0, 1);
        ConsumerRecord<String, byte[]> third = record("tx-3", 0, 2);
        ConsumerRecord<String, byte[]> other = record("tx-4", 1, 0);
        when(reader.pending(MAX_BATCH_SIZE)).thenReturn(List.of(first, second, third, other));

        ReplaySummary summary = service.replayPending();

        assertThat(summary).isEqualTo(new ReplaySummary(4, 0));
        verify(replayer, times(1)).replay(first, ReplayTrigger.SCHEDULED);
        verify(replayer, times(1)).replay(second, ReplayTrigger.SCHEDULED);
        verify(replayer, times(1)).replay(third, ReplayTrigger.SCHEDULED);
        verify(replayer, times(1)).replay(other, ReplayTrigger.SCHEDULED);
        verify(truncator, times(1)).truncate(Map.of(PARTITION_0, 3L, PARTITION_1, 1L));
        verifyNoMoreInteractions(replayer, truncator);
    }

    @Test
    @DisplayName("Should stop a partition at its first failure and keep the failed message and the ones after it")
    void shouldStopAPartitionAtItsFirstFailureAndKeepTheFailedMessageAndTheOnesAfterIt() {
        ConsumerRecord<String, byte[]> first = record("tx-1", 0, 0);
        ConsumerRecord<String, byte[]> failing = record("tx-2", 0, 1);
        ConsumerRecord<String, byte[]> skipped = record("tx-3", 0, 2);
        ConsumerRecord<String, byte[]> other = record("tx-4", 1, 0);
        when(reader.pending(MAX_BATCH_SIZE)).thenReturn(List.of(first, failing, skipped, other));
        doNothing().when(replayer).replay(first, ReplayTrigger.SCHEDULED);
        doThrow(new DeadLetterReplayException("broker down", new IllegalStateException()))
                .when(replayer).replay(failing, ReplayTrigger.SCHEDULED);

        ReplaySummary summary = service.replayPending();

        assertThat(summary).isEqualTo(new ReplaySummary(2, 1));
        verify(replayer, times(1)).replay(first, ReplayTrigger.SCHEDULED);
        verify(replayer, times(1)).replay(failing, ReplayTrigger.SCHEDULED);
        verify(replayer, times(1)).replay(other, ReplayTrigger.SCHEDULED);
        verify(truncator, times(1)).truncate(Map.of(PARTITION_0, 1L, PARTITION_1, 1L));
        verifyNoMoreInteractions(replayer, truncator);
    }

    @Test
    @DisplayName("Should not truncate a partition whose first message fails")
    void shouldNotTruncateAPartitionWhoseFirstMessageFails() {
        ConsumerRecord<String, byte[]> failing = record("tx-1", 0, 0);
        when(reader.pending(MAX_BATCH_SIZE)).thenReturn(List.of(failing));
        doThrow(new DeadLetterReplayException("broker down", new IllegalStateException()))
                .when(replayer).replay(failing, ReplayTrigger.SCHEDULED);

        ReplaySummary summary = service.replayPending();

        assertThat(summary).isEqualTo(new ReplaySummary(0, 1));
        verify(truncator, times(1)).truncate(Map.of());
    }

    @Test
    @DisplayName("Should report a truncation that fails")
    void shouldReportATruncationThatFails() {
        when(reader.pending(MAX_BATCH_SIZE)).thenReturn(List.of(record("tx-1", 0, 0)));
        doThrow(new DeadLetterReplayException("cannot truncate", new IllegalStateException()))
                .when(truncator).truncate(Map.of(PARTITION_0, 1L));

        assertThatThrownBy(() -> service.replayPending()).isInstanceOf(DeadLetterReplayException.class);
    }

    @Test
    @DisplayName("Should report a dead letter topic that cannot be read")
    void shouldReportADeadLetterTopicThatCannotBeRead() {
        when(reader.pending(MAX_BATCH_SIZE)).thenThrow(new DeadLetterReadException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.replayPending()).isInstanceOf(DeadLetterReadException.class);
        verifyNoInteractions(replayer, truncator);
    }

    @Test
    @DisplayName("Should skip a run that starts while another one is still going")
    void shouldSkipARunThatStartsWhileAnotherOneIsStillGoing() {
        ConsumerRecord<String, byte[]> first = record("tx-1", 0, 0);
        when(reader.pending(MAX_BATCH_SIZE)).thenReturn(List.of(first));
        ReplaySummary[] overlapping = new ReplaySummary[1];
        doAnswer(invocation -> {
            overlapping[0] = service.replayPending();
            return null;
        }).when(replayer).replay(first, ReplayTrigger.SCHEDULED);

        ReplaySummary summary = service.replayPending();

        assertThat(overlapping[0]).isEqualTo(ReplaySummary.empty());
        assertThat(summary).isEqualTo(new ReplaySummary(1, 0));
        verify(reader, times(1)).pending(MAX_BATCH_SIZE);
    }

    @Test
    @DisplayName("Should allow a new run after the previous one finished")
    void shouldAllowANewRunAfterThePreviousOneFinished() {
        when(reader.pending(MAX_BATCH_SIZE)).thenReturn(List.of());

        service.replayPending();
        service.replayPending();

        verify(reader, times(2)).pending(MAX_BATCH_SIZE);
    }

    @Test
    @DisplayName("Should replay the requested transaction ids reading the topic once and report each one in order")
    void shouldReplayTheRequestedTransactionIdsReadingTheTopicOnceAndReportEachOneInOrder() {
        ConsumerRecord<String, byte[]> wanted = record("tx-2", 0, 4);
        ConsumerRecord<String, byte[]> another = record("tx-9", 1, 7);
        when(reader.all()).thenReturn(List.of(wanted, record("tx-5", 0, 5), another));

        List<ReplayResult> results = service.replay(List.of("tx-9", "tx-unknown", "tx-2"));

        assertThat(results).containsExactly(
                ReplayResult.replayed("tx-9", 1, 7),
                ReplayResult.notFound("tx-unknown"),
                ReplayResult.replayed("tx-2", 0, 4));
        verify(reader, times(1)).all();
        verify(replayer, times(1)).replay(another, ReplayTrigger.MANUAL);
        verify(replayer, times(1)).replay(wanted, ReplayTrigger.MANUAL);
        verifyNoMoreInteractions(reader, replayer);
        verifyNoInteractions(truncator);
    }

    @Test
    @DisplayName("Should replay the most recent message when an id failed more than once")
    void shouldReplayTheMostRecentMessageWhenAnIdFailedMoreThanOnce() {
        ConsumerRecord<String, byte[]> older = record("tx-1", 0, 1, BASE_TIMESTAMP);
        ConsumerRecord<String, byte[]> newer = record("tx-1", 0, 5, BASE_TIMESTAMP + 1_000);
        when(reader.all()).thenReturn(List.of(older, newer));

        List<ReplayResult> results = service.replay(List.of("tx-1"));

        assertThat(results).containsExactly(ReplayResult.replayed("tx-1", 0, 5));
        verify(replayer, times(1)).replay(newer, ReplayTrigger.MANUAL);
        verifyNoMoreInteractions(replayer);
    }

    @Test
    @DisplayName("Should replay an id once even when it is requested twice")
    void shouldReplayAnIdOnceEvenWhenItIsRequestedTwice() {
        ConsumerRecord<String, byte[]> wanted = record("tx-1", 0, 1);
        when(reader.all()).thenReturn(List.of(wanted));

        List<ReplayResult> results = service.replay(List.of("tx-1", "tx-1"));

        assertThat(results).containsExactly(ReplayResult.replayed("tx-1", 0, 1));
        verify(replayer, times(1)).replay(wanted, ReplayTrigger.MANUAL);
    }

    @Test
    @DisplayName("Should report a failed send without stopping the other ids")
    void shouldReportAFailedSendWithoutStoppingTheOtherIds() {
        ConsumerRecord<String, byte[]> failing = record("tx-1", 0, 1);
        ConsumerRecord<String, byte[]> working = record("tx-2", 0, 2);
        when(reader.all()).thenReturn(List.of(failing, working));
        doThrow(new DeadLetterReplayException("broker down", new IllegalStateException()))
                .when(replayer).replay(failing, ReplayTrigger.MANUAL);

        List<ReplayResult> results = service.replay(List.of("tx-1", "tx-2"));

        assertThat(results).containsExactly(ReplayResult.failed("tx-1"), ReplayResult.replayed("tx-2", 0, 2));
        verify(replayer, times(1)).replay(working, ReplayTrigger.MANUAL);
    }

    @Test
    @DisplayName("Should ignore dead letters that have no key")
    void shouldIgnoreDeadLettersThatHaveNoKey() {
        when(reader.all()).thenReturn(List.of(record(null, 0, 1)));

        List<ReplayResult> results = service.replay(List.of("tx-1"));

        assertThat(results).containsExactly(ReplayResult.notFound("tx-1"));
        verifyNoInteractions(replayer);
    }

    @Test
    @DisplayName("Should report a dead letter topic that cannot be read when replaying ids")
    void shouldReportADeadLetterTopicThatCannotBeReadWhenReplayingIds() {
        when(reader.all()).thenThrow(new DeadLetterReadException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.replay(List.of("tx-1"))).isInstanceOf(DeadLetterReadException.class);
        verifyNoInteractions(replayer, truncator);
    }

}
