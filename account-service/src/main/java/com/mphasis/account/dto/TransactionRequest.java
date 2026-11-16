package com.mphasis.account.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
    @NotBlank String eventId,
    @NotBlank String accountId,
    @NotBlank @Pattern(regexp="CREDIT|DEBIT", message="type must be CREDIT or DEBIT") String type,
    @NotNull @DecimalMin(value="0.0", inclusive=false, message="amount must be greater than zero") BigDecimal amount,
    @NotBlank String currency,
    @NotNull Instant eventTimestamp
) {}
