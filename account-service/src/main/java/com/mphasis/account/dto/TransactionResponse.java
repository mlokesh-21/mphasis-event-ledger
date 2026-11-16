package com.mphasis.account.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record TransactionResponse(String eventId, String accountId, String type,
                                  BigDecimal amount, String currency, Instant eventTimestamp,
                                  boolean duplicate) {}
