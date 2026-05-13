# Payment Orchestration System - Current Architecture

This document describes the implemented system. It replaces the earlier pre-implementation plan and avoids documenting technologies or boundaries that are not used in the codebase.

## Objective

Build a simplified payment orchestration system with:

- REST APIs for payment creation and retrieval
- merchant API key authentication
- idempotency protection
- provider routing
- retry and failover behavior
- durable persistence
- status tracking
- unit and integration test coverage
- operational visibility through logs, metrics, health checks, and tracing configuration

## Architecture Style

The application is a modular monolith.

```text
Client
  |
  v
Spring MVC Controller
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
  +--> PostgreSQL
  +--> Redis
  +--> Kafka
```

There are no internal remote calls between modules. API, orchestration, routing, providers, persistence, security, messaging, ledger, webhook, notification, and real-time delivery are package-level modules inside one Spring Boot deployable.

## Runtime Components

| Component | Implementation |
|---|---|
| Language | Kotlin |
| Runtime | Java 21 |
| Framework | Spring Boot 3.3 |
| API | Spring MVC REST |
| Persistence | PostgreSQL, Spring Data JPA, Flyway |
| Idempotency | PostgreSQL-backed request hash and cached response |
| Locking | Redis payment lock |
| Rate limiting | Redis fixed-window per merchant |
| Events | Transactional outbox table plus scheduled Kafka publisher |
| Messaging | Spring Kafka |
| Provider reliability | Resilience4j retry and circuit breaker registries |
| Metrics | Micrometer, Prometheus registry |
| Tracing | OpenTelemetry exporter configuration |
| Docs | README, OpenAPI/Swagger, Postman collection |
| Tests | JUnit 5, Mockito-Kotlin, Spring MockMvc, Testcontainers |

## Main Payment Flow

```text
POST /api/v1/payments
  |
  v
ApiKeyAuthenticationFilter
  |
  v
PaymentController validates request and idempotency key
  |
  v
PaymentOrchestrationService
  |
  +--> checks cached idempotency response
  +--> creates payment in INITIATED state
  +--> writes status history and outbox event
  +--> stores idempotency response
  |
  v
HTTP 201 response
```

Provider processing is asynchronous:

```text
PaymentProcessingJob
  |
  v
find INITIATED payments
  |
  v
RedisPaymentLock
  |
  v
transition INITIATED -> PROCESSING
  |
  v
RoutingEngine resolves provider chain
  |
  v
RetryCoordinator calls provider connector
  |
  v
persist CAPTURED or FAILED
  |
  v
publish application event and outbox row
```

## Routing

Routing rules are stored in the database and sorted by priority.

Seeded local/demo behavior:

| Payment method | Primary provider | Failover provider |
|---|---|---|
| CARD | PROVIDER_A | PROVIDER_B |
| UPI | PROVIDER_B | PROVIDER_A |

The routing engine can also filter rules by currency and amount range.

## Provider Connectors

Providers implement a local Kotlin interface:

```text
ProviderConnector
  - charge(payment)
  - refund(payment)
  - getStatus(providerTransactionId)
```

Implemented connectors:

- `ProviderAConnector`
- `ProviderBConnector`

Both connectors are simulated adapters. They model the shape of external PSP integration without requiring real payment provider credentials.

## Retry And Failover

`RetryCoordinator` receives an ordered provider chain from `RoutingEngine`.

Behavior:

1. Call primary provider through Resilience4j retry and circuit breaker wrappers.
2. Persist each provider attempt.
3. If the provider returns a non-retryable failure, stop.
4. If the provider returns a retryable failure, try the next provider.
5. If all providers fail, mark the payment failed with an unavailable provider result.

Retryable errors are classified by `ProviderErrorClassifier`.

## Idempotency

Create payment requires:

```text
X-Idempotency-Key
```

Behavior:

| Case | Result |
|---|---|
| New key and body | Create payment, return `201` |
| Same key and same body | Return cached response, return `200` |
| Same key and different body | Return `409 idempotency_conflict` |

The durable idempotency record stores:

- merchant id
- idempotency key
- request hash
- response status
- response body
- linked payment id
- expiry timestamp

## Status Model

Implemented statuses:

- `INITIATED`
- `PROCESSING`
- `AUTHORIZED`
- `CAPTURED`
- `FAILED`
- `CANCELLED`
- `REFUNDED`

Assignment mapping:

| Assignment status | Implemented status |
|---|---|
| CREATED | INITIATED |
| PROCESSING | PROCESSING |
| SUCCESS | CAPTURED |
| FAILED | FAILED |
| RETRYING | tracked as provider attempts and retryable failover |

## Persistence

Important tables:

- `merchants`
- `merchant_api_keys`
- `payments`
- `payment_attempts`
- `payment_status_history`
- `outbox_events`
- `idempotency_keys`
- `providers`
- `routing_rules`
- `ledger_entries`
- `webhook_deliveries`

Flyway owns schema creation and local demo seed data.

## Messaging And Side Effects

Payment status changes write outbox rows in the same transaction as the domain update.

`OutboxProcessor`:

- claims rows with PostgreSQL row locking
- publishes to Kafka
- records publish timestamps
- tracks retry count and last error

Kafka consumers:

- `LedgerConsumer`
- `NotificationConsumer`
- `WebhookConsumer`

Side effects are intentionally asynchronous and eventually consistent.

## Ledger

Ledger entries are created from terminal Kafka events.

Capture:

- debit merchant receivable
- credit payment clearing account

Refund:

- reverse capture entries

Ledger processing is idempotent by event id and entry type.

## Webhooks

When a payment has an HTTPS `webhookUrl`, terminal events create webhook deliveries.

Webhook behavior:

- JSON payload delivery
- HMAC-SHA256 signature
- timestamp header
- event type header
- HTTP connect/read timeouts
- retryable status classification
- scheduled retry

Headers:

```text
X-Yuno-Signature
X-Yuno-Timestamp
X-Yuno-Event-Type
```

## WebSocket And Realtime

Clients can connect to:

```text
ws://localhost:8080/ws/payments
```

Topic:

```text
/topic/payments/{merchantId}/{paymentId}
```

Payment status changes publish application events that fan out through WebSocket and Redis pub/sub.

## Security

Implemented:

- merchant API key authentication
- API key hash storage
- optional merchant id header consistency check
- API key rotation endpoint
- Redis-backed rate limiting
- request body size guard
- validation for idempotency keys and webhook URLs

Local/demo seed:

```text
merchantId: merchant_demo
apiKey: test_api_key_123
```

This seed is for development and demo use only.

## Observability

Implemented:

- `/actuator/health`
- Micrometer counters/timers for payments, providers, webhooks, and idempotency
- Prometheus registry dependency
- structured JSON logs
- OpenTelemetry tracing bridge and OTLP exporter configuration
- Jaeger in Docker Compose for local tracing backend

## Tests

Fast verification:

```powershell
.\gradlew.bat compileKotlin compileTestKotlin detekt test jacocoTestCoverageVerification
```

Integration tests:

```powershell
.\gradlew.bat integrationTest
```

Integration tests use Testcontainers and need a Docker daemon.

Current integration coverage:

- application context and health
- create payment
- async capture with Provider A for card payment
- idempotent replay
- idempotency conflict
- missing API key
- payment not found
- list payments
- invalid idempotency key
- unsupported currency
- non-HTTPS webhook URL
- API key rotation

Focused unit coverage:

- request validation
- state transitions
- routing
- retry and failover coordinator
- provider registry
- webhook delivery and retry scheduling
- webhook consumer
- ledger service
- notification consumer
- API exception handling
- security filters
- rate limiting
- real-time publisher

## Local Demo Checklist

1. Start dependencies:

```powershell
docker compose up -d
```

2. Run app:

```powershell
.\gradlew.bat bootRun
```

3. Check health:

```powershell
curl.exe http://localhost:8080/actuator/health
```

4. Create payment:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/payments `
  -H "Content-Type: application/json" `
  -H "X-API-Key: test_api_key_123" `
  -H "X-Idempotency-Key: demo-flow-001" `
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
    "merchantReference": "ORDER-12345"
  }'
```

5. Poll status until `CAPTURED`:

```powershell
curl.exe -H "X-API-Key: test_api_key_123" `
  http://localhost:8080/api/v1/payments/{paymentId}/status
```

## Known Demo Limits

- Provider connectors are simulated.
- Webhook delivery needs a reachable HTTPS receiver to demonstrate end to end.
- WebSocket status updates need a STOMP client to demonstrate live.
- Testcontainers integration tests must run where Docker daemon access is available.
- Demo seed credentials should not be used outside local/demo environments.

## Future Improvements

- production-grade merchant onboarding instead of seed data
- stricter WebSocket subscription authorization
- provider-specific real HTTP adapters
- optional IP allowlisting for merchant API keys
- Prometheus endpoint exposure profile for operations environments
- deeper integration tests for webhook delivery with a local mock HTTPS receiver
