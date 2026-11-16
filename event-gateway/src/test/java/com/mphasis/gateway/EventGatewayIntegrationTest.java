
package com.mphasis.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.*;

import java.io.File;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventGatewayIntegrationTest {

    private static Process accountProcess;
    private static String accountServiceUrl;

    @Autowired
    private TestRestTemplate restTemplate;

    // Start Account Service before Gateway creates its RestClient bean.
    @DynamicPropertySource
    static void configureAccountService(DynamicPropertyRegistry registry) {
        registry.add(
                "account-service.base-url",
                EventGatewayIntegrationTest::startAccountService
        );
    }

    private static synchronized String startAccountService() {

        if (accountProcess != null && accountProcess.isAlive()) {
            return accountServiceUrl;
        }

        try {
            // Select an available port.
            int port;

            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }

            // Locate the Account Service executable JAR.
            Path jarPath = Path.of(
                    "..",
                    "account-service",
                    "target",
                    "account-service-1.0.0.jar"
            ).toAbsolutePath().normalize();

            if (!Files.exists(jarPath)) {
                throw new IllegalStateException(
                        "Account Service JAR not found: " + jarPath
                                + ". Run Maven clean verify from the project root."
                );
            }

            // Locate Java executable.
            String javaExecutable = Path.of(
                    System.getProperty("java.home"),
                    "bin",
                    System.getProperty("os.name").toLowerCase().contains("win")
                            ? "java.exe"
                            : "java"
            ).toString();

            ProcessBuilder builder = new ProcessBuilder(
                    javaExecutable,
                    "-jar",
                    jarPath.toString(),
                    "--server.port=" + port,
                    "--spring.datasource.url=jdbc:h2:mem:integration-account;DB_CLOSE_DELAY=-1",
                    "--spring.jpa.hibernate.ddl-auto=create-drop"
            );

            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            accountProcess = builder.start();

            accountServiceUrl = "http://localhost:" + port;

            // Wait until Account Service is healthy.
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();

            URI healthUri = URI.create(
                    accountServiceUrl + "/actuator/health"
            );

            long deadline = System.currentTimeMillis() + 60000;

            while (System.currentTimeMillis() < deadline) {

                if (!accountProcess.isAlive()) {
                    throw new IllegalStateException(
                            "Account Service process stopped unexpectedly."
                    );
                }

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(healthUri)
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();

                    HttpResponse<String> response = client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

                    if (response.statusCode() == 200
                            && response.body().contains("\"UP\"")) {
                        return accountServiceUrl;
                    }

                } catch (Exception ignored) {
                    // Service may still be starting.
                }

                Thread.sleep(500);
            }

            throw new IllegalStateException(
                    "Account Service did not become healthy within 60 seconds."
            );

        } catch (Exception ex) {
            stopAccountService();
            throw new IllegalStateException(
                    "Unable to start Account Service for integration test.",
                    ex
            );
        }
    }

    @AfterAll
    static void stopAccountService() {

        if (accountProcess != null && accountProcess.isAlive()) {
            accountProcess.destroy();

            try {
                if (!accountProcess.waitFor(5,
                        java.util.concurrent.TimeUnit.SECONDS)) {
                    accountProcess.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                accountProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void gatewayShouldSubmitEventAndUpdateAccountBalance() {

        String eventId = "integration-event-001";
        String accountId = "integration-account-001";

        String request = """
                {
                  "eventId": "integration-event-001",
                  "accountId": "integration-account-001",
                  "type": "CREDIT",
                  "amount": 750.00,
                  "currency": "INR",
                  "eventTimestamp": "2026-11-16T10:00:00Z",
                  "metadata": {
                    "source": "integration-test"
                  }
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(request, headers);

        // 1. Submit event through Gateway.
        ResponseEntity<Map> submitResponse = restTemplate.exchange(
                "/events",
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertEquals(
                HttpStatus.CREATED,
                submitResponse.getStatusCode()
        );

        assertNotNull(submitResponse.getBody());

        assertEquals(
                eventId,
                submitResponse.getBody().get("eventId")
        );

        assertEquals(
                "PROCESSED",
                submitResponse.getBody().get("status")
        );

        // 2. Verify balance through Gateway.
        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/accounts/" + accountId + "/balance",
                Map.class
        );

        assertEquals(HttpStatus.OK, balanceResponse.getStatusCode());
        assertNotNull(balanceResponse.getBody());

        Number balance = (Number) balanceResponse.getBody().get("balance");

        assertEquals(
                0,
                new BigDecimal(balance.toString())
                        .compareTo(new BigDecimal("750.00"))
        );

        // 3. Submit the same event again.
        ResponseEntity<Map> duplicateResponse = restTemplate.exchange(
                "/events",
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertEquals(
                HttpStatus.OK,
                duplicateResponse.getStatusCode()
        );

        assertTrue(
                Boolean.TRUE.equals(
                        duplicateResponse.getBody().get("duplicate")
                )
        );

        // 4. Verify duplicate did not change balance.
        ResponseEntity<Map> finalBalanceResponse = restTemplate.getForEntity(
                "/accounts/" + accountId + "/balance",
                Map.class
        );

        assertEquals(
                HttpStatus.OK,
                finalBalanceResponse.getStatusCode()
        );

        Number finalBalance =
                (Number) finalBalanceResponse.getBody().get("balance");

        assertEquals(
                0,
                new BigDecimal(finalBalance.toString())
                        .compareTo(new BigDecimal("750.00"))
        );
    }
}
