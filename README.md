# Payment Orchestration System

Kotlin and Spring Boot implementation of a simplified payment orchestration system. The project is built as a modular monolith: one deployable application, one PostgreSQL database, and package-level boundaries for API, orchestration, routing, providers, persistence, idempotency, messaging, security, observability, and real-time status delivery.

The implementation intentionally uses in-process Kotlin interfaces between modules. Provider connectors are simulated adapters behind a common interface, which keeps the assignment focused on orchestration, reliability, persistence, and testability without adding unnecessary remote-service complexity.

## Architecture

```text
Client
  |
  v
Controller Layer
  |
  v
PaymentOrchestrationService
  |
  +--> IdempotencyService
  +--> PaymentStateMachine
  +--> RoutingEngine
  +--> RetryCoordinator
  +--> ProviderConnectorRegistry
  +--> OutboxPublisher
  |
  v
PostgreSQL / Redis / Kafka
```

### Runtime Flow

1. Merchant calls `POST /api/v1/payments` with `X-API-Key` and `X-Idempotency-Key`.
2. Controller validates the request body, idempotency key, and authenticated merchant.
3. Orchestration service checks the idempotency store.
4. A payment is persisted in `INITIATED` state.
5. The HTTP response returns quickly.
6. A scheduled processor picks up initiated payments.
7. Routing resolves the provider chain from database-backed routing rules.
8. Retry coordinator calls Provider A or Provider B with Resilience4j retry/circuit breaker support.
9. Final status is persisted, status history is stored, and an outbox event is written.
10. Outbox processor publishes events to Kafka.
11. Ledger, notification, webhook, and real-time publishers react to terminal events.

## Implemented Features

- Spring Boot 3.3, Kotlin, Java 21
- REST APIs for create, fetch, list, status, cancel, refund
- API key authentication and rotation
- Request validation for amount, currency, card, UPI, idempotency key, metadata, webhook URL, and request size
- DB-backed idempotency with request-hash conflict detection
- Redis-backed payment lock and rate limiting
- Explicit payment state machine
- Provider routing with primary and failover chains
- Provider A and Provider B simulated connectors
- Retry and failover coordinator with Resilience4j
- PostgreSQL persistence with Flyway migrations
- Payment attempt and status history tables
- Outbox pattern with PostgreSQL row claiming
- Kafka publisher and consumers
- Ledger entries for capture and refund
- Notification logging for capture, failure, and refund
- Webhook delivery with HMAC-SHA256 signature, timeout, retry classification, and scheduled retry
- WebSocket/STOMP status topic publisher
- Redis pub/sub status fan-out support
- Structured JSON logging
- Micrometer metrics and actuator endpoints
- OpenAPI/Swagger configuration
- Unit tests and Testcontainers-backed integration tests

## Status Mapping

The assignment uses generic status names. This implementation uses payment-domain names:

| Assignment | Implementation |
|---|---|
| CREATED | INITIATED |
| PROCESSING | PROCESSING |
| SUCCESS | CAPTURED |
| FAILED | FAILED |
| RETRYING | Retry attempts are stored in `payment_attempts`; retryable provider errors drive failover |

Additional implemented terminal states:

- `CANCELLED`
- `REFUNDED`

## API Summary

Base URL:

```text
http://localhost:8080
```

Endpoints:

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/payments` | Create a payment |
| `GET` | `/api/v1/payments/{id}` | Fetch payment details |
| `GET` | `/api/v1/payments/{id}/status` | Fetch current payment status |
| `GET` | `/api/v1/payments` | List merchant payments |
| `POST` | `/api/v1/payments/{id}/cancel` | Cancel a payment |
| `POST` | `/api/v1/payments/{id}/refund` | Refund a captured payment |
| `POST` | `/api/v1/merchant/api-keys/rotate` | Rotate merchant API key |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui/index.html` | Swagger UI |

Required headers for payment APIs:

```text
X-API-Key: test_api_key_123
X-Idempotency-Key: unique-key-per-create-request
```

`X-Idempotency-Key` must be 8 to 128 characters and may contain letters, numbers, `.`, `_`, `:`, and `-`.

## Demo Credentials

The local/demo seed migration creates one merchant API key:

```text
merchantId: merchant_demo
apiKey: test_api_key_123
```

This credential is for local/demo use only.

## Create Payment Example

```powershell
curl.exe -X POST http://localhost:8080/api/v1/payments `
  -H "Content-Type: application/json" `
  -H "X-API-Key: test_api_key_123" `
  -H "X-Idempotency-Key: demo-11111111" `
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

The initial response returns `INITIATED`. Background processing then moves the payment to `CAPTURED` or `FAILED`.

Check status:

```powershell
curl.exe -H "X-API-Key: test_api_key_123" `
  http://localhost:8080/api/v1/payments/{paymentId}/status
```

## Local Run

Prerequisites:

- Java 21
- Docker Desktop or Docker Engine

Start dependencies:

```powershell
docker compose up -d
```

Run the app:

```powershell
.\gradlew.bat bootRun
```

Health check:

```powershell
curl.exe http://localhost:8080/actuator/health
```

## Running With Containers On A Remote VM

If PostgreSQL, Redis, Kafka, and Jaeger are running on an Azure VM, keep an SSH tunnel open from the local machine:

```powershell
ssh -i "C:\path\to\key.pem" `
  -L 5432:localhost:5432 `
  -L 6379:localhost:6379 `
  -L 9092:localhost:9092 `
  -L 4318:localhost:4318 `
  -L 16686:localhost:16686 `
  user@vm-public-ip
```

Then run the Spring Boot app locally with the default `localhost` configuration.

## Tests

Fast checks:

```powershell
.\gradlew.bat compileKotlin compileTestKotlin detekt test jacocoTestCoverageVerification
```

Integration tests:

```powershell
.\gradlew.bat integrationTest
```

Integration tests use Testcontainers and require access to a Docker daemon. If Docker is only available on an Azure VM, run the integration tests on that VM.

Current integration coverage includes:

- application health
- create payment
- async processing to `CAPTURED`
- Provider A routing for card payments
- idempotent replay
- idempotency conflict
- missing API key
- list payments
- not found response
- invalid idempotency key
- unsupported currency
- non-HTTPS webhook URL
- API key rotation

## Observability

- Health: `/actuator/health`
- Metrics: Micrometer and Prometheus registry
- Tracing: OpenTelemetry exporter configuration
- Logs: structured JSON through Logback
- Local tracing dependency: Jaeger in `docker-compose.yml`

## WebSocket Status Updates

Endpoint:

```text
ws://localhost:8080/ws/payments
```

Subscribe to:

```text
/topic/payments/{merchantId}/{paymentId}
```

## Webhooks

Payment requests may include an HTTPS `webhookUrl`. Terminal payment events trigger a webhook delivery with:

```text
X-Yuno-Signature
X-Yuno-Timestamp
X-Yuno-Event-Type
```

Webhook delivery uses HMAC-SHA256 signing and retry scheduling for retryable failures.

## Submission Notes

- The system is a modular monolith, not a microservice deployment.
- Provider connectors are simulated in-process implementations behind an interface.
- PostgreSQL is the durable idempotency source. Redis is used for locks, rate limiting, and real-time fan-out support.
- Kafka is used for side effects through the outbox pattern.
- The local seeded merchant and API key are intended only for development and demo runs.

## Additional Files

- [plan.md](plan.md): current architecture and implementation plan
- [AI_PROMPTS.md](AI_PROMPTS.md): prompt history and evolution notes
- [Postman collection](docs/payment-orchestration.postman_collection.json): quick API collection
