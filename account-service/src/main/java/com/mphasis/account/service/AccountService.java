package com.mphasis.account.service;

import com.mphasis.account.dto.*;
import com.mphasis.account.entity.AccountTransaction;
import com.mphasis.account.repository.AccountTransactionRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountTransactionRepository repository;
    public AccountService(AccountTransactionRepository repository) { this.repository = repository; }

    @Transactional
    public TransactionResponse apply(TransactionRequest request) {
        log.info("Applying transaction eventId={} accountId={} type={}", request.eventId(), request.accountId(), request.type());
        var existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            AccountTransaction t = existing.get();
            return toResponse(t, true);
        }
        AccountTransaction saved = repository.saveAndFlush(new AccountTransaction(
            request.eventId(), request.accountId(), request.type(), request.amount(),
            request.currency(), request.eventTimestamp()));
        return toResponse(saved, false);
    }

    @Transactional(readOnly=true)
    public BigDecimal balance(String accountId) {
        return repository.calculateBalance(accountId);
    }

    @Transactional(readOnly=true)
    public AccountDetails details(String accountId) {
        List<AccountTransaction> txs = repository.findByAccountIdOrderByEventTimestampDesc(accountId);
        return new AccountDetails(accountId, balance(accountId), txs.stream().limit(20).map(t -> toResponse(t,false)).toList());
    }

    private TransactionResponse toResponse(AccountTransaction t, boolean duplicate) {
        return new TransactionResponse(t.getEventId(), t.getAccountId(), t.getType(),
            t.getAmount(), t.getCurrency(), t.getEventTimestamp(), duplicate);
    }

    public record AccountDetails(String accountId, BigDecimal balance, List<TransactionResponse> recentTransactions) {}
}
