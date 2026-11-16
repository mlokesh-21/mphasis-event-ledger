
package com.mphasis.gateway.client;

import com.mphasis.gateway.dto.AccountTransactionRequest;
import com.mphasis.gateway.exception.AccountServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AccountClientTest {

    private HttpServer server;
    private AccountClient accountClient;
    private CircuitBreaker circuitBreaker;

    private final AtomicReference<String> receivedTraceId =
            new AtomicReference<>();

    private final AtomicReference<String> receivedBody =
            new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {

        server = HttpServer.create(
                new InetSocketAddress("localhost", 0), 0
        );

        server.createContext(
                "/accounts/acct-1/transactions",
                exchange -> {
                    receivedTraceId.set(
                            exchange.getRequestHeaders()
                                    .getFirst("X-Trace-Id")
                    );

                    receivedBody.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8
                            )
                    );

                    byte[] response = "{}".getBytes(StandardCharsets.UTF_8);

                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                }
        );



        server.createContext(
                "/accounts/acct-1/balance",
                exchange -> {
                    byte[] response =
                            "{\"accountId\":\"acct-1\",\"balance\":150.00}"
                                    .getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set(
                            "Content-Type", "application/json"
                    );

                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                }
        );



        server.start();

        circuitBreaker = CircuitBreaker.of(
                "accountServiceTest",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .build()
        );

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();

        accountClient = new AccountClient(restClient, circuitBreaker);
    }

    @AfterEach
    void tearDown() {

        MDC.clear();

        if (server != null) {
            server.stop(0);
        }
    }

    private AccountTransactionRequest createRequest() {
        return new AccountTransactionRequest(
                "evt-1",
                "acct-1",
                "CREDIT",
                new BigDecimal("100"),
                "USD",
                Instant.parse("2026-05-15T10:00:00Z")
        );
    }

    @Test
    void applyTransactionShouldPropagateTraceId() {

        MDC.put("traceId", "trace-test-001");

        accountClient.applyTransaction(createRequest());

        assertEquals("trace-test-001", receivedTraceId.get());

        assertNotNull(receivedBody.get());
        assertTrue(receivedBody.get().contains("evt-1"));
    }

    @Test
    void applyTransactionShouldUseFallbackTraceIdWhenMdcIsEmpty() {

        MDC.clear();

        accountClient.applyTransaction(createRequest());

        assertEquals("trace-unavailable", receivedTraceId.get());
    }

    @Test
    void getBalanceShouldReturnAccountBalance() {

        MDC.put("traceId", "balance-trace-001");

        var result = accountClient.getBalance("acct-1");

        assertNotNull(result);
        assertEquals("acct-1", result.get("accountId"));
        assertEquals(150.00, ((Number) result.get("balance")).doubleValue());
    }

    @Test
    void openCircuitBreakerShouldReturnServiceUnavailableException() {

        circuitBreaker.transitionToOpenState();

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.applyTransaction(createRequest())
        );
    }
}
