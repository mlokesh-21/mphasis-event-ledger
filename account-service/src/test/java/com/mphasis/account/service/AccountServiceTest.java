
package com.mphasis.account.service;

import com.mphasis.account.dto.TransactionRequest;
import com.mphasis.account.entity.AccountTransaction;
import com.mphasis.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountTransactionRepository repository;

    @InjectMocks
    private AccountService service;

    private TransactionRequest request;

    @BeforeEach
    void setUp() {
        request = new TransactionRequest(
                "evt-1",
                "acct-1",
                "CREDIT",
                new BigDecimal("100"),
                "USD",
                Instant.parse("2026-05-15T10:00:00Z")
        );
    }

    @Test
    void applyShouldSaveNewTransaction() {
        AccountTransaction transaction = new AccountTransaction(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                Instant.parse("2026-05-15T10:00:00Z")
        );

        when(repository.findByEventId("evt-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(AccountTransaction.class)))
                .thenReturn(transaction);

        var result = service.apply(request);

        assertNotNull(result);
        assertEquals("evt-1", result.eventId());
        assertEquals("acct-1", result.accountId());
        assertEquals(new BigDecimal("100"), result.amount());
        assertFalse(result.duplicate());

        verify(repository).findByEventId("evt-1");
        verify(repository).saveAndFlush(any(AccountTransaction.class));
    }

    @Test
    void duplicateEventShouldNotInsertAgain() {
        AccountTransaction existing = new AccountTransaction(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                Instant.parse("2026-05-15T10:00:00Z")
        );

        when(repository.findByEventId("evt-1"))
                .thenReturn(Optional.of(existing));

        var result = service.apply(request);

        assertTrue(result.duplicate());
        assertEquals("evt-1", result.eventId());

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void balanceShouldReturnCalculatedBalance() {
        when(repository.calculateBalance("acct-1"))
                .thenReturn(new BigDecimal("750.00"));

        BigDecimal balance = service.balance("acct-1");

        assertEquals(new BigDecimal("750.00"), balance);
        verify(repository).calculateBalance("acct-1");
    }

    @Test
    void balanceShouldReturnZeroForAccountWithoutTransactions() {
        when(repository.calculateBalance("acct-empty"))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal balance = service.balance("acct-empty");

        assertEquals(BigDecimal.ZERO, balance);
    }

    @Test
    void detailsShouldReturnAccountAndRecentTransactions() {
        AccountTransaction transaction = new AccountTransaction(
                "evt-1", "acct-1", "CREDIT",
                new BigDecimal("100"), "USD",
                Instant.parse("2026-05-15T10:00:00Z")
        );

        when(repository.findByAccountIdOrderByEventTimestampDesc("acct-1"))
                .thenReturn(List.of(transaction));

        when(repository.calculateBalance("acct-1"))
                .thenReturn(new BigDecimal("100"));

        var details = service.details("acct-1");

        assertEquals("acct-1", details.accountId());
        assertEquals(new BigDecimal("100"), details.balance());
        assertEquals(1, details.recentTransactions().size());
        assertEquals("evt-1",
                details.recentTransactions().get(0).eventId());
    }

    @Test
    void detailsShouldReturnMaximumTwentyRecentTransactions() {
        List<AccountTransaction> transactions = new ArrayList<>();

        for (int i = 1; i <= 25; i++) {
            transactions.add(new AccountTransaction(
                    "evt-" + i,
                    "acct-1",
                    "CREDIT",
                    new BigDecimal("10"),
                    "USD",
                    Instant.parse("2026-05-15T10:00:00Z").plusSeconds(i)
            ));
        }

        when(repository.findByAccountIdOrderByEventTimestampDesc("acct-1"))
                .thenReturn(transactions);

        when(repository.calculateBalance("acct-1"))
                .thenReturn(new BigDecimal("250"));

        var details = service.details("acct-1");

        assertEquals(20, details.recentTransactions().size());
        assertEquals(new BigDecimal("250"), details.balance());
    }
}
