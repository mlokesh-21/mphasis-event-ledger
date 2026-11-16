
package com.mphasis.account.controller;

import com.mphasis.account.dto.TransactionRequest;
import com.mphasis.account.dto.TransactionResponse;
import com.mphasis.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> apply(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody TransactionRequest request) {

        if (!accountId.equals(request.accountId())) {
            throw new IllegalArgumentException(
                    "Path accountId must match request accountId"
            );
        }

        TransactionResponse result = service.apply(request);

        return ResponseEntity
                .status(result.duplicate()
                        ? HttpStatus.OK
                        : HttpStatus.CREATED)
                .body(result);
    }

    @GetMapping("/{accountId}/balance")
    public Map<String, Object> balance(
            @PathVariable("accountId") String accountId) {

        BigDecimal balance = service.balance(accountId);

        return Map.of(
                "accountId", accountId,
                "balance", balance
        );
    }

    @GetMapping("/{accountId}")
    public AccountService.AccountDetails details(
            @PathVariable("accountId") String accountId) {

        return service.details(accountId);
    }
}
