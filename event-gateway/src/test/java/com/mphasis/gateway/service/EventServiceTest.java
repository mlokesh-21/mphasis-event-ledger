
package com.mphasis.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mphasis.gateway.client.AccountClient;
import com.mphasis.gateway.dto.EventRequest;
import com.mphasis.gateway.entity.EventEntity;
import com.mphasis.gateway.entity.EventStatus;
import com.mphasis.gateway.repository.EventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository repository;

    @Mock
    private AccountClient accountClient;

    private SimpleMeterRegistry meterRegistry;
    private EventService service;

    private EventRequest request;

    private final Instant eventTime =
            Instant.parse("2026-05-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        service = new EventService(
                repository,
                accountClient,
                new ObjectMapper(),
                meterRegistry
        );

        request = new EventRequest(
                "evt-1",
                "acct-1",
                "CREDIT",
                new BigDecimal("100"),
                "USD",
                eventTime,
                Map.of("source", "test")
        );
    }

    @Test
    void submitShouldSaveAndProcessNewEvent() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, "{\"source\":\"test\"}",
                EventStatus.PENDING
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(entity),
                        Optional.of(entity)
                );

        when(repository.saveAndFlush(any(EventEntity.class)))
                .thenReturn(entity);

        var result = service.submit(request);

        assertNotNull(result);
        assertFalse(result.duplicate());
        assertEquals("evt-1", result.event().eventId());
        assertEquals(EventStatus.PROCESSED, result.event().status());

        verify(repository).saveAndFlush(any(EventEntity.class));
        verify(accountClient).applyTransaction(any());
        verify(repository).save(entity);
    }

    @Test
    void processedDuplicateShouldNotCallAccountServiceAgain() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, null,
                EventStatus.PROCESSED
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(Optional.of(entity));

        var result = service.submit(request);

        assertTrue(result.duplicate());
        assertTrue(result.event().duplicate());
        assertEquals("evt-1", result.event().eventId());

        verifyNoInteractions(accountClient);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void pendingEventShouldBeRetried() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, null,
                EventStatus.PENDING
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(Optional.of(entity), Optional.of(entity));

        var result = service.submit(request);

        assertTrue(result.duplicate());
        assertEquals(EventStatus.PROCESSED, result.event().status());

        verify(accountClient).applyTransaction(any());
        verify(repository).save(entity);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void submitShouldIncrementSubmittedMetric() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, "{\"source\":\"test\"}",
                EventStatus.PENDING
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(entity),
                        Optional.of(entity)
                );

        when(repository.saveAndFlush(any(EventEntity.class)))
                .thenReturn(entity);

        service.submit(request);

        double count = meterRegistry
                .counter("events.submitted")
                .count();

        assertEquals(1.0, count);
    }

    @Test
    void submitShouldSerializeMetadata() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, "{\"source\":\"test\"}",
                EventStatus.PENDING
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(entity),
                        Optional.of(entity)
                );

        when(repository.saveAndFlush(any(EventEntity.class)))
                .thenReturn(entity);

        service.submit(request);

        verify(repository).saveAndFlush(argThat(saved ->
                saved.getMetadataJson() != null
                        && saved.getMetadataJson().contains("source")
                        && saved.getMetadataJson().contains("test")
        ));
    }

    @Test
    void getShouldReturnExistingEvent() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, null,
                EventStatus.PROCESSED
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(Optional.of(entity));

        var result = service.get("evt-1");

        assertNotNull(result);
        assertEquals("evt-1", result.eventId());
        assertEquals("acct-1", result.accountId());
        assertEquals(EventStatus.PROCESSED, result.status());
    }

    @Test
    void getShouldThrowExceptionWhenEventDoesNotExist() {
        when(repository.findByEventId("missing-event"))
                .thenReturn(Optional.empty());

        assertThrows(
                EventService.EventNotFoundException.class,
                () -> service.get("missing-event")
        );
    }

    @Test
    void listShouldReturnEventsForAccount() {
        EventEntity first = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, null,
                EventStatus.PROCESSED
        );

        EventEntity second = new EventEntity(
                "evt-2", "acct-1", "DEBIT",
                new BigDecimal("20"), "USD",
                eventTime.plusSeconds(60), null,
                EventStatus.PROCESSED
        );

        when(repository.findByAccountIdOrderByEventTimestampAsc("acct-1"))
                .thenReturn(List.of(first, second));

        var result = service.list("acct-1");

        assertEquals(2, result.size());
        assertEquals("evt-1", result.get(0).eventId());
        assertEquals("evt-2", result.get(1).eventId());
    }

    @Test
    void listShouldReturnEmptyListWhenAccountHasNoEvents() {
        when(repository.findByAccountIdOrderByEventTimestampAsc("acct-empty"))
                .thenReturn(List.of());

        var result = service.list("acct-empty");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getShouldDeserializeMetadata() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, "{\"source\":\"test\"}",
                EventStatus.PROCESSED
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(Optional.of(entity));

        var result = service.get("evt-1");

        assertNotNull(result.metadata());
        assertEquals("test", result.metadata().get("source"));
    }

    @Test
    void getShouldReturnNullMetadataWhenMetadataIsMissing() {
        EventEntity entity = new EventEntity(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                eventTime, null,
                EventStatus.PROCESSED
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(Optional.of(entity));

        var result = service.get("evt-1");

        assertNull(result.metadata());
    }
}
