package com.joaoPBessa.payments.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DeleteRecordsResult;
import org.apache.kafka.clients.admin.DeletedRecords;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dead letter truncator")
class DeadLetterTruncatorTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final TopicPartition PARTITION_0 = new TopicPartition("payment.events.DLT", 0);
    private static final TopicPartition PARTITION_1 = new TopicPartition("payment.events.DLT", 1);

    @Mock
    private Admin admin;

    private DeadLetterTruncator truncator() {
        return new DeadLetterTruncator(() -> admin, TIMEOUT);
    }

    private DeleteRecordsResult succeededFor(TopicPartition... partitions) {
        Map<TopicPartition, KafkaFuture<DeletedRecords>> futures = new HashMap<>();
        for (TopicPartition partition : partitions) {
            futures.put(partition, KafkaFuture.completedFuture(new DeletedRecords(0)));
        }
        return new DeleteRecordsResult(futures);
    }

    @Test
    @DisplayName("Should delete the records before each given offset and close the admin client")
    void shouldDeleteTheRecordsBeforeEachGivenOffsetAndCloseTheAdminClient() {
        Map<TopicPartition, RecordsToDelete> expected = Map.of(
                PARTITION_0, RecordsToDelete.beforeOffset(5L),
                PARTITION_1, RecordsToDelete.beforeOffset(2L));
        when(admin.deleteRecords(expected)).thenReturn(succeededFor(PARTITION_0, PARTITION_1));

        truncator().truncate(Map.of(PARTITION_0, 5L, PARTITION_1, 2L));

        verify(admin, times(1)).deleteRecords(expected);
        verify(admin, times(1)).close();
    }

    @Test
    @DisplayName("Should not touch Kafka when there is nothing to delete")
    void shouldNotTouchKafkaWhenThereIsNothingToDelete() {
        truncator().truncate(Map.of());

        verifyNoInteractions(admin);
    }

    @Test
    @DisplayName("Should report a deletion that fails and still close the admin client")
    void shouldReportADeletionThatFailsAndStillCloseTheAdminClient() {
        Map<TopicPartition, KafkaFuture<DeletedRecords>> futures = Map.of(PARTITION_0, failedFuture());
        when(admin.deleteRecords(Map.of(PARTITION_0, RecordsToDelete.beforeOffset(5L))))
                .thenReturn(new DeleteRecordsResult(futures));

        assertThatThrownBy(() -> truncator().truncate(Map.of(PARTITION_0, 5L)))
                .isInstanceOf(DeadLetterReplayException.class);
        verify(admin, times(1)).close();
    }

    private KafkaFuture<DeletedRecords> failedFuture() {
        KafkaFutureImpl<DeletedRecords> future = new KafkaFutureImpl<>();
        future.completeExceptionally(new TimeoutException("broker down"));
        return future;
    }

}
