package com.joaoPBessa.payments.consumer.controllers;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoPBessa.payments.consumer.api.dto.request.ReplayDeadLettersRequest;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterMessage;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterPayload;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterReadException;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterReplayService;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterService;
import com.joaoPBessa.payments.consumer.dlt.ReplayResult;
import com.joaopBessa.payments.common.avro.PaymentEvent;
import com.joaopBessa.payments.common.avro.PaymentMethod;

@WebMvcTest(controllers = DeadLetterController.class, properties = {
        "spring.cloud.vault.enabled=false", "spring.cloud.bootstrap.enabled=false" })
@DisplayName("GET /api/v1/dead-letters")
class DeadLetterControllerTest {

    private static final String URL = "/api/v1/dead-letters";
    private static final String REPLAY_URL = "/api/v1/dead-letters/replay";
    private static final Instant TIMESTAMP = Instant.parse("2026-11-09T11:56:10.123Z");
    private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-11-09T11:56:09.900Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeadLetterService deadLetterService;

    @MockitoBean
    private DeadLetterReplayService deadLetterReplayService;

    private DeadLetterMessage decodedMessage() {
        PaymentEvent event = PaymentEvent.newBuilder()
                .setTransactionId("tx-1")
                .setEventTimestamp(EVENT_TIMESTAMP)
                .setSourceAccount("000000")
                .setTargetAccount("910002")
                .setAmount(new BigDecimal("12.50"))
                .setCurrency("USD")
                .setPaymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
        return new DeadLetterMessage(0, 3, "tx-1", TIMESTAMP, DeadLetterPayload.decoded(event));
    }

    private DeadLetterMessage undecodableMessage() {
        return new DeadLetterMessage(1, 8, "poison", TIMESTAMP,
                DeadLetterPayload.undecodable("this is not an avro payload"));
    }

    @Test
    @DisplayName("Should return the decoded events with snake_case fields")
    void shouldReturnTheDecodedEventsWithSnakeCaseFields() throws Exception {
        when(deadLetterService.oldestMessages()).thenReturn(List.of(decodedMessage()));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].partition").value(0))
                .andExpect(jsonPath("$[0].offset").value(3))
                .andExpect(jsonPath("$[0].key").value("tx-1"))
                .andExpect(jsonPath("$[0].timestamp").value("2026-11-09T11:56:10.123Z"))
                .andExpect(jsonPath("$[0].payload.transaction_id").value("tx-1"))
                .andExpect(jsonPath("$[0].payload.event_timestamp").value("2026-11-09T11:56:09.900Z"))
                .andExpect(jsonPath("$[0].payload.source_account").value("000000"))
                .andExpect(jsonPath("$[0].payload.target_account").value("910002"))
                .andExpect(jsonPath("$[0].payload.amount").value(12.50))
                .andExpect(jsonPath("$[0].payload.currency").value("USD"))
                .andExpect(jsonPath("$[0].payload.payment_method").value("credit_card"))
                .andExpect(jsonPath("$[0].raw_payload").doesNotExist());

        verify(deadLetterService, times(1)).oldestMessages();
        verifyNoMoreInteractions(deadLetterService);
    }

    @Test
    @DisplayName("Should return the raw text of a message that is not a valid event")
    void shouldReturnTheRawTextOfAMessageThatIsNotAValidEvent() throws Exception {
        when(deadLetterService.oldestMessages()).thenReturn(List.of(undecodableMessage()));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payload").doesNotExist())
                .andExpect(jsonPath("$[0].raw_payload").value("this is not an avro payload"));
    }

    @Test
    @DisplayName("Should return an empty list when the dead letter topic is empty")
    void shouldReturnAnEmptyListWhenTheDeadLetterTopicIsEmpty() throws Exception {
        when(deadLetterService.oldestMessages()).thenReturn(List.of());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Should answer 503 when the dead letter topic cannot be read")
    void shouldAnswer503WhenTheDeadLetterTopicCannotBeRead() throws Exception {
        when(deadLetterService.oldestMessages())
                .thenThrow(new DeadLetterReadException("Could not read the dead letter topic", new RuntimeException()));

        mockMvc.perform(get(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The dead letter topic is unavailable, try again later"));
    }

    @Test
    @DisplayName("POST replay -> Should answer 202 with the outcome of each requested id")
    void shouldAnswer202WithTheOutcomeOfEachRequestedId() throws Exception {
        when(deadLetterReplayService.replay(List.of("tx-1", "tx-2", "tx-3"))).thenReturn(List.of(
                ReplayResult.replayed("tx-1", 0, 3), ReplayResult.notFound("tx-2"), ReplayResult.failed("tx-3")));

        mockMvc.perform(post(REPLAY_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_ids\": [\"tx-1\", \"tx-2\", \"tx-3\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].transaction_id").value("tx-1"))
                .andExpect(jsonPath("$.results[0].status").value("replayed"))
                .andExpect(jsonPath("$.results[0].partition").value(0))
                .andExpect(jsonPath("$.results[0].offset").value(3))
                .andExpect(jsonPath("$.results[1].transaction_id").value("tx-2"))
                .andExpect(jsonPath("$.results[1].status").value("not_found"))
                .andExpect(jsonPath("$.results[1].partition").doesNotExist())
                .andExpect(jsonPath("$.results[1].offset").doesNotExist())
                .andExpect(jsonPath("$.results[2].status").value("failed"));

        verify(deadLetterReplayService, times(1)).replay(List.of("tx-1", "tx-2", "tx-3"));
        verifyNoMoreInteractions(deadLetterReplayService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"transaction_ids\": []}",
            "{\"transaction_ids\": [\"tx-1\", \" \"]}",
            "{\"transaction_ids\": null}",
            "{\"transaction_ids\": \"tx-1\"}",
            "not json" })
    @DisplayName("POST replay -> Should answer 400 as a problem detail for an invalid body")
    void shouldAnswer400AsAProblemDetailForAnInvalidBody(String body) throws Exception {
        mockMvc.perform(post(REPLAY_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(deadLetterReplayService);
    }

    @Test
    @DisplayName("POST replay -> Should answer 400 when more ids than the limit are requested")
    void shouldAnswer400WhenMoreIdsThanTheLimitAreRequested() throws Exception {
        String ids = IntStream.rangeClosed(1, ReplayDeadLettersRequest.MAX_IDS + 1)
                .mapToObj(index -> "\"tx-" + index + "\"")
                .collect(Collectors.joining(","));

        mockMvc.perform(post(REPLAY_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_ids\": [" + ids + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(deadLetterReplayService);
    }

    @Test
    @DisplayName("POST replay -> Should answer 503 when the dead letter topic cannot be read")
    void shouldAnswer503WhenTheDeadLetterTopicCannotBeReadForAReplay() throws Exception {
        when(deadLetterReplayService.replay(List.of("tx-1")))
                .thenThrow(new DeadLetterReadException("Could not read the dead letter topic", new RuntimeException()));

        mockMvc.perform(post(REPLAY_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction_ids\": [\"tx-1\"]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

}
