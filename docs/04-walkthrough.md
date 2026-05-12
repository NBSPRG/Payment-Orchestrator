# Walkthrough: Payment Orchestration System — Complete Build-Out

All 8 phases of the implementation plan have been executed. Below is a summary of everything built.

---

## Files Created/Modified

| Category | New | Modified | Total |
|---|---|---|---|
| Database migrations | 4 | 0 | 4 |
| Domain model | 2 | 0 | 2 |
| Ledger module | 3 | 0 | 3 |
| Notification module | 1 | 0 | 1 |
| Webhook module | 4 | 0 | 4 |
| Kafka consumers | 3 | 0 | 3 |
| Orchestration | 1 new, 3 modified | 3 | 4 |
| Provider layer | 1 new, 3 modified | 3 | 4 |
| Config | 3 new, 3 modified | 3 | 6 |
| Security | 2 new, 2 modified | 2 | 4 |
| Observability | 1 | 0 | 1 |
| Persistence | 0 new, 4 modified | 4 | 4 |
| API layer | 1 new, 3 modified | 3 | 4 |
| Resources | 4 | 1 | 5 |
| Tests | 4 | 0 | 4 |
| **Total** | **~30 new** | **~22 modified** | **~52** |

---

## Phase 1: Foundation

- [build.gradle.kts](file:///d:/Databsaes/build.gradle.kts) — Added resilience4j-circuitbreaker/retry/timelimiter/bulkhead, springdoc-openapi, logstash-logback-encoder, spring-kafka-test, mockito-kotlin, jackson-datatype-jsr310

---

## Phase 2: Seed Data + Endpoints + API Polish

- [V8__seed_demo_merchant.sql](file:///d:/Databsaes/src/main/resources/db/migration/V8__seed_demo_merchant.sql) — Demo merchant with SHA-256 verified API key hash
- [PaymentController.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/PaymentController.kt) — 6 endpoints: `POST /` (201/200), `GET /{id}`, `GET /{id}/status`, `POST /{id}/cancel`, `POST /{id}/refund`, `GET /` (paginated + filtered)
- [PaymentStatusResponse.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/dto/PaymentStatusResponse.kt) — Lightweight status DTO
- [PaymentDtos.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/dto/PaymentDtos.kt) — `ErrorResponse` is now RFC 7807 compliant
- [ApiExceptionHandler.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/ApiExceptionHandler.kt) — Handles all exception types with traceId from OpenTelemetry
- [PaymentEventType.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentEventType.kt) — Added AUTHORIZED, CANCELLED, REFUNDED, STATUS_CHANGED

---

## Phase 3: Ledger + Kafka Consumers + Notification + Webhook

### Ledger Module (double-entry)
- [V9__create_ledger_entries_table.sql](file:///d:/Databsaes/src/main/resources/db/migration/V9__create_ledger_entries_table.sql) — `(event_id, entry_type)` unique constraint for idempotency
- [LedgerEntryEntity.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/ledger/LedgerEntryEntity.kt)
- [LedgerEntryRepository.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/ledger/LedgerEntryRepository.kt)
- [LedgerService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/ledger/LedgerService.kt) — `recordCapture()` creates DEBIT+CREDIT pair; `recordRefund()` creates reversal pair; idempotent via `DataIntegrityViolationException` catch

### Kafka Consumers
- [LedgerConsumer.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/messaging/consumer/LedgerConsumer.kt) — Listens to `payment.v1.captured` + `payment.v1.refunded`
- [NotificationConsumer.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/messaging/consumer/NotificationConsumer.kt) — Listens to captured/failed/refunded events
- [WebhookConsumer.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/messaging/consumer/WebhookConsumer.kt) — Triggers webhook delivery

### Notification Module
- [NotificationService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/notification/NotificationService.kt) — Logs notifications (ready for email/SMS integration)

### Webhook Module
- [V10__create_webhook_deliveries_table.sql](file:///d:/Databsaes/src/main/resources/db/migration/V10__create_webhook_deliveries_table.sql) — With retry scheduling index
- [WebhookDeliveryEntity.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/webhook/WebhookDeliveryEntity.kt)
- [WebhookService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/webhook/WebhookService.kt) — HMAC-SHA256 signing, exponential backoff (1m/5m/30m/2h/24h)
- [WebhookRetryProcessor.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/webhook/WebhookRetryProcessor.kt) — Scheduled retry of failed deliveries

### Kafka Config
- [KafkaConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/KafkaConfig.kt) — Full producer (idempotent + acks=all) and consumer factories with error handling

---

## Phase 4: Refund + Reconciliation + Provider

- [ProviderConnector.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/ProviderConnector.kt) — Added `refund()`, `getStatus()`, `supportedPaymentMethods`
- [ProviderAConnector.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/impl/ProviderAConnector.kt) + [ProviderBConnector.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/impl/ProviderBConnector.kt) — Implement all methods
- [ProviderErrorClassifier.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/ProviderErrorClassifier.kt) — Retryable vs non-retryable error classification
- [ReconciliationJob.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/ReconciliationJob.kt) — Runs every 5 min, resolves stuck payments via provider `getStatus()`
- [PaymentOrchestrationService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentOrchestrationService.kt) — `cancelPayment()`, `refundPayment()`, `CreatePaymentResult` for 201/200

---

## Phase 5: Reliability

- [Resilience4jConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/Resilience4jConfig.kt) — CircuitBreaker (50% failure, 30s open), Retry (3 attempts, exponential), TimeLimiter (5s)
- [RetryCoordinator.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/RetryCoordinator.kt) — Wrapped with `CircuitBreaker.decorateSupplier` + `Retry.decorateSupplier`
- [OutboxProcessor.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/messaging/outbox/OutboxProcessor.kt) — Retry counting, dead-letter after 10 failures, idempotent Kafka producer
- [RedisPaymentLock.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/idempotency/RedisPaymentLock.kt) — SETNX with TTL + safe release
- [RateLimitFilter.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/security/RateLimitFilter.kt) — Redis sliding-window per merchant (STANDARD: 60/min, PREMIUM: 300/min, ENTERPRISE: 1000/min)
- [idempotency_check.lua](file:///d:/Databsaes/src/main/resources/scripts/lua/idempotency_check.lua) — Atomic check-and-set for Redis idempotency

---

## Phase 6: Security

- [WebSocketAuthInterceptor.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/security/WebSocketAuthInterceptor.kt) — Validates API key from query params at handshake
- [MerchantPrincipal.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/security/MerchantPrincipal.kt) — Now includes `rateLimitTier` + `allowedPaymentMethods`
- [ApiKeyAuthenticationFilter.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/security/ApiKeyAuthenticationFilter.kt) — Populates rateLimitTier, excludes Swagger
- [WebSocketConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/WebSocketConfig.kt) — Configurable allowed origins
- [SecurityConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/SecurityConfig.kt) — Rate limiter in filter chain, Swagger permitted

---

## Phase 7: Observability

- [PaymentMetrics.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/observability/PaymentMetrics.kt) — Micrometer counters/timers for payments, providers, idempotency, status
- [logback-spring.xml](file:///d:/Databsaes/src/main/resources/logback-spring.xml) — Structured JSON (prod) / human-readable (local)
- [DomainEventEnvelope.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/domain/event/DomainEventEnvelope.kt) — Typed event envelope with traceId, schemaVersion, producerService
- [OutboxPublisher.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/OutboxPublisher.kt) — Uses DomainEventEnvelope
- [OpenApiConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/OpenApiConfig.kt) — Springdoc with API key security scheme
- [application.yml](file:///d:/Databsaes/src/main/resources/application.yml) — Full config with Resilience4j per-provider settings
- [application-local.yml](file:///d:/Databsaes/src/main/resources/application-local.yml) + [application-test.yml](file:///d:/Databsaes/src/test/resources/application-test.yml)

---

## Phase 8: Tests

- [AbstractIntegrationTest.kt](file:///d:/Databsaes/src/test/kotlin/com/yuno/payment/integration/AbstractIntegrationTest.kt) — Shared Testcontainers (PostgreSQL 16, Redis 7, Kafka 7.6)
- [PaymentFlowIntegrationTest.kt](file:///d:/Databsaes/src/test/kotlin/com/yuno/payment/integration/PaymentFlowIntegrationTest.kt) — Create (201), idempotent replay (200), 404, auth, list
- [RetryCoordinatorTest.kt](file:///d:/Databsaes/src/test/kotlin/com/yuno/payment/orchestration/RetryCoordinatorTest.kt) — Happy path, failover, non-retryable stop, all-fail
- [LedgerServiceTest.kt](file:///d:/Databsaes/src/test/kotlin/com/yuno/payment/ledger/LedgerServiceTest.kt) — Double-entry capture + refund verification

---

## Next Steps

To run and verify:
```bash
# 1. Install Java 21 + Gradle 8.x
# 2. Generate Gradle Wrapper
gradle wrapper

# 3. Start infrastructure
docker compose up -d

# 4. Run unit tests
./gradlew test

# 5. Run integration tests (requires Docker)
./gradlew test --tests "*Integration*"

# 6. Boot the app
./gradlew bootRun

# 7. Test with curl
curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-API-Key: test_api_key_123" \
  -H "X-Idempotency-Key: test-001" \
  -H "Content-Type: application/json" \
  -d '{"amount":{"value":10000,"currency":"INR"},"paymentMethod":{"type":"CARD","card":{"number":"4111111111111111","expiryMonth":"12","expiryYear":"2027","cvv":"123","holderName":"Test"}},"merchantReference":"ORDER-001"}'
```
