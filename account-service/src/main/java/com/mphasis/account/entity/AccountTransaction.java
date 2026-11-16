package com.mphasis.account.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_transactions",
       uniqueConstraints = @UniqueConstraint(name = "uk_account_event_id", columnNames = "event_id"))
public class AccountTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @Column(name="created_at", nullable=false)
    private Instant createdAt = Instant.now();

    protected AccountTransaction() {}
    public AccountTransaction(String eventId, String accountId, String type, BigDecimal amount,
                              String currency, Instant eventTimestamp) {
        this.eventId=eventId; this.accountId=accountId; this.type=type; this.amount=amount;
        this.currency=currency; this.eventTimestamp=eventTimestamp;
    }
    public Long getId(){return id;}
    public String getEventId(){return eventId;}
    public String getAccountId(){return accountId;}
    public String getType(){return type;}
    public BigDecimal getAmount(){return amount;}
    public String getCurrency(){return currency;}
    public Instant getEventTimestamp(){return eventTimestamp;}
    public Instant getCreatedAt(){return createdAt;}
}
