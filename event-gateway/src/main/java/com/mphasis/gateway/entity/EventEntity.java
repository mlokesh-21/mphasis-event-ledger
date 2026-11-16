package com.mphasis.gateway.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="events", uniqueConstraints=@UniqueConstraint(name="uk_event_id", columnNames="event_id"))
public class EventEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="event_id", nullable=false, unique=true, length=100)
    private String eventId;
    @Column(name="account_id", nullable=false, length=100)
    private String accountId;
    @Column(nullable=false, length=10)
    private String type;
    @Column(nullable=false, precision=19, scale=4)
    private BigDecimal amount;
    @Column(nullable=false, length=10)
    private String currency;
    @Column(name="event_timestamp", nullable=false)
    private Instant eventTimestamp;
    @Column(columnDefinition="CLOB")
    private String metadataJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private EventStatus status;
    @Column(name="received_at", nullable=false)
    private Instant receivedAt = Instant.now();

    protected EventEntity() {}
    public EventEntity(String eventId,String accountId,String type,BigDecimal amount,String currency,
                       Instant eventTimestamp,String metadataJson,EventStatus status) {
        this.eventId=eventId; this.accountId=accountId; this.type=type; this.amount=amount;
        this.currency=currency; this.eventTimestamp=eventTimestamp; this.metadataJson=metadataJson; this.status=status;
    }
    public void markProcessed(){this.status=EventStatus.PROCESSED;}
    public String getEventId(){return eventId;}
    public String getAccountId(){return accountId;}
    public String getType(){return type;}
    public BigDecimal getAmount(){return amount;}
    public String getCurrency(){return currency;}
    public Instant getEventTimestamp(){return eventTimestamp;}
    public String getMetadataJson(){return metadataJson;}
    public EventStatus getStatus(){return status;}
    public Instant getReceivedAt(){return receivedAt;}
}
