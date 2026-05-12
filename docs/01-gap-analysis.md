# Gap Analysis: Plan vs Implemented Code

## Summary

I've cross-referenced every component in `plan.md` (sections 1–18), `memory.md`, and `README.md` against the actual source tree. Below is a **complete inventory** of what exists, what's missing, and what's only partially done.

---

## ✅ Fully Implemented

| Component | Files |
|---|---|
| Spring Boot 3 + Kotlin skeleton | [PaymentApplication.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/PaymentApplication.kt) |
| Docker Compose (PG, Redis, Kafka, Jaeger) | [docker-compose.yml](file:///d:/Databsaes/docker-compose.yml) |
| Flyway migrations V0–V7 | [db/migration/](file:///d:/Databsaes/src/main/resources/db/migration) (8 files) |
| JPA entities (all 8 tables) | [persistence/entity/](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/persistence/entity) |
| Repositories (all 8) | [persistence/repository/](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/persistence/repository) |
| Payment state machine (explicit transition table) | [PaymentStateMachine.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentStateMachine.kt) |
| PaymentStatus enum with `canTransitionTo` | [PaymentStatus.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/domain/model/PaymentStatus.kt) |
| Routing engine (DB-backed, priority-sorted, filter by currency/amount) | [RoutingEngine.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/routing/RoutingEngine.kt) |
| Provider connector interface + registry | [ProviderConnector.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/ProviderConnector.kt), [ProviderConnectorRegistry.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/ProviderConnectorRegistry.kt) |
| Simulated providers A & B | [impl/](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/impl) (2 files) |
| Orchestration service (SAGA steps) | [PaymentOrchestrationService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentOrchestrationService.kt) |
| Retry coordinator with failover + attempt recording | [RetryCoordinator.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/RetryCoordinator.kt) |
| Outbox publisher (same-TX outbox row creation) | [OutboxPublisher.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/OutboxPublisher.kt) |
| Outbox processor (scheduled Kafka publish) | [OutboxProcessor.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/messaging/outbox/OutboxProcessor.kt) |
| Idempotency service (DB-backed, hash check, conflict detection) | [IdempotencyService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/idempotency/IdempotencyService.kt) |
| API key authentication filter | [ApiKeyAuthenticationFilter.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/security/ApiKeyAuthenticationFilter.kt) |
| Spring Security config | [SecurityConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/SecurityConfig.kt) |
| Payment create + get REST endpoints | [PaymentController.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/PaymentController.kt) |
| Request validation (card, UPI, currency, amount) | [PaymentDtos.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/dto/PaymentDtos.kt) |
| API exception handler | [ApiExceptionHandler.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/ApiExceptionHandler.kt) |
| WebSocket/STOMP config | [WebSocketConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/WebSocketConfig.kt) |
| WebSocket status publisher | [WebSocketPaymentStatusPublisher.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/realtime/WebSocketPaymentStatusPublisher.kt) |
| Redis pub/sub status publisher | [RedisPaymentStatusPublisher.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/realtime/RedisPaymentStatusPublisher.kt) |
| PaymentStatusChangedApplicationEvent | [PaymentStatusChangedApplicationEvent.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentStatusChangedApplicationEvent.kt) |
| Domain exceptions (3) | [domain/exception/](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/domain/exception) |
| DTO mapper | [PaymentDtoMapper.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/mapper/PaymentDtoMapper.kt) |
| Unit tests (3) | PaymentStatusTest, PaymentMethodRequestValidationTest, RoutingEngineTest |
| application.yml with virtual threads, tracing, Redis, Kafka | [application.yml](file:///d:/Databsaes/src/main/resources/application.yml) |

---

## ❌ Completely Missing Components

### 1. Kafka Consumers (Plan §5.5, §14)

> [!CAUTION]
> **No Kafka consumer classes exist.** The `messaging/consumer/` package is entirely absent.

The plan specifies two consumers that react to outbox-published Kafka events:

- **`NotificationConsumer`** — listens to `payment.v1.succeeded` / `payment.v1.failed`, sends email/SMS confirmations
- **`LedgerConsumer`** — listens to `payment.v1.captured` / `payment.v1.failed`, records debit/credit entries

The plan also references an **`AnalyticsConsumer`** in the architecture diagram. None of these exist.

**What's needed:**
- `messaging/consumer/NotificationConsumer.kt` — `@KafkaListener` for notification events
- `messaging/consumer/LedgerConsumer.kt` — `@KafkaListener` for ledger entries

At minimum, these should be skeleton implementations that log the received events, since the plan says they're side-effect consumers in the choreography layer.

---

### 2. Ledger Module (Plan §2 architecture diagram)

> [!IMPORTANT]
> The Ledger is referenced in the plan architecture as a consumer that "records debit/credit." There is no ledger table, no ledger entity, and no ledger service.

If you want Ledger to be more than just a Kafka consumer logging events, it would need:
- A `ledger_entries` table (migration)
- A `LedgerEntryEntity`
- A `LedgerEntryRepository`
- The `LedgerConsumer` that writes entries

---

### 3. Redis Config Class (Plan §14)

The plan's project structure lists `config/RedisConfig.kt`. There is no `RedisConfig.kt` — Spring Boot auto-configures a `StringRedisTemplate` from `application.yml`, which works, but the plan specifically calls for a config class. This is a minor gap.

---

### 4. OpenAPI / Swagger Config (Plan §14)

The plan lists `config/OpenApiConfig.kt` for API documentation. This file does not exist. No OpenAPI/Swagger dependency is in `build.gradle.kts` either.

---

### 5. Resilience4j Config Class (Plan §10, §14)

> [!WARNING]
> The plan lists `config/Resilience4jConfig.kt` with detailed per-provider circuit breaker, retry, and time-limiter configuration. No config class exists and no `resilience4j:` configuration block is in `application.yml`.

The `resilience4j-spring-boot3` dependency **is** in `build.gradle.kts`, but it's completely unconfigured. The `RetryCoordinator` does plain `try/catch` failover — **no Resilience4j decorators** are used (unlike the plan §8.5 which shows `CircuitBreaker.decorateSupplier` + `Retry.decorateSupplier`).

---

### 6. Cancel Payment Endpoint (Plan §4.1)

The plan specifies:
```
POST /api/v1/payments/{id}/cancel → Cancel payment
```

This endpoint is not implemented. The controller only has `POST /` (create) and `GET /{id}` (get).

---

### 7. List Payments Endpoint (Plan §4.1)

The plan specifies:
```
GET /api/v1/payments → List payments (paginated, filtered)
```

This endpoint is not implemented.

---

### 8. Lightweight Status Check Endpoint (Plan §4.1)

The plan specifies:
```
GET /api/v1/payments/{id}/status → Lightweight status check
```

This endpoint is not implemented.

---

### 9. Redis Idempotency with Lua Script (Plan §8.3, §9)

> [!WARNING]
> The plan specifies Redis as the **primary** idempotency store with atomic Lua check-and-set, and PostgreSQL as fallback. Current implementation uses only PostgreSQL for idempotency — no Redis idempotency at all.

The plan's project structure also lists:
- `idempotency/scripts/idempotency_check.lua`
- `resources/scripts/lua/idempotency_check.lua`

Neither exists.

---

### 10. Redis Distributed Lock (Plan §9)

The plan describes a `withPaymentLock()` function using Redis `SETNX` to prevent concurrent processing of the same payment. This is not implemented anywhere.

---

### 11. Kafka Consumer Config (Plan §14)

`KafkaConfig.kt` only has a producer factory. No consumer factory or consumer config exists (needed once consumers are added).

---

### 12. Domain Event Envelope (Plan §5.2, §14)

The plan specifies `domain/event/PaymentEvent.kt` and `domain/event/DomainEventEnvelope.kt` as proper typed event classes with `eventId`, `eventVersion`, `schemaVersion`, `traceId`, etc. These don't exist. The `OutboxPublisher` creates the envelope inline as a raw `Map<String, Any?>` — functional but not type-safe.

---

### 13. Webhook Delivery (Plan §4.4)

The plan describes a webhook push mechanism where merchants who prefer webhooks over WebSocket get POST callbacks with HMAC-SHA256 signatures. No webhook delivery code exists.

---

### 14. Reconciliation Job (Plan §10)

The plan describes a `@Scheduled` reconciliation job that finds payments stuck in `PROCESSING` and polls the provider for their real status. Not implemented.

---

### 15. `application-local.yml` / `application-test.yml` (Plan §14)

The plan lists both files. Neither exists. Tests and local dev both use the main `application.yml`.

---

### 16. Seed Data for Demo Merchant

The `README.md` references demo credentials (`merchant_demo` / `test_api_key_123`), but there is no Flyway migration or `data.sql` that seeds this merchant and API key into the database. The app would start with empty `merchants` and `merchant_api_keys` tables.

---

## ⚠️ Partially Implemented / Deviations from Plan

### 1. Error Response Format

The plan specifies **RFC 7807 Problem Details** format with `type`, `title`, `status`, `detail`, `instance`, `errorCode`, `traceId`, `timestamp`. The actual `ErrorResponse` is a simple `{error, message}` — much simpler than specified.

### 2. HATEOAS `_links` in Payment Response

The plan shows `_links` with `self`, `status`, and `cancel` URLs in the response. Not present.

### 3. PaymentEventType is Incomplete

`PaymentEventType` has `INITIATED`, `PROCESSING`, `CAPTURED`, `FAILED`. The plan adds: `AUTHORIZED`, `CANCELLED`, `STATUS_CHANGED`. These are missing.

### 4. ProviderConnector Interface

The plan specifies `supportedPaymentMethods: Set<PaymentMethodType>`, `charge(ProviderChargeRequest)`, `refund(ProviderRefundRequest)`, `getStatus(paymentId)`. The actual interface is simplified to just `name` and `charge(PaymentEntity)`. No refund or status check methods.

### 5. Outbox Envelope Missing Fields

The plan's event envelope includes `schemaVersion`, `producerService`, `traceId`. The outbox publisher doesn't include these.

### 6. Structured Logging

The plan specifies structured JSON logging with `traceId`, `merchantId`, `paymentId` in every log line. No custom logging configuration exists (no `logback-spring.xml` for JSON output).

---

## Priority Ranking for Missing Items

| Priority | Item | Effort | Reason |
|---|---|---|---|
| **P0** | Kafka consumers (Notification + Ledger) | Medium | Core architecture diagram components; without them the outbox publishes into the void |
| **P0** | Seed data migration for demo merchant | Small | App is untestable without it |
| **P1** | Cancel payment endpoint | Small | Plan §4.1 API contract |
| **P1** | List payments endpoint (paginated) | Small | Plan §4.1 API contract |
| **P1** | Status check endpoint | Small | Plan §4.1 API contract |
| **P1** | Resilience4j config (circuit breaker + retry + timeout) | Medium | Plan §10, dependency exists but unused |
| **P1** | Redis idempotency + Lua script | Medium | Plan §8.3 — core architectural decision |
| **P2** | Domain event envelope classes | Small | Type safety for event schema |
| **P2** | RFC 7807 error responses | Small | Plan §4.1 — API standard compliance |
| **P2** | Kafka consumer config | Small | Required for consumers to work |
| **P2** | `application-test.yml` | Small | Test isolation |
| **P2** | Seed data migration | Small | Demo-ability |
| **P3** | Reconciliation job | Medium | Plan §10, operational safety |
| **P3** | Webhook delivery | Medium | Plan §4.4 |
| **P3** | Redis distributed lock | Small | Plan §9 |
| **P3** | OpenAPI config | Small | Plan §14 |
| **P3** | Structured JSON logging config | Small | Plan §12 |
| **P3** | HATEOAS `_links` | Small | Plan §4.1 — nice to have |
