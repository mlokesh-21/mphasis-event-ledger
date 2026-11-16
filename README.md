# Mphasis Event Ledger

A Java 17 and Spring Boot microservices application for processing financial transaction events, with idempotency, out-of-order event handling, distributed tracing, observability, and resiliency.

## 1. Architecture

The application consists of two independently runnable microservices.

### Event Gateway — Port 8080
- Public-facing REST API for transaction event submission.
- Validates incoming event requests.
- Persists event records in its own H2 database.
- Handles duplicate event submissions.
- Communicates synchronously with Account Service.
- Implements Resilience4j Circuit Breaker.
- Generates and propagates `X-Trace-Id`.

### Account Service — Port 8081
- Internal service responsible for account transactions.
- Persists transactions in its own H2 database.
- Prevents duplicate transaction application using `eventId`.
- Calculates account balances.
- Provides account details and recent transactions.

### Architecture characteristics
- Each service runs as an independent process.
- Each service owns a separate H2 in-memory database.
- Services communicate through synchronous REST APIs.
- Net balance = total CREDIT − total DEBIT.
- Events are listed in chronological order using `eventTimestamp`.

## 2. Technology Stack

- Java 17
- Spring Boot 3.5.0
- Spring Data JPA
- H2 Database
- Maven
- Resilience4j
- Micrometer
- Spring Boot Actuator
- JUnit 5
- Mockito
- Docker and Docker Compose

## 3. Prerequisites

- JDK 17
- Maven 3.6.3 or later
- Docker Desktop (optional)
- Postman or curl

Verify Java and Maven:

```bash
java -version
mvn -version
```

## 4. Build and Test

Run from the project root:

```bash
mvn clean verify
```

Run tests only:

```bash
mvn clean test
```

Run Account Service tests:

```bash
mvn -pl account-service test
```

Run Event Gateway tests:

```bash
mvn -pl event-gateway test
```

The current unit test suite contains 17 tests:
- Account Service: 6 tests
- Event Gateway: 11 tests

Test reports are generated under:

```text
account-service/target/surefire-reports/
event-gateway/target/surefire-reports/
```

## 5. Run Locally

Start Account Service in Terminal 1:

```bash
mvn -pl account-service spring-boot:run
```

Start Event Gateway in Terminal 2:

```bash
mvn -pl event-gateway spring-boot:run
```

Service URLs:

- Event Gateway: http://localhost:8080
- Account Service: http://localhost:8081

## 6. Run Using Docker Compose

Build and start both services:

```bash
docker compose up --build
```

Check running containers:

```bash
docker compose ps
```

Stop services:

```bash
docker compose down
```

## 7. API Endpoints

### Event Gateway

| Method | Endpoint | Description |
|---|---|---|
| POST | `/events` | Submit a transaction event |
| GET | `/events/{id}` | Retrieve an event |
| GET | `/events?account={accountId}` | List account events |
| GET | `/health` | Gateway health |

### Account Service

| Method | Endpoint | Description |
|---|---|---|
| POST | `/accounts/{accountId}/transactions` | Apply a transaction |
| GET | `/accounts/{accountId}/balance` | Retrieve account balance |
| GET | `/accounts/{accountId}` | Retrieve account details |
| GET | `/health` | Account Service health |

## 8. Example Event Request

Submit a transaction:

```bash
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: demo-trace-001" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {
      "source": "mainframe-batch",
      "batchId": "B-9042"
    }
  }'
```

List events:

```bash
curl "http://localhost:8080/events?account=acct-123"
```

Retrieve an event:

```bash
curl http://localhost:8080/events/evt-001
```

Retrieve account balance:

```bash
curl http://localhost:8080/accounts/acct-123/balance
```

Check service health:

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
```

## 9. Observability and Monitoring

### Metrics

Gateway Actuator metrics:

```text
http://localhost:8080/actuator/metrics
```

Custom metric:

```text
events.submitted
```

### Circuit Breaker

Circuit breaker state:

```text
http://localhost:8080/actuator/circuitbreakers
```

Circuit breaker events:

```text
http://localhost:8080/actuator/circuitbreakerevents
```

### Distributed Tracing

Send a trace ID with the request:

```text
X-Trace-Id: demo-trace-001
```

The Gateway propagates the trace ID to Account Service. Both services include the trace ID in their logs.

### Structured Logging

Logs include structured fields such as timestamp, log level, logger, message, trace ID, and service.

## 10. H2 Database Consoles

### Gateway Database

URL:

```text
http://localhost:8080/h2-console
```

JDBC URL:

```text
jdbc:h2:mem:gatewaydb
```

### Account Database

URL:

```text
http://localhost:8081/h2-console
```

JDBC URL:

```text
jdbc:h2:mem:accountdb
```

Username: `sa`

Password: blank

## 11. Design Decisions

1. The Gateway stores an event with `PENDING` status before calling Account Service.
2. If Account Service is unavailable, the Gateway returns an appropriate service-unavailable response and retains the pending event.
3. A later submission using the same event ID can retry processing.
4. Account Service independently enforces event ID uniqueness to prevent duplicate transaction application after ambiguous network failures.
5. Account balance is calculated using all persisted credits and debits, regardless of event arrival order.
6. Event listings use the original event timestamp rather than the received timestamp.
7. Resilience4j Circuit Breaker protects the synchronous Account Service call.
8. Each service uses its own H2 in-memory database. Data is lost when the service restarts.

## 12. Limitations and Future Improvements

For production-grade financial processing, consider:

- Transactional outbox pattern.
- Automated reconciliation of stuck pending events.
- Authentication and authorization.
- Currency consistency checks.
- Stronger concurrency handling.
- Persistent production database.
- OpenTelemetry with Jaeger or Zipkin.
- Automated integration and contract testing.

## 13. GitHub Repository

Repository:

https://github.com/mlokesh-21/mphasis-event-ledger

The repository contains the source code, Maven configuration, Docker Compose configuration, Postman collection, README, and automated unit tests.
