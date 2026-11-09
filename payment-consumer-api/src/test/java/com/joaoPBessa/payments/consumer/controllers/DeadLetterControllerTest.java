package com.joaoPBessa.payments.consumer.controllers;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoPBessa.payments.consumer.api.dto.response.DeadLetterMessageResponse;
import com.joaoPBessa.payments.consumer.api.dto.response.PaymentEventResponse;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterReadException;
import com.joaoPBessa.payments.consumer.dlt.DeadLetterService;
import com.joaopBessa.payments.common.domain.PaymentMethod;

@WebMvcTest(controllers = DeadLetterController.class, properties = {
        "spring.cloud.vault.enabled=false", "spring.cloud.bootstrap.enabled=false" })
@DisplayName("GET /api/v1/dead-letters")
class DeadLetterControllerTest {

    private static final String URL = "/api/v1/dead-letters";
    private static final Instant TIMESTAMP = Instant.parse("2026-11-09T11:56:10.123Z");
    private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-11-09T11:56:09.900Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeadLetterService deadLetterService;

    private DeadLetterMessageResponse decodedMessage() {
        PaymentEventResponse payload = new PaymentEventResponse("tx-1", EVENT_TIMESTAMP, "000000", "910002",
                new BigDecimal("12.50"), "USD", PaymentMethod.CREDIT_CARD);
        return new DeadLetterMessageResponse(0, 3, "tx-1", TIMESTAMP, payload, null);
    }

    private DeadLetterMessageResponse undecodableMessage() {
        return new DeadLetterMessageResponse(1, 8, "poison", TIMESTAMP, null, "this is not an avro payload");
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

}
