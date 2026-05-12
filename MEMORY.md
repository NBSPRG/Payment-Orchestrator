# Project Memory

## Current Direction

This project is a Kotlin/Spring Boot payment orchestration system being built as a modular monolith.

Key architecture decisions:

- Use one Spring Boot deployable.
- Use one primary PostgreSQL database for now.
- Keep modules separated by package and table ownership.
- Use in-process Kotlin interfaces between modules.
- Do not use gRPC between internal modules while this remains a monolith.
- Add gRPC only later if a boundary becomes remote, such as a real external provider adapter or an extracted microservice.
- Kafka/outbox is used for asynchronous side effects and future service extraction.
- Redis is used for status fan-out/idempotency-related infrastructure, not as the primary source of truth.

## Current Implemented Areas

- Spring Boot 3 + Kotlin project.
- PostgreSQL, Redis, Kafka, and Jaeger Docker Compose setup.
- Flyway migrations for:
  - merchants
  - merchant API keys
  - payments
  - payment attempts
  - payment status history
  - outbox events
  - providers
  - routing rules
- Merchant API key authentication.
- Payment create/get REST API.
- Payment state machine.
- Routing engine backed by DB routing rules.
- Simulated provider connectors through `ProviderConnector`.
- Retry/failover coordinator skeleton.
- Idempotency result persistence.
- Outbox row creation and scheduled Kafka publisher.
- Redis best-effort status publishing.
- WebSocket/STOMP status publishing.
- Focused unit tests for payment status, payment method validation, and routing.

## Recent Changes

Added or improved:

- `README.md` architecture note explaining modular monolith, single DB, and no internal gRPC for now.
- `PaymentRepository.findByMerchantIdAndIdempotencyKey`.
- `OutboxEventRepository.findTop100ByPublishedFalseOrderByIdAsc`.
- Idempotency race handling in `IdempotencyService`.
- Duplicate payment creation recovery in `PaymentOrchestrationService`.
- Outbox polling no longer scans every row.
- WebSocket/STOMP config:
  - endpoint: `/ws/payments`
  - topic: `/topic/payments/{merchantId}/{paymentId}`
- WebSocket status publisher using `SimpMessagingTemplate`.
- API key filter skips `/ws/payments`.
- Stronger request validation for:
  - currency format
  - card number
  - expiry month/year
  - CVV
  - UPI VPA
  - card/UPI payload matching selected payment method type
- Generic `IllegalArgumentException` API handler.
- Tests:
  - `PaymentMethodRequestValidationTest`
  - `RoutingEngineTest`

## Important Files

- `plan.md`: original long-form architecture plan.
- `README.md`: current practical project recap and local run instructions.
- `src/main/kotlin/com/yuno/payment/orchestration/PaymentOrchestrationService.kt`: main payment flow.
- `src/main/kotlin/com/yuno/payment/orchestration/PaymentStateMachine.kt`: state transitions.
- `src/main/kotlin/com/yuno/payment/orchestration/RetryCoordinator.kt`: provider failover path.
- `src/main/kotlin/com/yuno/payment/routing/RoutingEngine.kt`: routing rule selection.
- `src/main/kotlin/com/yuno/payment/provider/ProviderConnector.kt`: provider abstraction.
- `src/main/kotlin/com/yuno/payment/idempotency/IdempotencyService.kt`: idempotency cache/result handling.
- `src/main/kotlin/com/yuno/payment/messaging/outbox/OutboxProcessor.kt`: scheduled Kafka outbox publisher.
- `src/main/kotlin/com/yuno/payment/config/WebSocketConfig.kt`: STOMP/WebSocket setup.
- `src/main/kotlin/com/yuno/payment/realtime/WebSocketPaymentStatusPublisher.kt`: WebSocket status publishing.
- `src/main/resources/db/migration`: database schema.

## Current Environment Blocker

Local verification has not been run successfully because this machine currently does not have Java or Gradle available on PATH.

Observed failures:

- `java -version` failed.
- `gradle test` failed.

User plans to add required dependencies later.

Once dependencies are installed, run:

```powershell
gradle test
```

Then, for local app run:

```powershell
docker compose up -d
gradle bootRun
```

## Remaining Work

High-value next steps:

- Add Gradle Wrapper after Gradle is installed:

```powershell
gradle wrapper
```

- Run and fix compile/test issues after Java 21 and Gradle are available.
- Add Testcontainers integration tests for PostgreSQL, Kafka, and Redis.
- Add controller tests using MockMvc.
- Add idempotency integration tests, especially concurrent same-key requests.
- Add outbox tests for Kafka publish success/failure behavior.
- Add real Resilience4j configuration around provider calls:
  - timeout
  - retry
  - circuit breaker
  - bulkhead
- Improve outbox for production:
  - multi-instance claiming
  - retry count
  - dead-letter handling
  - avoid duplicate publication across app instances
- Add reconciliation job for payments stuck in non-terminal states.
- Add production-grade WebSocket authorization for merchant-specific subscriptions.
- Consider stricter module boundaries later, possibly with package rules or ArchUnit.

## Architectural Reminder

For this project phase:

- Single DB is correct.
- Multiple DBs are premature.
- Internal gRPC is unnecessary.
- Keep module boundaries clean in code first.
- Extract services only when there is a real operational reason.
