# Complete Build-Out: Payment Orchestration System

Full implementation of all remaining components from the "left to build" summary, built as modules inside the current modular monolith.

---

## User Review Required

> [!IMPORTANT]
> This is a **very large scope** (~50+ files to create/modify). I recommend we execute it in phases, with a checkpoint after each phase so you can review before we continue. Each phase is designed to be independently compilable and testable.

> [!WARNING]
> **Phase 1 (Foundation) requires Java 21 + Gradle on your machine.** Without that, we can write all the code but can't verify it compiles. Do you want me to proceed writing code anyway, or should we get the toolchain set up first?

---

## Open Questions

> [!IMPORTANT]
> 1. **Java/Gradle availability** — Last known state: Java and Gradle are not on PATH. Has this changed? Should I skip Phase 1 verification and just write code?
> 2. **Ledger schema** — The plan mentions "records debit/credit." Should the ledger be double-entry (every event creates both a debit and credit row), or single-entry (one row per event with a direction field)? I'll default to **double-entry** unless you say otherwise.
> 3. **Webhook secrets** — The plan says HMAC-SHA256 signing. Should webhook secrets be per-merchant (stored in `merchants` table) or per-webhook-URL (separate table)?  I'll default to **per-merchant** with a new column.
> 4. **Analytics consumer** — You said "if needed for reporting/demo." Should I build this, or skip for now?
> 5. **Provider adapters** — "Replace simulated providers with real HTTP/gRPC-style adapters" — should these still be simulated responses but structured as real HTTP calls (using `RestClient`/`WebClient`), or do you have actual provider endpoints?

---

## Proposed Changes — 8 Phases

---

### Phase 1: Foundation

Get the project compiling and passing existing tests.

#### [MODIFY] [build.gradle.kts](file:///d:/Databsaes/build.gradle.kts)
- Add missing dependencies: `springdoc-openapi` for OpenAPI, `resilience4j-circuitbreaker`, `resilience4j-retry`, `resilience4j-timelimiter`, `resilience4j-bulkhead`, `logback-encoder` for structured JSON logging
- Add Kafka test dependency `spring-kafka-test`

#### [NEW] gradle wrapper files
- Run `gradle wrapper` to generate `gradlew`, `gradlew.bat`, `gradle/wrapper/`

#### Verify
- `gradlew test` passes for existing 3 unit tests

---

### Phase 2: Seed Data + Missing Endpoints + API Polish

Fill the REST API gaps and make the app demo-able.

#### [NEW] V8__seed_demo_merchant.sql
`src/main/resources/db/migration/V8__seed_demo_merchant.sql`
- Insert `merchant_demo` into `merchants`
- Insert API key with prefix `test_api_` and hash of `test_api_key_123` into `merchant_api_keys`

#### [MODIFY] [PaymentController.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/PaymentController.kt)
- Add `GET /api/v1/payments/{id}/status` — lightweight status-only response
- Add `POST /api/v1/payments/{id}/cancel` — cancel payment if in `INITIATED` state
- Add `GET /api/v1/payments` — list payments with pagination (`Pageable`) and optional status filter
- Fix idempotent replay: first request returns `201`, duplicate returns `200`

#### [NEW] PaymentStatusResponse.kt
`src/main/kotlin/com/yuno/payment/api/v1/dto/PaymentStatusResponse.kt`
- Lightweight DTO: `{paymentId, status, updatedAt}`

#### [MODIFY] [PaymentDtos.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/dto/PaymentDtos.kt)
- Replace `ErrorResponse` with RFC 7807 `ProblemDetail` style: `type`, `title`, `status`, `detail`, `instance`, `errorCode`, `traceId`, `timestamp`

#### [MODIFY] [ApiExceptionHandler.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/ApiExceptionHandler.kt)
- Return RFC 7807 `ProblemDetail` responses with `traceId` from MDC

#### [MODIFY] [PaymentOrchestrationService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentOrchestrationService.kt)
- Add `cancelPayment(paymentId, merchantId)` method
- Wire state transition `INITIATED → CANCELLED`

#### [MODIFY] [PaymentEventType.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentEventType.kt)
- Add `AUTHORIZED`, `CANCELLED`, `REFUNDED`, `STATUS_CHANGED`

#### [MODIFY] [PaymentRepository.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/persistence/repository/PaymentRepository.kt)
- Add `findByMerchantId(merchantId, Pageable): Page<PaymentEntity>`
- Add `findByMerchantIdAndStatus(merchantId, status, Pageable): Page<PaymentEntity>`

---

### Phase 3: Ledger + Kafka Consumers + Notification + Webhook

Build all side-effect modules as choreography consumers inside the monolith.

#### Ledger Module

#### [NEW] V9__create_ledger_entries_table.sql
`src/main/resources/db/migration/V9__create_ledger_entries_table.sql`
```sql
CREATE TABLE ledger_entries (
    id              BIGSERIAL       NOT NULL,
    payment_id      VARCHAR(26)     NOT NULL,
    merchant_id     VARCHAR(255)    NOT NULL,
    entry_type      VARCHAR(20)     NOT NULL,  -- DEBIT | CREDIT
    amount_value    BIGINT          NOT NULL,
    amount_currency VARCHAR(3)      NOT NULL,
    balance_after   BIGINT,                    -- running balance (optional)
    event_id        VARCHAR(255)    NOT NULL,  -- idempotency: Kafka eventId
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT uq_ledger_event UNIQUE (event_id, entry_type)
);
CREATE INDEX idx_ledger_payment ON ledger_entries (payment_id);
CREATE INDEX idx_ledger_merchant ON ledger_entries (merchant_id);
```

#### [NEW] LedgerEntryEntity.kt
`src/main/kotlin/com/yuno/payment/ledger/LedgerEntryEntity.kt`

#### [NEW] LedgerEntryRepository.kt
`src/main/kotlin/com/yuno/payment/ledger/LedgerEntryRepository.kt`

#### [NEW] LedgerService.kt
`src/main/kotlin/com/yuno/payment/ledger/LedgerService.kt`
- `recordCapture(paymentId, merchantId, amount, eventId)` — creates DEBIT + CREDIT pair
- `recordRefund(paymentId, merchantId, amount, eventId)` — reversal entries
- Idempotent: uses `event_id` unique constraint to skip duplicates

#### [NEW] LedgerConsumer.kt
`src/main/kotlin/com/yuno/payment/messaging/consumer/LedgerConsumer.kt`
- `@KafkaListener(topics = ["payment.v1.captured"], groupId = "ledger-service")`
- `@KafkaListener(topics = ["payment.v1.failed"], groupId = "ledger-service")` — no-op or reversal if needed
- Deserializes event envelope, delegates to `LedgerService`

---

#### Notification Module

#### [NEW] NotificationConsumer.kt
`src/main/kotlin/com/yuno/payment/messaging/consumer/NotificationConsumer.kt`
- `@KafkaListener(topics = ["payment.v1.captured", "payment.v1.failed"], groupId = "notification-service")`
- Logs notification (email/SMS simulation)
- Idempotent via in-memory `ConcurrentHashMap<String, Boolean>` or DB dedup

#### [NEW] NotificationService.kt
`src/main/kotlin/com/yuno/payment/notification/NotificationService.kt`
- `sendPaymentConfirmation(...)` / `sendPaymentFailure(...)` — logs for now

---

#### Webhook Module

#### [NEW] V10__create_webhook_deliveries_table.sql
```sql
CREATE TABLE webhook_deliveries (
    id              BIGSERIAL       NOT NULL,
    payment_id      VARCHAR(26)     NOT NULL,
    merchant_id     VARCHAR(255)    NOT NULL,
    webhook_url     VARCHAR(2048)   NOT NULL,
    event_type      VARCHAR(200)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(50)     NOT NULL,  -- PENDING | DELIVERED | FAILED
    http_status     INT,
    attempt_count   INT             NOT NULL DEFAULT 0,
    max_attempts    INT             NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMPTZ,
    last_error      VARCHAR(2000),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_webhook_deliveries PRIMARY KEY (id)
);
CREATE INDEX idx_webhook_pending ON webhook_deliveries (next_retry_at) WHERE status = 'PENDING';
```

#### [NEW] WebhookDeliveryEntity.kt, WebhookDeliveryRepository.kt
`src/main/kotlin/com/yuno/payment/webhook/`

#### [NEW] WebhookService.kt
- Creates delivery record with HMAC-SHA256 signature
- Sends HTTP POST with `X-Yuno-Signature`, `X-Yuno-Timestamp`, `X-Yuno-Event-Type`
- Records result

#### [NEW] WebhookRetryProcessor.kt
- `@Scheduled` — picks up PENDING deliveries past `next_retry_at`
- Exponential backoff: 1min, 5min, 30min, 2h, 24h
- Marks FAILED after max attempts

#### [NEW] WebhookConsumer.kt
`src/main/kotlin/com/yuno/payment/messaging/consumer/WebhookConsumer.kt`
- `@KafkaListener(topics = ["payment.v1.captured", "payment.v1.failed", ...], groupId = "webhook-service")`
- Delegates to `WebhookService`

---

#### Kafka Consumer Config

#### [MODIFY] [KafkaConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/KafkaConfig.kt)
- Add `ConsumerFactory<String, String>` bean
- Add `ConcurrentKafkaListenerContainerFactory` bean
- Configure JSON deserialization, error handling, dead-letter topic

---

### Phase 4: Refund Flow + Reconciliation + Provider Improvements

#### [MODIFY] [ProviderConnector.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/provider/ProviderConnector.kt)
- Add `supportedPaymentMethods: Set<PaymentMethodType>`
- Add `refund(payment: PaymentEntity): ProviderResult`
- Add `getStatus(providerTransactionId: String): ProviderResult`
- Add default implementations for backward compat

#### [MODIFY] ProviderAConnector.kt + ProviderBConnector.kt
- Implement `refund()` and `getStatus()` simulations
- Add `supportedPaymentMethods`
- Structure as real HTTP-style adapters using `RestClient` (simulated responses)

#### [NEW] ProviderErrorClassifier.kt
`src/main/kotlin/com/yuno/payment/provider/ProviderErrorClassifier.kt`
- Classifies exceptions into retryable vs non-retryable
- Maps provider error codes to internal error taxonomy

#### [MODIFY] [PaymentOrchestrationService.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/PaymentOrchestrationService.kt)
- Add `refundPayment(paymentId, merchantId)` method
- State transition: `CAPTURED → REFUNDED`

#### [MODIFY] [PaymentController.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/api/v1/PaymentController.kt)
- Add `POST /api/v1/payments/{id}/refund`

#### [NEW] ReconciliationJob.kt
`src/main/kotlin/com/yuno/payment/orchestration/ReconciliationJob.kt`
- `@Scheduled(fixedDelay = 300_000)` — runs every 5 minutes
- Finds payments stuck in `PROCESSING` or `RETRY_PENDING` older than 10 minutes
- Calls `provider.getStatus()` to resolve actual state
- Transitions to terminal state

#### [MODIFY] [PaymentRepository.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/persistence/repository/PaymentRepository.kt)
- Add `findByStatusInAndUpdatedAtBefore(statuses, cutoff): List<PaymentEntity>`

---

### Phase 5: Reliability — Resilience4j, Outbox, Redis, Rate Limiting

#### [NEW] Resilience4jConfig.kt
`src/main/kotlin/com/yuno/payment/config/Resilience4jConfig.kt`
- Per-provider circuit breaker instances
- Per-provider retry instances with exponential backoff
- Per-provider time limiter

#### [MODIFY] application.yml
- Add full `resilience4j:` config block from plan §10
- Add provider endpoint config section

#### [MODIFY] [RetryCoordinator.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/RetryCoordinator.kt)
- Wrap provider calls with `CircuitBreaker.decorateSupplier` + `Retry.decorateSupplier`
- Inject `CircuitBreakerRegistry`, `RetryRegistry`, `TimeLimiterRegistry`
- Classify errors using `ProviderErrorClassifier`

#### [MODIFY] [OutboxProcessor.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/messaging/outbox/OutboxProcessor.kt)
- Use `SELECT ... FOR UPDATE SKIP LOCKED` for multi-instance safety
- Add `retry_count` column tracking (new migration)
- Add dead-letter handling: move to `outbox_dead_letters` after N failures
- Add duplicate publish protection via Kafka idempotent producer config

#### [NEW] V11__add_outbox_retry_count.sql
- Add `retry_count INT NOT NULL DEFAULT 0` and `max_retries INT NOT NULL DEFAULT 10` to `outbox_events`

#### [NEW] RedisIdempotencyService.kt
`src/main/kotlin/com/yuno/payment/idempotency/RedisIdempotencyService.kt`
- Redis-primary idempotency with Lua check-and-set
- Falls back to DB `IdempotencyService` on Redis failure

#### [NEW] idempotency_check.lua
`src/main/resources/scripts/lua/idempotency_check.lua`
- Atomic check-and-set as described in plan §8.3

#### [NEW] RedisPaymentLock.kt
`src/main/kotlin/com/yuno/payment/idempotency/RedisPaymentLock.kt`
- `withPaymentLock(paymentId, block)` using `SETNX` + Lua release
- Prevents concurrent processing of same payment

#### [NEW] RateLimitFilter.kt
`src/main/kotlin/com/yuno/payment/security/RateLimitFilter.kt`
- Redis sliding window rate limiter per merchant
- Uses API key `rate_limit_tier` to determine limits

---

### Phase 6: Security Hardening

#### [MODIFY] [WebSocketConfig.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/config/WebSocketConfig.kt)
- Restrict allowed origins (configurable via `application.yml`)

#### [NEW] WebSocketAuthInterceptor.kt
`src/main/kotlin/com/yuno/payment/security/WebSocketAuthInterceptor.kt`
- Validates API key from STOMP CONNECT frame headers
- Sets merchant principal in WebSocket session

#### [NEW] WebSocketSubscriptionInterceptor.kt
`src/main/kotlin/com/yuno/payment/security/WebSocketSubscriptionInterceptor.kt`
- On SUBSCRIBE to `/topic/payments/{merchantId}/{paymentId}`, verifies the subscriber's merchant ID matches

#### [MODIFY] [ApiKeyAuthenticationFilter.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/security/ApiKeyAuthenticationFilter.kt)
- Enforce `allowed_payment_methods` from API key against request
- Enforce `environment` separation (test key can't hit live, etc.)

---

### Phase 7: Observability

#### [NEW] PaymentMetrics.kt
`src/main/kotlin/com/yuno/payment/observability/PaymentMetrics.kt`
- Custom Micrometer metrics:
  - `payments.created` counter (by method, merchant)
  - `payment.provider.latency` timer (by provider, status)
  - `payments.pending` gauge
  - `payments.idempotency.hits` counter
  - `outbox.lag` gauge (count of unpublished events)
  - `provider.circuit_breaker.state` gauge

#### [NEW] logback-spring.xml
`src/main/resources/logback-spring.xml`
- Structured JSON output with `traceId`, `spanId`, `merchantId`, `paymentId` from MDC

#### [MODIFY] [OutboxPublisher.kt](file:///d:/Databsaes/src/main/kotlin/com/yuno/payment/orchestration/OutboxPublisher.kt)
- Include `traceId`, `schemaVersion`, `producerService` in event envelope

#### [NEW] application-local.yml
`src/main/resources/application-local.yml`
- Human-readable console logging for local dev

#### [NEW] application-test.yml
`src/test/resources/application-test.yml`
- Testcontainers-compatible config, reduced log noise

#### [NEW] OpenApiConfig.kt
`src/main/kotlin/com/yuno/payment/config/OpenApiConfig.kt`
- Springdoc OpenAPI configuration with title, version, security scheme

---

### Phase 8: Tests

#### [NEW] Integration test base class
`src/test/kotlin/com/yuno/payment/integration/AbstractIntegrationTest.kt`
- Testcontainers for PostgreSQL, Redis, Kafka
- Shared container lifecycle

#### [NEW] PaymentFlowIntegrationTest.kt
- Full create → captured flow with real DB + Kafka
- Cancel flow
- Refund flow
- Idempotency replay

#### [NEW] PaymentControllerTest.kt (MockMvc)
- Create payment (happy path)
- Create payment (missing API key → 401)
- Create payment (invalid body → 400)
- Get payment (not found → 404)
- Cancel payment
- List payments with pagination
- Status endpoint

#### [NEW] IdempotencyIntegrationTest.kt
- Same key, same body → same response
- Same key, different body → 409 Conflict
- Concurrent duplicate requests (parallel threads)

#### [NEW] OutboxProcessorTest.kt
- Publish success → marks published
- Kafka failure → stays unpublished, retried next cycle
- Dead-letter after max retries

#### [NEW] LedgerConsumerTest.kt
- Captured event → creates debit + credit entries
- Duplicate event → no duplicate entries (idempotent)

#### [NEW] RetryCoordinatorTest.kt
- Provider A fails (retryable) → falls over to Provider B
- Provider A fails (non-retryable) → stops immediately
- Circuit breaker open → skips provider

---

## Verification Plan

### Automated Tests
```bash
gradlew test                        # All unit tests
gradlew test --tests "*Integration*" # Integration tests (requires Docker)
```

### Manual Verification
- `docker compose up -d` + `gradlew bootRun`
- Create payment with demo credentials
- Verify Kafka events appear in topics
- Verify ledger entries created
- Check Jaeger UI for distributed traces
- Check Prometheus metrics at `/actuator/prometheus`
- Test WebSocket with STOMP client
- Test cancel and refund flows

---

## File Count Estimate

| Phase | New Files | Modified Files |
|---|---|---|
| Phase 1: Foundation | 0 | 1 |
| Phase 2: Endpoints + API Polish | 3 | 6 |
| Phase 3: Ledger + Consumers + Webhook | 14 | 1 |
| Phase 4: Refund + Reconciliation | 3 | 5 |
| Phase 5: Reliability | 5 | 4 |
| Phase 6: Security | 2 | 2 |
| Phase 7: Observability | 4 | 2 |
| Phase 8: Tests | 7 | 0 |
| **Total** | **~38 new** | **~21 modified** |
