# Mphasis Event Ledger

Two independently runnable Spring Boot microservices for financial transaction events.

## Architecture

- Event Gateway (port 8080): validates incoming events, persists them locally, enforces gateway idempotency, and calls Account Service.
- Account Service (port 8081): independently persists applied transactions and computes account balances.
- Each service owns a separate H2 in-memory database.
- `X-Trace-Id` is generated at the Gateway when absent and propagated to Account Service.
- Resilience4j Circuit Breaker and HTTP connection/read timeouts protect the synchronous service call.
- `/actuator/metrics` exposes Micrometer metrics, including `events.submitted`.

## Requirements

- JDK 17
- Maven 3.6.3+
- Docker Desktop (optional)
- Postman or curl

## Build and test

From the repository root:

```bash
mvn clean test
mvn clean package
```

## Run locally

Terminal 1:

```bash
mvn -pl account-service spring-boot:run
```

Terminal 2:

```bash
mvn -pl event-gateway spring-boot:run
```

## Run with Docker Compose

```bash
docker compose up --build
```

Stop:

```bash
docker compose down
```

## API examples

Submit an event:

```bash
curl -i -X POST http://localhost:8080/events   -H "Content-Type: application/json"   -H "X-Trace-Id: demo-trace-001"   -d '{
    "eventId":"evt-001",
    "accountId":"acct-123",
    "type":"CREDIT",
    "amount":150.00,
    "currency":"USD",
    "eventTimestamp":"2026-05-15T14:02:11Z",
    "metadata":{"source":"mainframe-batch","batchId":"B-9042"}
  }'
```

List events in chronological event-time order:

```bash
curl "http://localhost:8080/events?account=acct-123"
```

Get event:

```bash
curl http://localhost:8080/events/evt-001
```

Get balance:

```bash
curl http://localhost:8080/accounts/acct-123/balance
```

Health:

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
```

## H2 consoles

Gateway: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:mem:gatewaydb`

Account: http://localhost:8081/h2-console  
JDBC URL: `jdbc:h2:mem:accountdb`

Username: `sa`; password blank.

## Design decisions and limitations

1. The Gateway records an event as `PENDING` before calling Account Service. If the call fails, the Gateway returns 503 and keeps the pending event available to GET endpoints. A later POST with the same event ID retries processing.
2. Account Service independently enforces uniqueness on `eventId`. This prevents a retry after an ambiguous network timeout from applying the same transaction twice.
3. The balance is calculated from all persisted credits and debits, so event arrival order does not affect the result.
4. Event listing is sorted by `eventTimestamp`, not receive time.
5. Circuit breaker uses a count-based sliding window and opens after the configured failure threshold.
6. H2 is in-memory: data is lost when a service process/container restarts. This is appropriate for the assessment's embedded/in-memory database constraint, not production financial storage.
7. For production-grade delivery, consider transactional outbox, reconciliation for stuck PENDING events, authentication, authorization, currency consistency checks, and stronger concurrency handling.

## Commit history

Use incremental commits that reflect your development work. Do not squash the entire project into one commit.
