package com.mphasis.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mphasis.gateway.client.AccountClient;
import com.mphasis.gateway.dto.*;
import com.mphasis.gateway.entity.*;
import com.mphasis.gateway.exception.AccountServiceUnavailableException;
import com.mphasis.gateway.repository.EventRepository;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository repository;
    private final AccountClient accountClient;
    private final ObjectMapper mapper;
    private final Counter submittedCounter;
    public EventService(EventRepository repository, AccountClient accountClient,
                        ObjectMapper mapper, MeterRegistry meterRegistry) {
        this.repository=repository; this.accountClient=accountClient; this.mapper=mapper;
        this.submittedCounter=Counter.builder("events.submitted").description("Number of event submission attempts").register(meterRegistry);
    }

    public SubmissionResult submit(EventRequest request) {
        log.info("Received event eventId={} accountId={} type={}", request.eventId(), request.accountId(), request.type());
        submittedCounter.increment();
        EventEntity entity = repository.findByEventId(request.eventId()).orElse(null);
        boolean duplicate = entity != null;
        if (entity == null) {
            entity = new EventEntity(request.eventId(),request.accountId(),request.type(),request.amount(),
                request.currency(),request.eventTimestamp(),toJson(request.metadata()),EventStatus.PENDING);
            entity = repository.saveAndFlush(entity);
        } else if (entity.getStatus() == EventStatus.PROCESSED) {
            return new SubmissionResult(toResponse(entity,true), true);
        }

        // The Account Service also enforces eventId uniqueness. This protects against
        // a timeout after it committed but before the Gateway received the response.
        accountClient.applyTransaction(new AccountTransactionRequest(entity.getEventId(),entity.getAccountId(),
            entity.getType(),entity.getAmount(),entity.getCurrency(),entity.getEventTimestamp()));

        markProcessed(entity.getEventId());
        EventEntity refreshed = repository.findByEventId(entity.getEventId()).orElseThrow();
        return new SubmissionResult(toResponse(refreshed,duplicate),duplicate);
    }

    @Transactional
    protected void markProcessed(String eventId) {
        EventEntity entity = repository.findByEventId(eventId).orElseThrow();
        entity.markProcessed();
        repository.save(entity);
    }

    @Transactional(readOnly=true)
    public EventResponse get(String eventId) {
        return repository.findByEventId(eventId).map(e -> toResponse(e,false))
            .orElseThrow(() -> new EventNotFoundException("Event not found: "+eventId));
    }

    @Transactional(readOnly=true)
    public List<EventResponse> list(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId).stream()
            .map(e -> toResponse(e,false)).toList();
    }

    private String toJson(Object metadata) {
        if (metadata == null) return null;
        try { return mapper.writeValueAsString(metadata); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid metadata",ex); }
    }


    private EventResponse toResponse(EventEntity e, boolean duplicate) {
        return new EventResponse(
                e.getEventId(),
                e.getAccountId(),
                e.getType(),
                e.getAmount(),
                e.getCurrency(),
                e.getEventTimestamp(),
                fromJson(e.getMetadataJson()),
                e.getStatus(),
                duplicate,
                e.getReceivedAt()
        );
    }

    private Map<String, Object> fromJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }

        try {
            return mapper.readValue(
                    metadataJson,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Unable to deserialize event metadata", ex
            );
        }
    }


    public record SubmissionResult(EventResponse event, boolean duplicate) {}
    public static class EventNotFoundException extends RuntimeException {
        public EventNotFoundException(String message){super(message);}
    }
}
