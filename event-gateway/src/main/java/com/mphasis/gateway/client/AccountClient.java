
package com.mphasis.gateway.client;

import com.mphasis.gateway.dto.AccountTransactionRequest;
import com.mphasis.gateway.exception.AccountServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class AccountClient {

    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public AccountClient(
            RestClient accountRestClient,
            CircuitBreaker accountCircuitBreaker) {

        this.client = accountRestClient;
        this.circuitBreaker = accountCircuitBreaker;
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");

        return traceId == null
                ? "trace-unavailable"
                : traceId;
    }

    public void applyTransaction(
            AccountTransactionRequest request) {

        try {

            circuitBreaker.executeRunnable(() ->
                    client.post()
                            .uri(
                                    "/accounts/{accountId}/transactions",
                                    request.accountId()
                            )
                            .header("X-Trace-Id", getTraceId())
                            .body(request)
                            .retrieve()
                            .toBodilessEntity()
            );

        } catch (CallNotPermittedException |
                 RestClientException ex) {

            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable; please retry later",
                    ex
            );

        } catch (RuntimeException ex) {

            throw new AccountServiceUnavailableException(
                    "Account Service request failed; please retry later",
                    ex
            );
        }
    }

    public Map<?, ?> getBalance(String accountId) {

        try {

            return circuitBreaker.executeSupplier(() ->
                    client.get()
                            .uri(
                                    "/accounts/{accountId}/balance",
                                    accountId
                            )
                            .header("X-Trace-Id", getTraceId())
                            .retrieve()
                            .body(Map.class)
            );

        } catch (CallNotPermittedException |
                 RestClientException ex) {

            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable; balance cannot be retrieved",
                    ex
            );

        } catch (RuntimeException ex) {

            throw new AccountServiceUnavailableException(
                    "Account Service balance request failed",
                    ex
            );
        }
    }
}
