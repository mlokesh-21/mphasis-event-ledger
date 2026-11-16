# Mphasis Event Ledger

A Java 17 and Spring Boot microservices application for processing financial transaction events, with idempotency, out-of-order event handling, distributed trace propagation, observability, and resiliency.

## 1. Architecture

The application consists of two independently runnable microservices.

### Event Gateway — Port 8080

- Public-facing REST API for transaction event submission.
- Validates incoming event requests.
- Persists event records in its own H2 database.
- Handles duplicate event submissions using `eventId`.
- Communicates synchronously with Account Service through REST APIs.
- Implements Resilience4j Circuit Breaker.
- Generates and propagates `X-Trace-Id`.
- Exposes health checks and application metrics.

### Account Service — Port 8081

- Internal service responsible for account transactions.
- Persists transactions in its own H2 database.
- Prevents duplicate transaction application using `eventId`.
- Calculates account balances.
- Provides account details and recent transactions.
- Exposes health checks.

### Architecture characteristics

- Each service runs as an independent process.
- Each service owns a separate H2 in-memory database.
- Services communicate through synchronous REST APIs.
- Net balance = total CREDIT − total DEBIT.
- Events are listed in chronological order using `eventTimestamp`.
- Duplicate transactions are prevented at both service layers.
- Gateway retains pending events when Account Service is unavailable.

## 2. Architecture Flow

```text
                Client / External System
                         |
                         |
                         v
              +----------------------+
              |     Event Gateway    |
              |      Port 8080       |
              |                      |
              |  POST /events        |
              |  GET  /events/{id}   |
              |  GET  /events        |
              |                      |
              |  Event Database     |
              |  Circuit Breaker    |
              |  Trace ID           |
              +----------------------+
                         |
                         |
                   HTTP / REST
                  X-Trace-Id
                         |
                         v
              +----------------------+
              |    Account Service   |
              |      Port 8081       |
              |                      |
              |  POST transactions  |
              |  GET balance        |
              |  GET account        |
              |                      |
              |  Account Database   |
              +----------------------+
```

## 3. Technology Stack

| Technology | Usage |
|---|---|
| Java 17 | Programming language |
| Spring Boot 3.5.0 | Application framework |
| Spring Web | REST APIs |
| Spring Data JPA | Database persistence |
| H2 Database | In-memory databases |
| Maven | Build management |
| Resilience4j | Circuit breaker |
| Micrometer | Metrics |
| Spring Boot Actuator | Health and monitoring |
| Logstash Logback Encoder | Structured JSON logging |
| JUnit 5 | Automated testing |
| Mockito | Unit testing and mocking |
| TestRestTemplate | HTTP integration testing |
| ProcessBuilder | Launching Account Service for integration testing |
| Docker | Containerization |
| Docker Compose | Multi-container orchestration |

## 4. Project Structure

```text
mphasis-event-ledger/
│
├── account-service/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   │       └── java/
│   ├── Dockerfile
│   └── pom.xml
│
├── event-gateway/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   │       └── java/
│   ├── Dockerfile
│   └── pom.xml
│
├── docker-compose.yml
├── pom.xml
└── README.md
```

## 5. Prerequisites

- JDK 17
- Maven 3.6.3 or later
- Docker Desktop (optional)
- Postman or curl

Verify Java and Maven:

```bash
java -version
mvn -version
```

## 6. Build and Test

Run from the project root.

### Build and execute all tests

```bash
mvn clean verify
```

This command builds both microservices, packages the executable JARs, and executes the complete automated test suite.

On Windows, if Maven is not configured in PATH:

```cmd
C:\apache-maven\bin\mvn.cmd clean verify
```

### Run tests only

```bash
mvn clean test
```

### Run Account Service tests

```bash
mvn -pl account-service test
```

### Run Event Gateway tests

```bash
mvn -pl event-gateway test
```

### Automated Test Coverage

The current project contains 31 automated tests.

| Test Class | Module | Tests |
|---|---|---:|
| AccountServiceTest | Account Service | 6 |
| AccountClientTest | Event Gateway | 4 |
| EventControllerTest | Event Gateway | 9 |
| EventServiceTest | Event Gateway | 11 |
| EventGatewayIntegrationTest | Event Gateway | 1 |
| **Total** | | **31** |

### Integration Testing

The project includes a real HTTP integration test between Event Gateway and Account Service.

`EventGatewayIntegrationTest` launches the Account Service executable JAR as a separate Java process using `ProcessBuilder`.

The integration test verifies:

1. Account Service starts on an available port.
2. Gateway connects to Account Service through HTTP.
3. A transaction event is submitted through Gateway.
4. Account Service processes and persists the transaction.
5. Gateway retrieves the updated account balance.
6. Submitting the same event again does not apply the transaction twice.

The Account Service executable JAR must be built before the integration test runs.

Therefore, execute the complete build from the project root:

```bash
mvn clean verify
```

Test reports are generated under:

```text
account-service/target/surefire-reports/
event-gateway/target/surefire-reports/
```

## 7. Run Locally

Both microservices can run independently.

### Start Account Service

Open Terminal 1:

```bash
mvn -pl account-service spring-boot:run
```

### Start Event Gateway

Open Terminal 2:

```bash
mvn -pl event-gateway spring-boot:run
```

### Service URLs

| Service | URL |
|---|---|
| Event Gateway | http://localhost:8080 |
| Account Service | http://localhost:8081 |

## 8. Run Using Docker Compose

Docker Compose starts both microservices and configures their internal communication.

### Build and start both services

```bash
docker compose up --build -d
```

### Check running containers

```bash
docker compose ps
```

### View application logs

```bash
docker compose logs -f
```

View individual service logs:

```bash
docker compose logs -f account-service
docker compose logs -f event-gateway
```

### Stop services

```bash
docker compose down
```

### Docker Compose Configuration

The configuration uses:

- Account Service exposed on port 8081.
- Event Gateway exposed on port 8080.
- Separate H2 databases.
- Internal Docker networking.
- Account Service health check.
- Gateway startup dependency on Account Service health.
- `ACCOUNT_SERVICE_URL=http://account-service:8081` for internal communication.

The health-check commands use `curl`; ensure the Docker images include curl.

## 9. API Endpoints

### Event Gateway

| Method | Endpoint | Description |
|---|---|---|
| POST | `/events` | Submit a transaction event |
| GET | `/events/{id}` | Retrieve an event by ID |
| GET | `/events?account={accountId}` | List account events |
| GET | `/accounts/{accountId}/balance` | Retrieve account balance |
| GET | `/health` | Gateway health |

### Account Service

| Method | Endpoint | Description |
|---|---|---|
| POST | `/accounts/{accountId}/transactions` | Apply a transaction |
| GET | `/accounts/{accountId}/balance` | Retrieve account balance |
| GET | `/accounts/{accountId}` | Retrieve account details |
| GET | `/health` | Account Service health |

## 10. Event Request Validation

The Event Gateway validates incoming requests.

| Field | Validation |
|---|---|
| eventId | Required |
| accountId | Required |
| type | CREDIT or DEBIT |
| amount | Required and greater than zero |
| currency | Required |
| eventTimestamp | Required |
| metadata | Optional |

Invalid requests return HTTP 400 Bad Request.

## 11. Example Event Request

### Submit a CREDIT transaction

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

### Submit a DEBIT transaction

```bash
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-002",
    "accountId": "acct-123",
    "type": "DEBIT",
    "amount": 50.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:05:11Z"
  }'
```

### List account events

```bash
curl "http://localhost:8080/events?account=acct-123"
```

### Retrieve an event

```bash
curl http://localhost:8080/events/evt-001
```

### Retrieve account balance

```bash
curl http://localhost:8080/accounts/acct-123/balance
```

Expected net balance after the example CREDIT and DEBIT transactions:

```json
{
  "accountId": "acct-123",
  "balance": 100.00
}
```

### Check service health

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
```

## 12. Idempotency and Event Processing

The application supports duplicate event handling at both service layers.

### Gateway Processing

1. Gateway receives an event.
2. It checks whether the `eventId` already exists.
3. A new event is persisted with `PENDING` status.
4. Gateway calls Account Service.
5. After successful processing, the event status changes to `PROCESSED`.
6. If the event was already processed, the existing result is returned without applying another transaction.

### Account Service Processing

Account Service independently checks `eventId` uniqueness.

This prevents duplicate transaction application when a network timeout occurs after Account Service has committed a transaction but before Gateway receives the response.

### Event Ordering

Events are retrieved in ascending order using `eventTimestamp`, rather than their arrival time.

Account balance is calculated using all persisted credits and debits, independent of event arrival order.

## 13. Resiliency and Graceful Degradation

Gateway uses Resilience4j Circuit Breaker to protect synchronous calls to Account Service.

Configured characteristics include:

- Count-based sliding window.
- Sliding window size of 4 calls.
- Minimum number of calls: 4.
- Failure rate threshold: 50%.
- Open-state wait duration: 15 seconds.
- Two permitted calls in half-open state.
- Automatic transition from open to half-open.

### Graceful Degradation

When Account Service is unavailable:

- New event submissions return HTTP 503.
- The event remains in `PENDING` status.
- Previously stored events can still be retrieved.
- Event listing remains available.
- Balance retrieval returns a clear service-unavailable response.
- A later submission of a pending event can retry processing.

## 14. Observability and Monitoring

### Health Checks

Gateway:

```text
http://localhost:8080/health
```

Account Service:

```text
http://localhost:8081/health
```

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

### Distributed Trace Propagation

Send a trace ID with an incoming request:

```text
X-Trace-Id: demo-trace-001
```

Gateway propagates the trace ID to Account Service using the HTTP header.

Both services include trace-related information in their logs.

### Structured Logging

The application uses structured JSON logging with fields such as:

- Timestamp
- Log level
- Logger name
- Message
- Thread name
- Service name
- Trace information when available

## 15. H2 Database Consoles

### Gateway Database

URL:

```text
http://localhost:8080/h2-console
```

JDBC URL:

```text
jdbc:h2:mem:gatewaydb
```

### Account Service Database

URL:

```text
http://localhost:8081/h2-console
```

JDBC URL:

```text
jdbc:h2:mem:accountdb
```

Credentials:

```text
Username: sa
Password: blank
```

Each service uses an independent in-memory H2 database. Data is lost when the corresponding service restarts.

## 16. Design Decisions

1. Gateway stores an event with `PENDING` status before calling Account Service.
2. Account Service independently enforces event ID uniqueness.
3. Gateway uses Resilience4j Circuit Breaker to protect synchronous service communication.
4. Pending events can be retried through a subsequent submission using the same event ID.
5. Account balance is calculated using persisted credits and debits.
6. Event listings use the original event timestamp rather than the received timestamp.
7. Each microservice owns its own database.
8. Trace IDs are propagated between services using HTTP headers.
9. Actuator and Micrometer provide health and monitoring information.

## 17. Limitations and Future Improvements

For production-grade financial processing, consider:

- Transactional outbox pattern.
- Automated reconciliation of stuck pending events.
- Authentication and authorization.
- Currency consistency checks.
- Stronger concurrency handling.
- Persistent production database.
- OpenTelemetry with Jaeger or Zipkin.
- Automated contract testing.
- Centralized log aggregation.
- API rate limiting.

## 18. GitHub Repository

Repository:

https://github.com/mlokesh-21/mphasis-event-ledger

The repository contains:

- Event Gateway source code.
- Account Service source code.
- Maven configuration.
- Dockerfiles.
- Docker Compose configuration.
- Automated unit tests.
- HTTP integration test.
- README documentation.

The Git history preserves the incremental development and integration-test changes.
