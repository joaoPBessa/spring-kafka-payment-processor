package com.joaoPBessa.payments.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dead letter reader")
class DeadLetterReaderTest {

    private static final String TOPIC = "payment.events.DLT";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_MESSAGES = 10;
    private static final long BASE_TIMESTAMP = 1_000L;

    private MockConsumer<String, byte[]> consumer;
    private DeadLetterReader reader;

    @BeforeEach
    void setUp() {
        consumer = new MockConsumer<>("earliest");
        reader = new DeadLetterReader(() -> consumer, TOPIC, READ_TIMEOUT);
    }

    private TopicPartition givenPartition(int partition, long endOffset) {
        TopicPartition topicPartition = new TopicPartition(TOPIC, partition);
        consumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        consumer.updateEndOffsets(Map.of(topicPartition, endOffset));
        return topicPartition;
    }

    private void givenTopicWithPartitions(int partitionCount) {
        List<PartitionInfo> infos = new ArrayList<>();
        for (int partition = 0; partition < partitionCount; partition++) {
            infos.add(new PartitionInfo(TOPIC, partition, null, null, null));
        }
        consumer.updatePartitions(TOPIC, infos);
    }

    private ConsumerRecord<String, byte[]> record(int partition, long offset, long timestamp) {
        String key = "key-" + partition + "-" + offset;
        return new ConsumerRecord<>(TOPIC, partition, offset, timestamp, TimestampType.CREATE_TIME, 0, 0, key,
                key.getBytes(StandardCharsets.UTF_8), new RecordHeaders(), Optional.empty());
    }

    private void givenRecordsAvailableOnPoll(List<ConsumerRecord<String, byte[]>> records) {
        consumer.schedulePollTask(() -> records.forEach(consumer::addRecord));
    }

    private List<ConsumerRecord<String, byte[]>> sequentialRecords(int partition, int count) {
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        for (int offset = 0; offset < count; offset++) {
            records.add(record(partition, offset, BASE_TIMESTAMP + offset));
        }
        return records;
    }

    private List<String> keys(List<ConsumerRecord<String, byte[]>> records) {
        return records.stream().map(ConsumerRecord::key).toList();
    }

    @Test
    @DisplayName("Should return nothing when the topic has no partitions yet")
    void shouldReturnNothingWhenTheTopicHasNoPartitionsYet() {
        assertThat(reader.oldest()).isEmpty();
        assertThat(consumer.closed()).isTrue();
    }

    @Test
    @DisplayName("Should return nothing when the partitions are empty")
    void shouldReturnNothingWhenThePartitionsAreEmpty() {
        givenTopicWithPartitions(1);
        givenPartition(0, 0);

        assertThat(reader.oldest()).isEmpty();
        assertThat(consumer.closed()).isTrue();
    }

    @Test
    @DisplayName("Should return every message when there are fewer than the limit")
    void shouldReturnEveryMessageWhenThereAreFewerThanTheLimit() {
        givenTopicWithPartitions(1);
        givenPartition(0, 3);
        givenRecordsAvailableOnPoll(sequentialRecords(0, 3));

        assertThat(keys(reader.oldest())).containsExactly("key-0-0", "key-0-1", "key-0-2");
    }

    @Test
    @DisplayName("Should return only the ten oldest messages when there are more")
    void shouldReturnOnlyTheTenOldestMessagesWhenThereAreMore() {
        givenTopicWithPartitions(1);
        givenPartition(0, 15);
        givenRecordsAvailableOnPoll(sequentialRecords(0, 15));

        List<ConsumerRecord<String, byte[]>> oldest = reader.oldest();

        assertThat(oldest).hasSize(MAX_MESSAGES);
        assertThat(oldest.get(0).offset()).isZero();
        assertThat(oldest.get(MAX_MESSAGES - 1).offset()).isEqualTo(MAX_MESSAGES - 1);
    }

    @Test
    @DisplayName("Should merge partitions by timestamp and keep the oldest overall")
    void shouldMergePartitionsByTimestampAndKeepTheOldestOverall() {
        givenTopicWithPartitions(2);
        givenPartition(0, 2);
        givenPartition(1, 2);
        givenRecordsAvailableOnPoll(List.of(
                record(0, 0, BASE_TIMESTAMP + 10),
                record(0, 1, BASE_TIMESTAMP + 30),
                record(1, 0, BASE_TIMESTAMP + 20),
                record(1, 1, BASE_TIMESTAMP + 40)));

        assertThat(keys(reader.oldest())).containsExactly("key-0-0", "key-1-0", "key-0-1", "key-1-1");
    }

    @Test
    @DisplayName("Should cut the merged result at the limit across partitions")
    void shouldCutTheMergedResultAtTheLimitAcrossPartitions() {
        givenTopicWithPartitions(2);
        givenPartition(0, 8);
        givenPartition(1, 8);
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        for (int offset = 0; offset < 8; offset++) {
            records.add(record(0, offset, BASE_TIMESTAMP + offset * 2L));
            records.add(record(1, offset, BASE_TIMESTAMP + offset * 2L + 1));
        }
        givenRecordsAvailableOnPoll(records);

        List<ConsumerRecord<String, byte[]>> oldest = reader.oldest();

        assertThat(oldest).hasSize(MAX_MESSAGES);
        assertThat(oldest).extracting(ConsumerRecord::timestamp)
                .isSorted()
                .first().isEqualTo(BASE_TIMESTAMP);
        assertThat(oldest.get(MAX_MESSAGES - 1).timestamp()).isEqualTo(BASE_TIMESTAMP + MAX_MESSAGES - 1);
    }

    @Test
    @DisplayName("Should never commit offsets or join a group")
    void shouldNeverCommitOffsetsOrJoinAGroup() {
        givenTopicWithPartitions(1);
        givenPartition(0, 1);
        givenRecordsAvailableOnPoll(sequentialRecords(0, 1));

        reader.oldest();

        assertThat(consumer.subscription()).isEmpty();
    }

    @Test
    @DisplayName("Should close the consumer and report a Kafka failure as a read failure")
    void shouldCloseTheConsumerAndReportAKafkaFailureAsAReadFailure() {
        givenTopicWithPartitions(1);
        givenPartition(0, 1);
        consumer.setPollException(new KafkaException("broker down"));

        assertThatThrownBy(() -> reader.oldest())
                .isInstanceOf(DeadLetterReadException.class)
                .hasCauseInstanceOf(KafkaException.class);
        assertThat(consumer.closed()).isTrue();
    }

}
