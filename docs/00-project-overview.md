# Payment Orchestration System — Project Overview

A production-grade **modular monolith** payment orchestration system built with Spring Boot 3, Kotlin, and Java 21 virtual threads.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9 + Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache/Lock/Rate-Limit | Redis 7 |
| Messaging | Apache Kafka (Outbox pattern) |
| Resilience | Resilience4j (Circuit Breaker, Retry, TimeLimiter) |
| Real-time | WebSocket/STOMP + Redis Pub/Sub |
| Observability | Micrometer, OpenTelemetry, Structured JSON Logging |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Testcontainers, MockK |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                        │
│  PaymentController (6 endpoints)                        │
│  ApiKeyAuthenticationFilter + RateLimitFilter            │
├─────────────────────────────────────────────────────────┤
│                 Orchestration Layer                      │
│  PaymentOrchestrationService (SAGA)                     │
│  RetryCoordinator (Resilience4j wrapped)                │
│  PaymentStateMachine                                    │
│  ReconciliationJob                                      │
├─────────────────────────────────────────────────────────┤
│              Provider Connector Layer                   │
│  ProviderConnector → ProviderA, ProviderB               │
│  ProviderErrorClassifier                                │
│  ProviderConnectorRegistry                              │
├─────────────────────────────────────────────────────────┤
│                 Messaging (Kafka)                        │
│  OutboxPublisher → OutboxProcessor → Kafka Topics       │
│  LedgerConsumer │ NotificationConsumer │ WebhookConsumer │
├─────────────────────────────────────────────────────────┤
│                Side-Effect Modules                      │
│  LedgerService (double-entry)                           │
│  NotificationService                                    │
│  WebhookService (HMAC-SHA256, retry w/ backoff)         │
├─────────────────────────────────────────────────────────┤
│              Cross-Cutting Concerns                     │
│  IdempotencyService (DB + Redis Lua)                    │
│  RedisPaymentLock (distributed lock)                    │
│  PaymentMetrics (Micrometer)                            │
│  WebSocket/STOMP real-time updates                      │
├─────────────────────────────────────────────────────────┤
│                  Persistence Layer                       │
│  JPA Entities (8 tables) + Spring Data Repositories     │
│  Flyway Migrations (V0 — V11)                           │
│  PostgreSQL 16                                          │
└─────────────────────────────────────────────────────────┘
```

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/payments` | Create payment (idempotent, 201/200) |
| `GET` | `/api/v1/payments/{id}` | Get payment by ID |
| `GET` | `/api/v1/payments/{id}/status` | Lightweight status check |
| `POST` | `/api/v1/payments/{id}/cancel` | Cancel payment |
| `POST` | `/api/v1/payments/{id}/refund` | Refund payment |
| `GET` | `/api/v1/payments` | List payments (paginated + filtered) |

---

## Database Migrations

| Version | Description |
|---|---|
| V0 | Merchants and API keys tables |
| V1 | Payments table |
| V2 | Payment attempts table |
| V3 | Payment status history |
| V4 | Outbox events |
| V5 | Idempotency keys |
| V6 | Providers |
| V7 | Routing rules |
| V8 | Seed demo merchant data |
| V9 | Ledger entries (double-entry) |
| V10 | Webhook deliveries |
| V11 | Webhook secret + outbox retry columns |

---

## Project Structure

```
src/main/kotlin/com/yuno/payment/
├── api/                          # REST layer
│   ├── ApiExceptionHandler.kt    # RFC 7807 error handling
│   └── v1/
│       ├── PaymentController.kt  # 6 endpoints
│       ├── dto/                  # Request/Response DTOs
│       └── mapper/               # Entity ↔ DTO mapping
├── config/                       # Configuration
│   ├── AsyncConfig.kt
│   ├── KafkaConfig.kt            # Producer + Consumer factories
│   ├── OpenApiConfig.kt          # Swagger/OpenAPI
│   ├── RedisConfig.kt
│   ├── Resilience4jConfig.kt     # CB + Retry + TimeLimiter
│   ├── SecurityConfig.kt
│   └── WebSocketConfig.kt
├── domain/
│   ├── event/
│   │   └── DomainEventEnvelope.kt
│   ├── exception/                # Domain exceptions
│   └── model/                    # Value objects (Money, PaymentStatus, etc.)
├── idempotency/
│   ├── IdempotencyService.kt     # DB-backed
│   └── RedisPaymentLock.kt       # Distributed lock
├── ledger/                       # Double-entry ledger
│   ├── LedgerEntryEntity.kt
│   ├── LedgerEntryRepository.kt
│   └── LedgerService.kt
├── messaging/
│   ├── consumer/                 # Kafka consumers
│   │   ├── LedgerConsumer.kt
│   │   ├── NotificationConsumer.kt
│   │   └── WebhookConsumer.kt
│   └── outbox/
│       └── OutboxProcessor.kt    # Transactional outbox → Kafka
├── notification/
│   └── NotificationService.kt
├── observability/
│   └── PaymentMetrics.kt         # Micrometer metrics
├── orchestration/
│   ├── OutboxPublisher.kt
│   ├── PaymentEventType.kt
│   ├── PaymentOrchestrationService.kt  # Main SAGA orchestrator
│   ├── PaymentStateMachine.kt
│   ├── PaymentStatusChangedApplicationEvent.kt
│   ├── ReconciliationJob.kt
│   └── RetryCoordinator.kt       # Resilience4j-wrapped
├── persistence/
│   ├── entity/                   # JPA entities (8)
│   └── repository/               # Spring Data repos (8)
├── provider/
│   ├── ProviderConnector.kt      # Interface
│   ├── ProviderConnectorRegistry.kt
│   ├── ProviderErrorClassifier.kt
│   ├── model/ProviderResult.kt
│   └── impl/                     # ProviderA, ProviderB
├── realtime/
│   ├── RedisPaymentStatusPublisher.kt
│   └── WebSocketPaymentStatusPublisher.kt
├── routing/
│   └── RoutingEngine.kt          # DB-backed routing rules
├── security/
│   ├── ApiKeyAuthenticationFilter.kt
│   ├── ApiKeyHasher.kt
│   ├── MerchantPrincipal.kt
│   ├── RateLimitFilter.kt        # Redis sliding window
│   └── WebSocketAuthInterceptor.kt
├── support/
│   └── IdGenerator.kt            # ULID generator
├── webhook/
│   ├── WebhookDeliveryEntity.kt
│   ├── WebhookDeliveryRepository.kt
│   ├── WebhookRetryProcessor.kt  # Scheduled retry
│   └── WebhookService.kt         # HMAC-SHA256 signing
└── PaymentApplication.kt

src/main/resources/
├── application.yml               # Main config
├── application-local.yml         # Local dev profile
├── db/migration/                 # Flyway (V0–V11)
├── logback-spring.xml            # Structured JSON logging
└── scripts/lua/
    └── idempotency_check.lua     # Redis Lua script

src/test/
├── kotlin/.../
│   ├── integration/              # Testcontainers-based
│   │   ├── AbstractIntegrationTest.kt
│   │   └── PaymentFlowIntegrationTest.kt
│   ├── ledger/LedgerServiceTest.kt
│   ├── orchestration/RetryCoordinatorTest.kt
│   └── ...unit tests...
└── resources/
    └── application-test.yml
```

---

## Infrastructure Requirements

To run this system you need:

1. **Java 21** — required for virtual threads
2. **PostgreSQL 16** — primary datastore
3. **Redis 7** — caching, locks, rate limiting, pub/sub
4. **Apache Kafka** — event streaming (outbox pattern)

### Running Without Docker

If you cannot run Docker, you can install these services natively or use managed cloud services:

#### Option A: Native Installation (Windows)
- **PostgreSQL**: Download from https://www.postgresql.org/download/windows/
- **Redis**: Use Memurai (Redis-compatible for Windows) from https://www.memurai.com/ or Redis on WSL2
- **Kafka**: Download from https://kafka.apache.org/downloads — run with `bin\windows\` scripts

#### Option B: Cloud/Managed Services
- **PostgreSQL**: Use Neon (free tier), Supabase, or AWS RDS
- **Redis**: Use Upstash (free tier), Redis Cloud, or AWS ElastiCache  
- **Kafka**: Use Confluent Cloud (free tier) or Upstash Kafka

Update `application-local.yml` with your connection details.

---

## Quick Start (Without Docker)

```bash
# 1. Install Java 21
# 2. Install PostgreSQL, Redis, Kafka (see options above)

# 3. Create the database
psql -U postgres -c "CREATE DATABASE payment_db;"

# 4. Update application-local.yml with your connection strings

# 5. Generate Gradle wrapper (if not present)
gradle wrapper

# 6. Run tests (unit tests only, no infra needed)
./gradlew test --tests "*Test" --exclude-task "*Integration*"

# 7. Boot the application
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# 8. Test the API
curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-API-Key: test_api_key_123" \
  -H "X-Idempotency-Key: test-001" \
  -H "Content-Type: application/json" \
  -d '{"amount":{"value":10000,"currency":"INR"},"paymentMethod":{"type":"CARD","card":{"number":"4111111111111111","expiryMonth":"12","expiryYear":"2027","cvv":"123","holderName":"Test"}},"merchantReference":"ORDER-001"}'
```

---

## Documentation Index

| Document | Description |
|---|---|
| [01-gap-analysis.md](./01-gap-analysis.md) | Full inventory of plan vs implementation |
| [02-implementation-plan.md](./02-implementation-plan.md) | Detailed 8-phase implementation plan |
| [03-task-tracker.md](./03-task-tracker.md) | Task completion checklist |
| [04-walkthrough.md](./04-walkthrough.md) | Summary of all changes made |
