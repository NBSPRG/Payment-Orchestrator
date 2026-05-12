# Payment Orchestration

Modular monolith implementation of the payment orchestration plan in `plan.md`.

## Architecture Note

This project is intentionally built as a modular monolith:

- one Spring Boot deployable
- one primary PostgreSQL database
- package-level modules for API, orchestration, routing, provider adapters, persistence, security, idempotency, messaging, and real-time delivery
- in-process Kotlin interfaces between modules

That means internal modules should not call each other through HTTP or gRPC. gRPC only becomes useful when a boundary is actually remote, for example when a real provider adapter or a future extracted service runs outside this application. Until then, in-process interfaces are simpler, faster, and easier to keep transactional.

The current database approach is also deliberate: one PostgreSQL database, with clear table ownership by module. Multiple databases should be introduced only after splitting into independently deployed services, because separate databases would force distributed consistency before the system needs it.

## What Is Implemented

- Spring Boot 3 + Kotlin project skeleton
- Docker Compose for PostgreSQL, Redis, Kafka, and Jaeger
- Flyway schema for merchants, API keys, payments, attempts, status history, outbox, providers, and routing rules
- Merchant API key authentication with Spring Security
- Explicit payment state machine
- Provider routing and simulated provider connectors
- Idempotency result store
- Payment create/get REST API
- Async provider processing after payment creation
- Outbox row generation and scheduled Kafka publisher with PostgreSQL row claiming
- Webhook delivery with HTTP timeouts, retry classification, and HMAC signatures
- API key rotation endpoint
- WebSocket/STOMP status updates for clients
- Redis pub/sub status push as a best-effort cluster fan-out side effect
- Focused unit tests for state transitions, request validation, and routing

## Still Future Work

- Testcontainers integration tests for PostgreSQL, Redis, and Kafka
- Production-grade WebSocket authorization for merchant-specific subscriptions
- Bulkheads around real provider calls
- Durable background command queue for provider processing
- More concurrency/idempotency integration tests
- Optional IP allowlisting for merchant API keys

## Local Run

Prerequisites:

- Java 21
- Docker Desktop

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

Creating a payment returns the initial `INITIATED` state quickly. Provider processing happens asynchronously in the background; use the status endpoint, list endpoint, WebSocket topic, or webhook events to observe the final state.

## Troubleshooting

Check Java:

```powershell
java -version
```

Expected version is Java 21.

Check Docker services:

```powershell
docker compose ps
```

If full tests fail with Testcontainers errors, start Docker Desktop and rerun:

```powershell
.\gradlew.bat test
```

## Run Tests

Fast non-Docker checks:

```powershell
.\gradlew.bat compileKotlin compileTestKotlin
```

Fast unit/smoke suite with JaCoCo coverage:

```powershell
.\gradlew.bat test jacocoTestCoverageVerification
```

Docker-backed integration suite:

```powershell
.\gradlew.bat integrationTest
```

The coverage gate is set to 75% for the fast suite. Integration tests require Docker Desktop because they use Testcontainers for PostgreSQL, Redis, and Kafka.

## CI/CD Checks

GitHub Actions runs on every push and pull request:

- Compile Kotlin main and test sources
- Run Detekt static analysis
- Run fast unit/smoke tests
- Enforce JaCoCo 75% coverage
- Verify Docker is available
- Validate `docker-compose.yml`
- Start Docker Compose services
- Run Docker/Testcontainers-backed integration tests

Local equivalent:

```powershell
.\gradlew.bat compileKotlin compileTestKotlin detekt test jacocoTestCoverageVerification
.\gradlew.bat integrationTest
```

## API Notes

- `X-Idempotency-Key` must be 8 to 128 characters and may contain letters, numbers, `.`, `_`, `:`, and `-`.
- Payment create returns `INITIATED`; provider result is applied asynchronously.
- Webhook URLs must be HTTPS.
- Card expiry must not be in the past.
- Metadata is limited to 50 top-level entries.
- Request bodies are limited to 1 MiB by default.
- Supported currencies are `INR`, `USD`, `EUR`, and `GBP`.

## More Docs

- [Architecture decisions](docs/05-architecture-decisions.md)
- [Local smoke tests](docs/06-local-smoke-tests.md)
- [Postman collection](docs/payment-orchestration.postman_collection.json)

## Demo Credentials

Seeded merchant:

```text
merchantId: merchant_demo
apiKey: test_api_key_123
```

## Create Payment

```powershell
curl -X POST http://localhost:8080/api/v1/payments `
  -H "Content-Type: application/json" `
  -H "X-API-Key: test_api_key_123" `
  -H "X-Idempotency-Key: 11111111-1111-1111-1111-111111111111" `
  -d '{
    "amount": { "value": 10000, "currency": "INR" },
    "paymentMethod": {
      "type": "CARD",
      "card": {
        "number": "4111111111111111",
        "expiryMonth": "12",
        "expiryYear": "2027",
        "cvv": "123",
        "holderName": "Demo User"
      }
    },
    "merchantReference": "ORDER-12345",
    "description": "Order payment",
    "metadata": { "orderId": "12345" }
  }'
```

## Payment Status WebSocket

Connect a STOMP client to:

```text
ws://localhost:8080/ws/payments
```

Subscribe to the payment-specific topic:

```text
/topic/payments/{merchantId}/{paymentId}
```
