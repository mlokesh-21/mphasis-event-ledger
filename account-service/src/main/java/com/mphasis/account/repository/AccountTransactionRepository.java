package com.mphasis.account.repository;

import com.mphasis.account.entity.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
    Optional<AccountTransaction> findByEventId(String eventId);
    List<AccountTransaction> findByAccountIdOrderByEventTimestampDesc(String accountId);

    @Query("select coalesce(sum(case when t.type = 'CREDIT' then t.amount else -t.amount end), 0) " +
           "from AccountTransaction t where t.accountId = :accountId")
    BigDecimal calculateBalance(@Param("accountId") String accountId);

    @Query("select count(t) from AccountTransaction t where t.accountId = :accountId")
    long countForAccount(@Param("accountId") String accountId);
}
