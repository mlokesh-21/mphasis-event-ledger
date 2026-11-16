
package com.mphasis.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mphasis.gateway.client.AccountClient;
import com.mphasis.gateway.dto.EventRequest;
import com.mphasis.gateway.dto.EventResponse;
import com.mphasis.gateway.entity.EventStatus;
import com.mphasis.gateway.exception.AccountServiceUnavailableException;
import com.mphasis.gateway.service.EventService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@Import(ApiExceptionHandler.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private AccountClient accountClient;

    private EventResponse createResponse(boolean duplicate) {
        return new EventResponse(
                "evt-101",
                "acct-101",
                "CREDIT",
                new BigDecimal("500.00"),
                "INR",
                Instant.parse("2026-11-16T10:00:00Z"),
                Map.of("source", "test"),
                EventStatus.PROCESSED,
                duplicate,
                Instant.parse("2026-11-16T10:01:00Z")
        );
    }

    @Test
    void submitEventShouldReturn201() throws Exception {

        EventResponse response = createResponse(false);

        when(eventService.submit(any(EventRequest.class)))
                .thenReturn(new EventService.SubmissionResult(response, false));

        String request = """
                {
                  "eventId": "evt-101",
                  "accountId": "acct-101",
                  "type": "CREDIT",
                  "amount": 500.00,
                  "currency": "INR",
                  "eventTimestamp": "2026-11-16T10:00:00Z",
                  "metadata": {
                    "source": "test"
                  }
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-101"))
                .andExpect(jsonPath("$.accountId").value("acct-101"))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void duplicateEventShouldReturn200() throws Exception {

        EventResponse response = createResponse(true);

        when(eventService.submit(any(EventRequest.class)))
                .thenReturn(new EventService.SubmissionResult(response, true));

        String request = """
                {
                  "eventId": "evt-101",
                  "accountId": "acct-101",
                  "type": "CREDIT",
                  "amount": 500.00,
                  "currency": "INR",
                  "eventTimestamp": "2026-11-16T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void invalidEventTypeShouldReturn400() throws Exception {

        String request = """
                {
                  "eventId": "evt-102",
                  "accountId": "acct-101",
                  "type": "INVALID",
                  "amount": 500.00,
                  "currency": "INR",
                  "eventTimestamp": "2026-11-16T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void zeroAmountShouldReturn400() throws Exception {

        String request = """
                {
                  "eventId": "evt-103",
                  "accountId": "acct-101",
                  "type": "CREDIT",
                  "amount": 0,
                  "currency": "INR",
                  "eventTimestamp": "2026-11-16T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEventShouldReturn200() throws Exception {

        when(eventService.get("evt-101"))
                .thenReturn(createResponse(false));

        mockMvc.perform(get("/events/evt-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-101"))
                .andExpect(jsonPath("$.type").value("CREDIT"));
    }

    @Test
    void getMissingEventShouldReturn404() throws Exception {

        when(eventService.get("missing-event"))
                .thenThrow(new EventService.EventNotFoundException(
                        "Event not found: missing-event"
                ));

        mockMvc.perform(get("/events/missing-event"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listEventsShouldReturn200() throws Exception {

        when(eventService.list("acct-101"))
                .thenReturn(List.of(createResponse(false)));

        mockMvc.perform(get("/events")
                        .param("account", "acct-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-101"))
                .andExpect(jsonPath("$[0].accountId").value("acct-101"));
    }

    @Test
    void getBalanceShouldReturn200() throws Exception {

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("accountId", "acct-101");
        response.put("balance", 500.00);

        when(accountClient.getBalance("acct-101"))
                .thenAnswer(invocation -> response);

        mockMvc.perform(get("/accounts/acct-101/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-101"))
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    void accountServiceFailureShouldReturn503() throws Exception {

        when(accountClient.getBalance("acct-101"))
                .thenThrow(new AccountServiceUnavailableException(
                        "Account Service unavailable"
                ));

        mockMvc.perform(get("/accounts/acct-101/balance"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }
}
