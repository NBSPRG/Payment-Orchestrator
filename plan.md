# Payment Orchestration System — System Design Plan

> **Language:** Kotlin 1.9+  
> **Runtime:** JVM 21 (Virtual Threads)  
> **Framework:** Spring Boot 3.x  
> **Status:** Pre-implementation design document — no code written yet

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [Tech Stack & Rationale](#3-tech-stack--rationale)
4. [API Design Layer](#4-api-design-layer)
5. [Event & Messaging Architecture](#5-event--messaging-architecture)
6. [Real-time Layer](#6-real-time-layer)
7. [Data Model & Schema](#7-data-model--schema)
8. [Core Components](#8-core-components)
9. [Redis Strategy](#9-redis-strategy)
10. [Resilience & Fault Tolerance](#10-resilience--fault-tolerance)
11. [Backward Compatibility Strategy](#11-backward-compatibility-strategy)
12. [Observability](#12-observability)
13. [Security](#13-security)
14. [Project Structure](#14-project-structure)
15. [Sequence Diagrams](#15-sequence-diagrams)
16. [Test Strategy](#16-test-strategy)
17. [Performance Considerations](#17-performance-considerations)
18. [Evolution Roadmap](#18-evolution-roadmap)

---

## 1. Executive Summary

This system implements a **production-grade payment orchestration layer** inspired by Yuno's real platform. The design philosophy is:

> **Correctness first. Scalability second. Simplicity always.**

### Core Design Decisions (and why)

| Decision | Choice | Why Not the Alternative |
|---|---|---|
| Transaction pattern | Orchestration SAGA (critical path) + Choreography (side effects) | 2PC impossible across HTTP; pure choreography loses control over money movement |
| Consistency model | Eventual consistency with idempotency guarantees | Strong consistency requires distributed locks everywhere — kills throughput |
| API protocol | REST (external) + gRPC (internal providers) + WebSocket (real-time) | GraphQL overhead not justified for payment flows; Federation premature |
| Event bus | Kafka | RabbitMQ lacks replay; Kafka gives durability, partitioning by merchant, replay for reconciliation |
| Cache/Lock store | Redis | TTL-native, Lua atomic scripts, pub/sub for WebSocket fan-out |
| Idempotency store | Redis primary + DB fallback | Redis for speed; DB for durability after Redis TTL |
| Concurrency model | JVM 21 Virtual Threads + Spring MVC | Simpler than WebFlux; same I/O throughput; easier testing |

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                          CLIENT (Merchant)                           │
│               REST /api/v1/     WebSocket /ws/payments/{id}          │
└────────────────────────┬────────────────────┬────────────────────────┘
                         │ HTTPS               │ WSS
                         ▼                     ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                           │
│                                                                      │
│  ┌─────────────────┐   ┌──────────────────────────────────────────┐ │
│  │ REST Controller  │   │  WebSocket Handler                       │ │
│  │ /api/v1/payments │   │  Subscribes to Redis pub/sub channel     │ │
│  └────────┬────────┘   └──────────────────────────────────────────┘ │
│           │                                                          │
│           ▼                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │              PaymentOrchestrationService (SAGA Brain)           │ │
│  │                                                                 │ │
│  │  1. IdempotencyService      ← Redis Lua atomic check-and-set   │ │
│  │  2. PaymentStateMachine     ← Explicit state transitions        │ │
│  │  3. RoutingEngine           ← CARD→A, UPI→B, rules-based       │ │
│  │  4. ProviderConnector       ← gRPC or HTTP adapter              │ │
│  │  5. RetryCoordinator        ← Resilience4j + failover logic     │ │
│  │  6. OutboxPublisher         ← Writes events in same DB TX       │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│           │                                 │                        │
│           ▼                                 ▼                        │
│  ┌─────────────────┐             ┌─────────────────────┐            │
│  │  Provider A      │             │  Provider B          │            │
│  │  (CARD — gRPC/  │             │  (UPI — gRPC/HTTP)   │            │
│  │   HTTP adapter) │             │                      │            │
│  └─────────────────┘             └─────────────────────┘            │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                   PERSISTENCE LAYER                           │  │
│  │   PostgreSQL (JPA + Flyway)    Redis (Lettuce)                │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              OUTBOX PROCESSOR (Scheduled/CDC)                 │  │
│  │   Reads unpublished outbox rows → Publishes to Kafka          │  │
│  └───────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
                         │ Kafka Topics
                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      KAFKA EVENT BUS                                 │
│                                                                      │
│   payment.v1.initiated    payment.v1.processing                      │
│   payment.v1.succeeded    payment.v1.failed                          │
│   payment.v1.status_changed                                          │
└──────────┬────────────────────────┬─────────────────────────────────┘
           │                        │
           ▼                        ▼
  NotificationConsumer       LedgerConsumer          AnalyticsConsumer
  (sends email/SMS)          (records debit/credit)  (updates dashboards)
```

### SAGA Boundary

```
┌─── ORCHESTRATION (Synchronous, Money Critical) ─────────────────┐
│  Idempotency → Route → Charge → Persist → Emit terminal event   │
│  This is the SAGA. If any step fails, orchestrator compensates.  │
└──────────────────────────────────────┬──────────────────────────┘
                                       │ Publishes domain event (via Outbox)
┌─── CHOREOGRAPHY (Async, Side Effects) ──────────────────────────┐
│  Notification, Ledger, Analytics, Webhooks react to events      │
│  Eventual consistency is acceptable here                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Tech Stack & Rationale

### Core

| Component | Technology | Version | Rationale |
|---|---|---|---|
| Language | Kotlin | 1.9.x | Null safety, data classes, coroutines-ready, concise DSL |
| Runtime | JVM | 21 (LTS) | Virtual threads — 1M concurrent threads without Reactor complexity |
| Framework | Spring Boot | 3.3.x | Mature, battle-tested, best Kotlin support |
| Build | Gradle (Kotlin DSL) | 8.x | Type-safe build scripts; better than Maven for Kotlin |
| API | Spring MVC (not WebFlux) | — | Virtual threads give same I/O throughput; simpler testing |

### Persistence

| Component | Technology | Rationale |
|---|---|---|
| Primary DB | PostgreSQL 16 | ACID, JSONB for provider metadata, advisory locks |
| ORM | Spring Data JPA + Hibernate | Clean repositories, Kotlin-friendly |
| Migrations | Flyway | Version-controlled, repeatable, additive-only enforced by convention |
| Connection pool | HikariCP | Default in Spring Boot; fastest for PostgreSQL |

### Messaging & Events

| Component | Technology | Rationale |
|---|---|---|
| Event streaming | Apache Kafka | Durable, replayable, partition by merchantId for ordering |
| Schema management | JSON Schema (v1 envelope pattern) | Avro adds Confluent Schema Registry dependency — JSON schema is simpler and evolvable |
| Outbox processor | Polling scheduler (upgradeable to Debezium CDC) | CDC requires more infra; polling is sufficient and simpler to start |

### Caching & Real-time

| Component | Technology | Rationale |
|---|---|---|
| Cache + Lock store | Redis 7 (Lettuce client) | TTL-native, atomic Lua, pub/sub for WebSocket fan-out |
| Real-time push | Spring WebSocket (STOMP) | Merchant clients subscribe to payment status; no polling |
| Idempotency | Redis primary + PostgreSQL fallback | Redis for speed; DB is ground truth after TTL |

### Resilience

| Component | Technology | Rationale |
|---|---|---|
| Circuit Breaker | Resilience4j | Per-provider; prevents cascade failure |
| Retry | Resilience4j Retry | Exponential backoff; retryable error classification |
| Rate Limiter | Resilience4j + Redis sliding window | Both local and distributed rate limiting |
| Bulkhead | Resilience4j Bulkhead | Limit concurrent calls per provider |
| Timeout | Resilience4j TimeLimiter | Hard timeout on provider HTTP/gRPC calls |

### Internal Provider Communication

| Component | Technology | Rationale |
|---|---|---|
| Provider connectors | Interface + adapters (HTTP/gRPC) | Strategy pattern; swap provider impl without changing orchestrator |
| Provider A (CARD) | HTTP adapter (simulated) | Real would be gRPC or REST to external PSP |
| Provider B (UPI) | HTTP adapter (simulated) | Same pattern |

### Service Discovery

| Component | Technology | Rationale |
|---|---|---|
| Phase 1 service discovery | Static config / environment-based provider endpoints | This assignment is a modular monolith with simulated/external providers; Eureka is unnecessary operational weight |
| Kubernetes service discovery (future deployment) | Kubernetes Services + DNS | Preferred when deployed on K8s; simpler than running Eureka for Spring-only discovery |
| Eureka / Consul (future non-K8s deployment) | Optional | Useful only after splitting into independently deployed internal services outside K8s |

**Decision:** Do not add Eureka in Phase 1. Service discovery becomes useful when Payment, Routing, Ledger, Notification, Webhook, and Auth are deployed as separate services. Until then, provider endpoints should be configured through `application.yml`, environment variables, or a config server.

### Observability

| Component | Technology | Rationale |
|---|---|---|
| Metrics | Micrometer + Prometheus | Industry standard; custom payment metrics |
| Tracing | OpenTelemetry + Jaeger (Zipkin-compatible exporter optional) | Distributed trace across SAGA steps; backend can be swapped without changing instrumentation |
| Logging | SLF4J + Logback (structured JSON) | Machine-parseable; correlate by traceId |
| Health checks | Spring Actuator | Liveness + readiness probes for K8s |

### Testing

| Component | Technology | Rationale |
|---|---|---|
| Unit tests | JUnit 5 + Mockito-Kotlin | Standard |
| Integration tests | Testcontainers (Postgres + Redis + Kafka) | Real infra, not mocks |
| API tests | Spring MockMvc + RestAssured | Controller layer |
| Contract tests | Spring Cloud Contract | Provider/consumer contracts for API versioning |
| Load tests | k6 (optional) | Performance validation |

---

## 4. API Design Layer

### 4.1 REST API (External — Merchant-Facing)

**Versioning strategy:** URL path versioning (`/api/v1/`, `/api/v2/`)  
**Why not header versioning:** URL versioning is visible, cacheable, bookmarkable, and plays well with API gateways.

#### Endpoints

```
POST   /api/v1/payments              → Create payment
GET    /api/v1/payments/{id}         → Fetch payment by ID
GET    /api/v1/payments/{id}/status  → Lightweight status check
POST   /api/v1/payments/{id}/cancel  → Cancel payment
GET    /api/v1/payments              → List payments (paginated, filtered)
GET    /api/v1/health                → Health probe (Spring Actuator)
GET    /api/v1/metrics               → Prometheus metrics (Actuator)
```

#### Create Payment Request

```json
POST /api/v1/payments
Headers:
  X-Idempotency-Key: <UUID>          // REQUIRED — client-generated
  X-API-Key: <merchantApiKey>        // REQUIRED
  X-Merchant-Id: <merchantId>        // OPTIONAL correlation hint; server derives merchant from API key

Body:
{
  "amount": {
    "value": 10000,                  // In minor units (paise, cents)
    "currency": "INR"
  },
  "paymentMethod": {
    "type": "CARD",                  // CARD | UPI | WALLET | NET_BANKING
    "card": {
      "number": "4111111111111111",
      "expiryMonth": "12",
      "expiryYear": "2027",
      "cvv": "123",
      "holderName": "John Doe"
    }
  },
  "merchantReference": "ORDER-12345", // Merchant's own order ID
  "description": "Order payment",
  "metadata": {                       // Free-form, stored as JSONB
    "orderId": "12345",
    "customerId": "cust_abc"
  },
  "returnUrl": "https://merchant.com/payment/return",
  "webhookUrl": "https://merchant.com/webhook/payment"
}
```

#### Create Payment Response

```json
HTTP 201 Created
{
  "paymentId": "pay_01J9XYZ...",     // Yuno-generated ULID (not UUID)
  "status": "INITIATED",
  "amount": {
    "value": 10000,
    "currency": "INR"
  },
  "paymentMethod": {
    "type": "CARD",
    "maskedNumber": "411111******1111"
  },
  "provider": "PROVIDER_A",          // Which provider was routed to
  "merchantReference": "ORDER-12345",
  "createdAt": "2026-05-12T10:00:00Z",
  "updatedAt": "2026-05-12T10:00:00Z",
  "_links": {                        // HATEOAS — future-proof
    "self": "/api/v1/payments/pay_01J9XYZ",
    "status": "/api/v1/payments/pay_01J9XYZ/status",
    "cancel": "/api/v1/payments/pay_01J9XYZ/cancel"
  }
}
```

#### Error Response (RFC 7807 — Problem Details)

```json
HTTP 422 Unprocessable Entity
{
  "type": "https://api.yuno.com/errors/payment/insufficient-funds",
  "title": "Payment declined",
  "status": 422,
  "detail": "Card issuer declined the transaction",
  "instance": "/api/v1/payments/pay_01J9XYZ",
  "errorCode": "CARD_DECLINED",
  "traceId": "abc123def456",
  "timestamp": "2026-05-12T10:00:00Z"
}
```

**Why RFC 7807:** Standardized error schema that clients can parse consistently. Every HTTP framework understands it. Future-proof.

#### Idempotency Behavior

```
First request with X-Idempotency-Key: key-123 → 201 Created (payment created)
Same request again with X-Idempotency-Key: key-123 → 200 OK (same response, no charge)
Different request with X-Idempotency-Key: key-123 → 409 Conflict (same key, different body)
```

### 4.2 Internal Provider Interface (gRPC — future-ready)

Currently providers are simulated as HTTP adapters. The interface is designed for gRPC so we can swap to real gRPC providers without changing the orchestrator.

```protobuf
// provider_service.proto
syntax = "proto3";
package yuno.provider.v1;

service ProviderService {
  rpc ChargePayment(ChargeRequest) returns (ChargeResponse);
  rpc RefundPayment(RefundRequest) returns (RefundResponse);
  rpc GetPaymentStatus(StatusRequest) returns (StatusResponse);
}

message ChargeRequest {
  string idempotency_key = 1;
  string payment_id = 2;
  int64 amount_minor_units = 3;
  string currency = 4;
  PaymentMethodDetails method = 5;
  map<string, string> metadata = 6;
}

message ChargeResponse {
  string provider_transaction_id = 1;
  ProviderStatus status = 2;
  string error_code = 3;
  string error_message = 4;
  google.protobuf.Timestamp processed_at = 5;
}

enum ProviderStatus {
  PROVIDER_STATUS_UNSPECIFIED = 0;
  AUTHORIZED = 1;
  DECLINED = 2;
  PENDING = 3;
  ERROR = 4;
}
```

The Kotlin `ProviderConnector` interface mirrors this:

```kotlin
interface ProviderConnector {
    val providerName: String
    val supportedPaymentMethods: Set<PaymentMethodType>
    fun charge(request: ProviderChargeRequest): ProviderChargeResponse
    fun refund(request: ProviderRefundRequest): ProviderRefundResponse
    fun getStatus(paymentId: String): ProviderStatusResponse
}
```

### 4.3 WebSocket API (Real-time Status Push)

Merchants subscribe to payment status updates instead of polling.

```
WSS /ws/payments
STOMP protocol over WebSocket

Subscribe:  /topic/payments/{paymentId}
Receive:    PaymentStatusUpdateEvent (JSON)
```

```json
// Message received on /topic/payments/pay_01J9XYZ
{
  "paymentId": "pay_01J9XYZ",
  "previousStatus": "PROCESSING",
  "currentStatus": "CAPTURED",
  "timestamp": "2026-05-12T10:00:05Z",
  "providerTransactionId": "prov_txn_abc123"
}
```

**How it works:**
1. Client connects WebSocket → subscribes to `/topic/payments/{id}`
2. Orchestrator updates payment status in DB
3. Orchestrator publishes to Redis pub/sub channel `payment-status:{id}`
4. WebSocket handler subscribes to Redis channel → pushes to STOMP topic
5. Client receives update without polling

**Why Redis pub/sub here (not Kafka)?**  
WebSocket connections live on a specific app instance. Redis pub/sub is the lightweight fan-out mechanism between app instances. Kafka events go to downstream consumers (notification, ledger) — different concern.

### 4.4 Webhook (Merchant Push Notification)

For merchants that prefer webhook over WebSocket:

```
POST {merchant.webhookUrl}
Headers:
  X-Yuno-Signature: HMAC-SHA256(secret, body)
  X-Yuno-Timestamp: 1715510400
  X-Yuno-Event-Type: payment.succeeded

Body:
{
  "eventId": "evt_01J9XYZ",
  "eventType": "payment.succeeded",
  "paymentId": "pay_01J9XYZ",
  "data": { ...full payment object... }
}
```

Webhooks are fired by the Kafka consumer — not inline in the request path. Retry logic is separate from payment retry logic.

---

## 5. Event & Messaging Architecture

### 5.1 Kafka Topic Design

```
Topic naming: {domain}.{version}.{event}

payment.v1.initiated
payment.v1.processing
payment.v1.authorized
payment.v1.captured
payment.v1.failed
payment.v1.cancelled
payment.v1.status_changed    ← catch-all for downstream consumers
```

**Partitioning:** By `merchantId` — guarantees ordering of events for the same merchant. Prevents split-brain across partitions.

**Retention:** 7 days (configurable). Enables replay for reconciliation and analytics backfill.

### 5.2 Event Envelope (Backward Compatible)

Every event follows this JSON envelope:

```json
{
  "eventId": "evt_01J9XYZ",
  "eventVersion": "1.0",
  "eventType": "payment.succeeded",
  "occurredAt": "2026-05-12T10:00:05Z",
  "traceId": "abc123",
  "producerService": "payment-orchestration",
  "schemaVersion": "v1",
  "payload": {
    ...event-specific fields...
  }
}
```

**Why envelope pattern:**  
- `eventVersion` lets consumers ignore events they don't understand  
- `schemaVersion` enables schema evolution without breaking consumers  
- `traceId` propagates distributed trace across all services  

### 5.3 Transactional Outbox Pattern

**Problem it solves:** Preventing the dual-write problem between DB and Kafka.

```
WITHOUT OUTBOX:
  UPDATE payments SET status='SUCCESS'   ← DB write
  PUBLISH to Kafka                        ← separate operation
  → App crash between these two = event lost forever

WITH OUTBOX:
  BEGIN TRANSACTION
    UPDATE payments SET status='SUCCESS'
    INSERT INTO outbox_events (topic, key, payload, published=false)
  COMMIT
  → Outbox processor: SELECT unpublished → publish → mark published
  → Guaranteed: if DB commit succeeded, event WILL eventually be published
```

#### Outbox Processor

```
Scheduler runs every 100ms:
  1. SELECT * FROM outbox_events WHERE published=false AND created_at < now()-5s ORDER BY id LIMIT 100
  2. FOR each event: Kafka.send(topic, key, payload)
  3. UPDATE outbox_events SET published=true WHERE id IN (...)
  
  Failure handling:
    - Kafka unavailable → events stay unpublished → retry next cycle
    - No events lost ever
    
  Future upgrade:
    - Replace scheduler with Debezium CDC (PostgreSQL WAL) for zero-lag publishing
    - No code change needed — just add Debezium; disable scheduler
```

### 5.4 Intra-Service Events vs Durable Events

Use two event paths intentionally:

1. **Local Spring application events** for in-process reactions inside the same application.
   - Example: `PaymentStatusChangedApplicationEvent`
   - Use with `@TransactionalEventListener(phase = AFTER_COMMIT)` so listeners only run after DB commit.
   - Good for cache refresh, WebSocket push, metrics enrichment, and local audit hooks.
   - Not a source of truth; events are lost if the process crashes before delivery.

2. **Outbox + Kafka integration events** for durable cross-service communication.
   - Example: `payment.v1.captured`, `payment.v1.failed`, `payment.v1.status_changed`
   - Used by Notification, Ledger, Analytics, Webhook, and future services.
   - Source of truth for async side effects because events survive process crashes.

**Rule:** Any effect that must happen eventually goes through the outbox. Spring events are only a convenience for local decoupling.

### 5.5 Event Consumers (Side Effects via Choreography)

```kotlin
// Each consumer is independent, decoupled, restartable
@KafkaListener(topics = ["payment.v1.succeeded"], groupId = "notification-service")
fun onPaymentSucceeded(event: PaymentSucceededEvent) {
    notificationService.sendConfirmation(event)
}

@KafkaListener(topics = ["payment.v1.failed"], groupId = "notification-service")
fun onPaymentFailed(event: PaymentFailedEvent) {
    notificationService.sendFailureNotification(event)
}
```

Consumers are in the same service for this assignment. In production they'd be separate deployable units.

---

## 6. Real-time Layer

### 6.1 WebSocket + Redis Pub/Sub Architecture

```
App Instance 1            App Instance 2
│ Client A connected       │ Client B connected
│ to payment pay_001       │ to payment pay_001
│                          │
│ ◄── Redis Sub ──────────►│
│   channel: payment-status:pay_001
│                          │
│                     Orchestrator updates pay_001
│                     → publishes to Redis channel
│                          │
│◄ receives message        │◄ receives message
│  pushes to Client A      │  pushes to Client B
```

This handles horizontal scaling — any app instance can push to any connected client.

### 6.2 STOMP Configuration

```kotlin
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic")        // In-memory broker (upgradeable to RabbitMQ)
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/payments")
            .setAllowedOriginPatterns("*")
            .withSockJS()                          // Fallback for non-WebSocket clients
    }
}
```

**Future upgrade:** Replace in-memory STOMP broker with RabbitMQ STOMP broker for clustered WebSocket support.

---

## 7. Data Model & Schema

### 7.1 Entity Relationship

```
merchants (1) ──────────────── (N) payments
payments (1) ────────────────── (N) payment_attempts
payments (1) ────────────────── (N) payment_status_history
payments (1) ────────────────── (1) idempotency_keys
outbox_events (standalone)
providers (standalone — routing config)
routing_rules (standalone — routing logic)
```

### 7.2 PostgreSQL Schema (Flyway Migrations)

#### V0__create_merchants_and_api_keys_tables.sql

```sql
CREATE TABLE merchants (
    id                      VARCHAR(255)    NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    status                  VARCHAR(50)     NOT NULL,          -- ACTIVE | SUSPENDED | DISABLED
    environment             VARCHAR(20)     NOT NULL,          -- TEST | LIVE
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_merchants PRIMARY KEY (id)
);

CREATE TABLE merchant_api_keys (
    id                      VARCHAR(26)     NOT NULL,          -- ULID
    merchant_id             VARCHAR(255)    NOT NULL,
    key_prefix              VARCHAR(16)     NOT NULL,          -- First chars for lookup/debug only
    key_hash                VARCHAR(255)    NOT NULL,          -- Argon2/bcrypt hash; never store raw key
    environment             VARCHAR(20)     NOT NULL,          -- TEST | LIVE
    status                  VARCHAR(50)     NOT NULL,          -- ACTIVE | REVOKED
    allowed_payment_methods JSONB,
    rate_limit_tier         VARCHAR(50),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ,

    CONSTRAINT pk_merchant_api_keys PRIMARY KEY (id),
    CONSTRAINT fk_api_keys_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
);

CREATE UNIQUE INDEX uq_merchant_api_keys_prefix ON merchant_api_keys (key_prefix);
CREATE INDEX idx_merchant_api_keys_merchant ON merchant_api_keys (merchant_id);
```

#### V1__create_payments_table.sql

```sql
CREATE TABLE payments (
    id                      VARCHAR(26)     NOT NULL,         -- ULID
    merchant_id             VARCHAR(255)    NOT NULL,
    idempotency_key         VARCHAR(255)    NOT NULL,
    merchant_reference      VARCHAR(255),
    status                  VARCHAR(50)     NOT NULL,          -- Enum as string (evolvable)
    payment_method_type     VARCHAR(50)     NOT NULL,          -- CARD | UPI | WALLET
    amount_value            BIGINT          NOT NULL,          -- Minor units
    amount_currency         VARCHAR(3)      NOT NULL,          -- ISO 4217
    provider_name           VARCHAR(100),
    provider_transaction_id VARCHAR(255),
    description             VARCHAR(500),
    webhook_url             VARCHAR(2048),
    return_url              VARCHAR(2048),
    metadata                JSONB,                             -- Free-form merchant data
    failure_reason          VARCHAR(1000),
    failure_error_code      VARCHAR(100),
    retry_count             INT             NOT NULL DEFAULT 0,
    version                 BIGINT          NOT NULL DEFAULT 0, -- Optimistic lock
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT uq_payments_idempotency UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_payments_merchant_id ON payments (merchant_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_merchant_reference ON payments (merchant_reference);
CREATE INDEX idx_payments_created_at ON payments (created_at);
CREATE INDEX idx_payments_metadata_gin ON payments USING GIN (metadata);
```

#### V2__create_payment_attempts_table.sql

```sql
CREATE TABLE payment_attempts (
    id                      VARCHAR(26)     NOT NULL,          -- ULID
    payment_id              VARCHAR(26)     NOT NULL,
    attempt_number          INT             NOT NULL,
    provider_name           VARCHAR(100)    NOT NULL,
    provider_request        JSONB,                             -- What we sent
    provider_response       JSONB,                             -- What we received
    status                  VARCHAR(50)     NOT NULL,
    error_code              VARCHAR(100),
    error_message           VARCHAR(1000),
    is_retryable            BOOLEAN         NOT NULL DEFAULT FALSE,
    duration_ms             BIGINT,                            -- Provider call latency
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_payment_attempts PRIMARY KEY (id),
    CONSTRAINT fk_payment_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts (payment_id);
```

#### V3__create_payment_status_history_table.sql

```sql
-- Append-only audit log. NEVER delete or update rows here.
CREATE TABLE payment_status_history (
    id                      BIGSERIAL       NOT NULL,
    payment_id              VARCHAR(26)     NOT NULL,
    from_status             VARCHAR(50),
    to_status               VARCHAR(50)     NOT NULL,
    reason                  VARCHAR(1000),
    triggered_by            VARCHAR(100),                      -- ORCHESTRATOR | WEBHOOK | MANUAL
    metadata                JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_payment_status_history PRIMARY KEY (id)
);

CREATE INDEX idx_status_history_payment_id ON payment_status_history (payment_id);
```

#### V4__create_outbox_events_table.sql

```sql
CREATE TABLE outbox_events (
    id                      BIGSERIAL       NOT NULL,
    aggregate_id            VARCHAR(26)     NOT NULL,          -- paymentId
    aggregate_type          VARCHAR(100)    NOT NULL,          -- "Payment"
    event_type              VARCHAR(200)    NOT NULL,          -- "payment.v1.succeeded"
    kafka_topic             VARCHAR(500)    NOT NULL,
    kafka_key               VARCHAR(255)    NOT NULL,          -- merchantId for partitioning
    payload                 JSONB           NOT NULL,
    published               BOOLEAN         NOT NULL DEFAULT FALSE,
    published_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published = FALSE;
```

#### V5__create_idempotency_keys_table.sql

```sql
-- Fallback store when Redis TTL expires but we still need the result
CREATE TABLE idempotency_keys (
    id                      BIGSERIAL       NOT NULL,
    merchant_id             VARCHAR(255)    NOT NULL,
    idempotency_key         VARCHAR(255)    NOT NULL,
    request_hash            VARCHAR(64)     NOT NULL,           -- SHA-256 of request body
    response_status         INT             NOT NULL,
    response_body           JSONB           NOT NULL,
    payment_id              VARCHAR(26),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_keys UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);
```

#### V6__create_providers_table.sql

```sql
CREATE TABLE providers (
    name                    VARCHAR(100)    NOT NULL,
    display_name            VARCHAR(255)    NOT NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    max_retry_count         INT             NOT NULL DEFAULT 3,
    timeout_ms              INT             NOT NULL DEFAULT 5000,
    config                  JSONB,                              -- Provider-specific config
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_providers PRIMARY KEY (name)
);

INSERT INTO providers (name, display_name, max_retry_count, timeout_ms) VALUES
    ('PROVIDER_A', 'Provider A (Card)', 3, 5000),
    ('PROVIDER_B', 'Provider B (UPI)', 3, 8000);
```

#### V7__create_routing_rules_table.sql

```sql
CREATE TABLE routing_rules (
    id                      SERIAL          NOT NULL,
    payment_method_type     VARCHAR(50)     NOT NULL,
    provider_name           VARCHAR(100)    NOT NULL,
    priority                INT             NOT NULL DEFAULT 1,
    conditions              JSONB,                              -- e.g. {"currency": "INR", "min_amount": 0}
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_routing_rules PRIMARY KEY (id),
    CONSTRAINT fk_routing_rules_provider FOREIGN KEY (provider_name) REFERENCES providers (name)
);

INSERT INTO routing_rules (payment_method_type, provider_name, priority) VALUES
    ('CARD', 'PROVIDER_A', 1),
    ('UPI',  'PROVIDER_B', 1),
    ('CARD', 'PROVIDER_B', 2),   -- Failover: CARD can fall back to Provider B
    ('UPI',  'PROVIDER_A', 2);   -- Failover: UPI can fall back to Provider A
```

### 7.3 Why ULID Instead of UUID

**ULID (Universally Unique Lexicographically Sortable Identifier)**  
- Sortable by creation time (first 10 chars = timestamp)  
- Better B-tree index performance than random UUID  
- Still globally unique, URL-safe  
- No sequential ID leak (vs auto-increment BIGINT)  

Format: `pay_01J9XYZABC123DEF456GH78`

---

## 8. Core Components

### 8.1 Payment State Machine

```
                    ┌─────────────────────────────────────────┐
                    │            STATE MACHINE                 │
                    │                                          │
  INITIATED ──────► PROCESSING ──────► AUTHORIZED ──────► CAPTURED
      │                 │                  │
      │                 │ (provider fail)   │ (capture fail)
      │                 ▼                  ▼
      │           RETRY_PENDING        FAILED
      │                 │
      │           (max retries)
      │                 ▼
      │            FAILED
      │
      ├──────────────────────────────────► CANCELLED  (before PROCESSING)
      │
      │              (any terminal state can trigger)
      └──────────────────────────────────► REFUNDED   (post-capture)
```

**State transition rules:**
- Only valid transitions are allowed (throws `InvalidStateTransitionException`)
- Every transition is logged to `payment_status_history`
- Terminal states: `CAPTURED`, `FAILED`, `CANCELLED`, `REFUNDED`
- State machine is a first-class domain component, not scattered `if/else` logic in the orchestrator
- Spring State Machine library is not required in Phase 1; a small explicit transition table is easier to test and audit
- Each successful transition creates both a status-history row and an outbox event in the same DB transaction

```kotlin
enum class PaymentStatus {
    INITIATED, PROCESSING, AUTHORIZED, CAPTURED,
    RETRY_PENDING, FAILED, CANCELLED, REFUNDED;

    fun canTransitionTo(target: PaymentStatus): Boolean =
        validTransitions[this]?.contains(target) ?: false

    companion object {
        val validTransitions = mapOf(
            INITIATED       to setOf(PROCESSING, CANCELLED),
            PROCESSING      to setOf(AUTHORIZED, RETRY_PENDING, FAILED),
            AUTHORIZED      to setOf(CAPTURED, FAILED),
            RETRY_PENDING   to setOf(PROCESSING, FAILED),
            CAPTURED        to setOf(REFUNDED),
            FAILED          to emptySet(),
            CANCELLED       to emptySet(),
            REFUNDED        to emptySet()
        )
        
        val terminalStatuses = setOf(CAPTURED, FAILED, CANCELLED, REFUNDED)
    }
}
```

### 8.2 Routing Engine

```kotlin
@Component
class RoutingEngine(
    private val routingRuleRepository: RoutingRuleRepository
) {
    // Returns ordered list of providers to try (primary + failovers)
    fun resolveProviderChain(
        paymentMethodType: PaymentMethodType,
        amount: Money,
        currency: String
    ): List<String> {
        return routingRuleRepository
            .findActiveRules(paymentMethodType)
            .filter { it.matches(amount, currency) }
            .sortedBy { it.priority }
            .map { it.providerName }
    }
}
```

Rules are database-driven — changing routing logic doesn't require a deployment.

### 8.3 Idempotency Service (Redis + Lua)

**Why Lua scripts?** Atomic check-and-set in Redis. Without Lua, two concurrent requests with the same key could both miss the cache and both create payments (TOCTOU race condition).

```lua
-- idempotency_check_and_set.lua
-- KEYS[1] = idempotency key in Redis
-- ARGV[1] = request hash
-- ARGV[2] = response JSON  
-- ARGV[3] = TTL in seconds
-- Returns: "HIT" + cached response | "MISS" | "CONFLICT" + cached hash

local existing = redis.call('GET', KEYS[1])
if existing then
    local data = cjson.decode(existing)
    if data.requestHash == ARGV[1] then
        return {'HIT', existing}
    else
        return {'CONFLICT', data.requestHash}
    end
end

local entry = cjson.encode({
    requestHash = ARGV[1],
    response = cjson.decode(ARGV[2])
})
redis.call('SETEX', KEYS[1], ARGV[3], entry)
return {'MISS'}
```

### 8.4 Orchestration Service (SAGA Steps)

```kotlin
@Service
class PaymentOrchestrationService(
    private val idempotencyService: IdempotencyService,
    private val paymentRepository: PaymentRepository,
    private val routingEngine: RoutingEngine,
    private val retryCoordinator: RetryCoordinator,
    private val stateMachine: PaymentStateMachine,
    private val outboxPublisher: OutboxPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val redisPublisher: RedisPaymentStatusPublisher
) {
    fun createPayment(request: CreatePaymentRequest, merchantId: String): PaymentResponse {

        // Step 1: Idempotency check (atomic, outside main transaction)
        idempotencyService.checkAndLock(request.idempotencyKey, merchantId, request)
            ?.let { return it }  // Return cached response if seen before

        // Step 2: Short DB transaction - create payment record (INITIATED) + outbox event
        val payment = createInitiatedPayment(request, merchantId)

        // Step 3: Resolve provider chain
        val providerChain = routingEngine.resolveProviderChain(
            request.paymentMethodType, request.amount, request.currency
        )

        // Step 4: Execute provider call outside DB transaction
        val result = retryCoordinator.executeWithFailover(payment, providerChain)

        // Step 5: Short DB transaction - transition state + history + outbox event
        val finalPayment = persistProviderResult(payment, result)

        // Step 6: Local Spring event + real-time push (best-effort, after durable state exists)
        applicationEventPublisher.publishEvent(PaymentStatusChangedApplicationEvent(finalPayment))
        redisPublisher.publishStatusChange(finalPayment)

        // Step 7: Store idempotency result
        val response = finalPayment.toResponse()
        idempotencyService.storeResult(request.idempotencyKey, merchantId, request, response)

        return response
    }

    fun createInitiatedPayment(request: CreatePaymentRequest, merchantId: String): Payment {
        return transactionTemplate.execute {
            val payment = paymentRepository.save(request.toPayment(merchantId))
            outboxPublisher.publish(payment, PaymentEventType.INITIATED)
            payment
        }!!
    }

    fun persistProviderResult(payment: Payment, result: ProviderResult): Payment {
        return transactionTemplate.execute {
            val finalPayment = stateMachine.transition(payment, result.toStatus(), result)
            outboxPublisher.publish(finalPayment, result.toEventType())
            finalPayment
        }!!
    }
}
```

**Transaction boundary rule:** Never hold a database transaction open while calling an external provider. The orchestrator uses short transactions for local state changes, releases the DB connection, performs provider I/O, then opens another short transaction to persist the result.

### 8.5 Retry Coordinator

```kotlin
@Component
class RetryCoordinator(
    private val providerRegistry: ProviderConnectorRegistry,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry
) {
    fun executeWithFailover(
        payment: Payment,
        providerChain: List<String>
    ): ProviderResult {
        var lastError: ProviderError? = null

        for (providerName in providerChain) {
            val connector = providerRegistry.get(providerName)

            val attempt = payment.newAttempt(providerName)

            try {
                val result = executeWithResilience(providerName) {
                    connector.charge(payment.toChargeRequest(attempt.idempotencyKey))
                }

                attempt.recordSuccess(result)
                return ProviderResult.success(providerName, result)

            } catch (e: NonRetryableException) {
                // Card declined, invalid card — don't try next provider
                attempt.recordFailure(e, retryable = false)
                return ProviderResult.failure(providerName, e, retryable = false)

            } catch (e: RetryableException) {
                // Timeout, 5xx — try next provider in chain
                attempt.recordFailure(e, retryable = true)
                lastError = ProviderError(providerName, e)
                continue
            }
        }

        // Exhausted all providers
        return ProviderResult.failure("ALL_PROVIDERS", lastError!!.exception, retryable = false)
    }

    private fun <T> executeWithResilience(providerName: String, block: () -> T): T {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(providerName)
        val retry = retryRegistry.retry(providerName)
        val timeLimiter = TimeLimiter.ofDefaults()

        return CircuitBreaker.decorateSupplier(circuitBreaker) {
            Retry.decorateSupplier(retry) {
                block()
            }.get()
        }.get()
    }
}
```

**Retryable vs Non-retryable error classification:**

| Error Type | Retryable | Action |
|---|---|---|
| Network timeout | Yes | Retry same provider (with backoff) |
| HTTP 5xx | Yes | Retry same provider, then failover |
| HTTP 429 (rate limit) | Yes | Backoff + failover |
| HTTP 402 (card declined) | No | Stop immediately |
| HTTP 400 (invalid request) | No | Stop immediately |
| Circuit breaker open | Yes | Skip to next provider |

---

## 9. Redis Strategy

### Key Naming Convention

```
idempotency:{merchantId}:{idempotencyKey}    TTL: 24h
lock:payment:{paymentId}                     TTL: 30s  (distributed lock)
payment:status:{paymentId}                   TTL: 5m   (status cache)
ratelimit:{merchantId}:payments              TTL: 1m   (sliding window counter)
payment-status:{paymentId}                   (pub/sub channel — no key stored)
```

### Redis Usage Map

| Purpose | Pattern | TTL | Lua? |
|---|---|---|---|
| Idempotency | Check-and-set | 24h | Yes — atomic |
| Distributed lock | SET NX PX | 30s | No — SETNX is atomic |
| Payment status cache | GET/SET | 5min | No |
| Rate limiting | INCR + EXPIRE | 1min | Yes — atomic sliding window |
| WebSocket fan-out | PUBLISH/SUBSCRIBE | N/A | No |

### Distributed Lock (Prevent Concurrent Processing)

```kotlin
// Prevents two concurrent requests for the same payment from both reaching the provider
fun <T> withPaymentLock(paymentId: String, block: () -> T): T {
    val lockKey = "lock:payment:$paymentId"
    val token = UUID.randomUUID().toString()
    
    val acquired = redis.opsForValue()
        .setIfAbsent(lockKey, token, 30, TimeUnit.SECONDS)
    
    if (acquired != true) throw ConcurrentPaymentProcessingException(paymentId)
    
    try {
        return block()
    } finally {
        // Lua script — only release if we own the lock (prevents releasing another thread's lock)
        releaseLockScript.execute(lockKey, token)
    }
}
```

---

## 10. Resilience & Fault Tolerance

### Per-Provider Circuit Breaker Config

```yaml
resilience4j:
  circuitbreaker:
    instances:
      PROVIDER_A:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        failureRateThreshold: 50          # Open after 50% failures in window
        waitDurationInOpenState: 30s      # Stay open for 30s before half-open
        permittedNumberOfCallsInHalfOpenState: 3
      PROVIDER_B:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s

  retry:
    instances:
      PROVIDER_A:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2   # 500ms, 1000ms, 2000ms
        retryExceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.HttpServerErrorException

  timelimiter:
    instances:
      PROVIDER_A:
        timeoutDuration: 5s
      PROVIDER_B:
        timeoutDuration: 8s
```

### Reconciliation Job (Handles Unknown States)

```kotlin
// Runs every 5 minutes — finds payments stuck in PROCESSING
@Scheduled(fixedDelay = 300_000)
fun reconcileStuckPayments() {
    val stuckPayments = paymentRepository.findByStatusAndUpdatedAtBefore(
        status = PaymentStatus.PROCESSING,
        cutoff = Instant.now().minus(10, ChronoUnit.MINUTES)
    )

    for (payment in stuckPayments) {
        val providerStatus = providerConnector.getStatus(payment.providerTransactionId)
        // Update payment based on actual provider status
        stateMachine.transition(payment, providerStatus.toPaymentStatus())
    }
}
```

---

## 11. Backward Compatibility Strategy

### API Versioning
- New major version `/api/v2/` when breaking changes required
- Old version maintained for minimum 6 months
- Deprecation header on old versions: `Deprecation: true`, `Sunset: <date>`

### Database Migrations (Additive-Only Convention)
```
✅ Add new columns with DEFAULT or NULL
✅ Add new tables
✅ Add new indexes
✅ Add new enum values
❌ Never drop columns (mark as deprecated in comments instead)
❌ Never rename columns (add new column, migrate data, deprecate old)
❌ Never change column types (add new column with new type)
```

### Kafka Schema Evolution
- Envelope pattern lets consumers ignore unknown fields
- `eventVersion` in envelope lets consumers skip unsupported versions
- New fields are always optional (never required in new schema versions)
- Never remove fields — mark as deprecated in schema docs

### Provider Interface Evolution
- `ProviderConnector` interface is additive — new methods have default implementations
- Provider config stored as JSONB — no migration needed for new config keys

### Feature Flags
```kotlin
@ConditionalOnProperty(prefix = "features", name = ["new-routing-engine"], havingValue = "true")
@Component
class NewRoutingEngine : RoutingEngine { ... }
```

Dark launches: new behavior gated by feature flag, enabled per-merchant via config.

---

## 12. Observability

### Custom Metrics (Micrometer)

```kotlin
// Business metrics — what matters in production
Counter.builder("payments.created")
    .tag("payment_method", payment.methodType)
    .tag("merchant_id", payment.merchantId)
    .register(meterRegistry).increment()

Timer.builder("payment.provider.latency")
    .tag("provider", providerName)
    .tag("status", result.status)
    .register(meterRegistry)
    .record(duration)

Gauge.builder("payments.pending", paymentRepository) { repo ->
    repo.countByStatus(PaymentStatus.PROCESSING).toDouble()
}.register(meterRegistry)

Counter.builder("payments.idempotency.hits")
    .description("Duplicate requests caught by idempotency")
    .register(meterRegistry).increment()
```

### Structured Logging

```json
{
  "timestamp": "2026-05-12T10:00:05Z",
  "level": "INFO",
  "traceId": "abc123def456",
  "spanId": "fed321",
  "merchantId": "merchant_xyz",
  "paymentId": "pay_01J9XYZ",
  "event": "payment_status_changed",
  "fromStatus": "PROCESSING",
  "toStatus": "CAPTURED",
  "provider": "PROVIDER_A",
  "durationMs": 342,
  "message": "Payment captured successfully"
}
```

### OpenTelemetry Trace Propagation

Every payment carries a `traceId` from creation through all SAGA steps, provider calls, Kafka events, and consumers. One trace shows the full lifecycle in Jaeger.

**Tracing backend decision:** Instrument with OpenTelemetry, not a backend-specific API. Jaeger is the default local backend because it is easy to run in Docker and works well for distributed traces. Zipkin can be used instead through an OpenTelemetry exporter if the deployment already standardizes on Zipkin.

**Trace propagation requirements:**
- Accept or generate W3C `traceparent` at the API boundary.
- Propagate trace context into provider HTTP/gRPC calls.
- Put `traceId` and `spanId` in structured logs.
- Include `traceId` in outbox payloads and Kafka headers.
- Link async consumer spans back to the original payment trace.

---

## 13. Security

### Authentication
- Merchant API: API key (`X-API-Key`) validated via Spring Security filter before any payment endpoint is reached
- Merchant identity is derived from the authenticated API key; never trust request body or `X-Merchant-Id` as authority
- `X-Merchant-Id` may be accepted only as an optional correlation/debug header and must match the authenticated merchant if present
- API keys are hashed at rest, scoped by environment (`test`/`live`), status, allowed payment methods, and rate-limit tier
- Phase 1 uses API keys for merchant-to-platform authentication; OAuth2/OIDC can be added later for dashboard users and delegated access
- Internal APIs: mTLS (service-to-service)
- WebSocket: Same API key in STOMP CONNECT frame headers; subscriptions are authorized per merchant/payment ID

### Sensitive Data
- Card numbers stored masked only (`411111******1111`) — never full PAN
- CVV never stored — only used in-flight for provider call
- Provider request/response in `payment_attempts.provider_request/response` — strip sensitive fields before persisting

### OWASP Top 10 Mitigations
- SQL injection: JPA parameterized queries — no string interpolation in queries
- Rate limiting: Redis sliding window per merchant
- Input validation: Bean Validation (`@NotNull`, `@Size`, custom validators)
- HTTPS only: TLS termination at load balancer
- Secrets: Environment variables / Vault (no secrets in code or config files)

---

## 14. Project Structure

```
payment-orchestration/
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml              ← Postgres, Redis, Kafka for local dev
├── Dockerfile
├── README.md
│
├── src/
│   ├── main/
│   │   ├── kotlin/com/yuno/payment/
│   │   │   ├── PaymentApplication.kt
│   │   │   │
│   │   │   ├── api/                        ← API layer (controllers, DTOs)
│   │   │   │   ├── v1/
│   │   │   │   │   ├── PaymentController.kt
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── CreatePaymentRequest.kt
│   │   │   │   │   │   ├── PaymentResponse.kt
│   │   │   │   │   │   └── ErrorResponse.kt
│   │   │   │   │   └── mapper/
│   │   │   │   │       └── PaymentDtoMapper.kt
│   │   │   │   └── websocket/
│   │   │   │       ├── WebSocketConfig.kt
│   │   │   │       └── PaymentStatusWebSocketHandler.kt
│   │   │   │
│   │   │   ├── domain/                     ← Pure domain model (no framework deps)
│   │   │   │   ├── model/
│   │   │   │   │   ├── Payment.kt
│   │   │   │   │   ├── PaymentAttempt.kt
│   │   │   │   │   ├── Money.kt
│   │   │   │   │   └── PaymentStatus.kt
│   │   │   │   ├── event/
│   │   │   │   │   ├── PaymentEvent.kt
│   │   │   │   │   └── DomainEventEnvelope.kt
│   │   │   │   └── exception/
│   │   │   │       ├── InvalidStateTransitionException.kt
│   │   │   │       ├── IdempotencyConflictException.kt
│   │   │   │       └── PaymentNotFoundException.kt
│   │   │   │
│   │   │   ├── orchestration/              ← SAGA orchestration engine
│   │   │   │   ├── PaymentOrchestrationService.kt
│   │   │   │   ├── PaymentStateMachine.kt
│   │   │   │   ├── RetryCoordinator.kt
│   │   │   │   └── OutboxPublisher.kt
│   │   │   │
│   │   │   ├── routing/                    ← Routing engine
│   │   │   │   ├── RoutingEngine.kt
│   │   │   │   └── RoutingRule.kt
│   │   │   │
│   │   │   ├── provider/                   ← Provider connectors (Strategy pattern)
│   │   │   │   ├── ProviderConnector.kt    ← Interface
│   │   │   │   ├── ProviderConnectorRegistry.kt
│   │   │   │   ├── model/
│   │   │   │   │   ├── ProviderChargeRequest.kt
│   │   │   │   │   └── ProviderChargeResponse.kt
│   │   │   │   └── impl/
│   │   │   │       ├── ProviderAConnector.kt
│   │   │   │       └── ProviderBConnector.kt
│   │   │   │
│   │   │   ├── idempotency/                ← Idempotency service + Redis Lua
│   │   │   │   ├── IdempotencyService.kt
│   │   │   │   └── scripts/
│   │   │   │       └── idempotency_check.lua
│   │   │   │
│   │   │   ├── persistence/                ← JPA repositories + entities
│   │   │   │   ├── entity/
│   │   │   │   │   ├── PaymentEntity.kt
│   │   │   │   │   ├── PaymentAttemptEntity.kt
│   │   │   │   │   ├── OutboxEventEntity.kt
│   │   │   │   │   └── IdempotencyKeyEntity.kt
│   │   │   │   └── repository/
│   │   │   │       ├── PaymentRepository.kt
│   │   │   │       ├── PaymentAttemptRepository.kt
│   │   │   │       └── OutboxEventRepository.kt
│   │   │   │
│   │   │   ├── messaging/                  ← Kafka producers + consumers
│   │   │   │   ├── outbox/
│   │   │   │   │   └── OutboxProcessor.kt  ← Scheduler + Kafka publisher
│   │   │   │   └── consumer/
│   │   │   │       ├── NotificationConsumer.kt
│   │   │   │       └── LedgerConsumer.kt
│   │   │   │
│   │   │   ├── realtime/                   ← Redis pub/sub + WebSocket push
│   │   │   │   └── RedisPaymentStatusPublisher.kt
│   │   │   │
│   │   │   └── config/                     ← Spring configuration
│   │   │       ├── RedisConfig.kt
│   │   │       ├── KafkaConfig.kt
│   │   │       ├── Resilience4jConfig.kt
│   │   │       ├── SecurityConfig.kt
│   │   │       └── OpenApiConfig.kt
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── db/migration/               ← Flyway migrations
│   │       │   ├── V1__create_payments_table.sql
│   │       │   ├── V2__create_payment_attempts_table.sql
│   │       │   ├── V3__create_payment_status_history.sql
│   │       │   ├── V4__create_outbox_events.sql
│   │       │   ├── V5__create_idempotency_keys.sql
│   │       │   ├── V6__create_providers.sql
│   │       │   └── V7__create_routing_rules.sql
│   │       └── scripts/lua/
│   │           └── idempotency_check.lua
│   │
│   └── test/
│       ├── kotlin/com/yuno/payment/
│       │   ├── unit/                       ← Pure unit tests (no Spring context)
│       │   ├── integration/                ← Testcontainers
│       │   └── api/                        ← MockMvc controller tests
│       └── resources/
│           └── application-test.yml
│
└── docs/
    ├── plan.md                             ← This document
    ├── test-cases.md                       ← All test scenarios
    ├── api.md                              ← OpenAPI spec
    └── prompts.md                          ← Vibe coding prompt log
```

---

## 15. Sequence Diagrams

### Happy Path — Card Payment

```
Client          Controller      Orchestrator    Idempotency    RoutingEngine   ProviderA      DB           Kafka
  │                │                │               │               │              │            │              │
  │ POST /payments │                │               │               │              │            │              │
  │ X-Idempotency-Key               │               │               │              │            │              │
  ├───────────────►│                │               │               │              │            │              │
  │                │ createPayment()│               │               │              │            │              │
  │                ├───────────────►│               │               │              │            │              │
  │                │                │ checkKey()    │               │              │            │              │
  │                │                ├──────────────►│               │              │            │              │
  │                │                │◄── MISS ──────┤               │              │            │              │
  │                │                │                               │              │            │              │
  │                │                │─── BEGIN TX ──────────────────────────────────────────────►            │
  │                │                │               save(INITIATED) │              │            │              │
  │                │                ├──────────────────────────────────────────────────────────►│              │
  │                │                │               insert outbox   │              │            │              │
  │                │                ├──────────────────────────────────────────────────────────►│              │
  │                │                │─── COMMIT ─────────────────────────────────────────────────            │
  │                │                │                               │              │            │              │
  │                │                │               resolveChain([CARD])           │            │              │
  │                │                ├──────────────────────────────►│              │            │              │
  │                │                │◄── [PROVIDER_A, PROVIDER_B] ──┤              │            │              │
  │                │                │                                              │            │              │
  │                │                │         charge(request)        │             │            │              │
  │                │                ├─────────────────────────────────────────────►│            │              │
  │                │                │◄── AUTHORIZED ──────────────────────────────┤            │              │
  │                │                │                                              │            │              │
  │                │                │─── BEGIN TX ──────────────────────────────────────────────►            │
  │                │                │               update(CAPTURED)│              │            │              │
  │                │                ├──────────────────────────────────────────────────────────►│              │
  │                │                │               insert outbox   │              │            │              │
  │                │                ├──────────────────────────────────────────────────────────►│              │
  │                │                │─── COMMIT ─────────────────────────────────────────────────            │
  │                │                │                                                                         │
  │                │                │         PUBLISH to Redis pub/sub (WebSocket push)                      │
  │                │                │         store idempotency result                                       │
  │                │                │                                                                         │
  │                │◄───────────────┤ 201 Created                                                            │
  │◄───────────────┤                │                                                                         │
  │                │                │                                                           Outbox ───────►│
  │                │                │                                                           Processor  payment.v1.succeeded
```

### Failover Path — Provider A Times Out

```
Orchestrator      ProviderA        ProviderB        DB
     │                │                │             │
     │  charge()      │                │             │
     ├───────────────►│                │             │
     │   [5s timeout] │                │             │
     │◄──TimeoutEx────┤                │             │
     │                                 │             │
     │  insert attempt(A, FAILED, retryable=true)    │
     ├────────────────────────────────────────────► DB
     │                                 │             │
     │  charge()      [CIRCUIT OPEN?]  │             │
     ├────────────────────────────────►│             │
     │◄── AUTHORIZED ─────────────────┤             │
     │                                 │             │
     │  update payment(CAPTURED via B)              │
     ├────────────────────────────────────────────► DB
```

---

## 16. Test Strategy

### Classification

#### Sanity Tests
Core flows that must always work — run on every commit, fast (<30s).
- Create card payment → CAPTURED
- Create UPI payment → CAPTURED
- Fetch payment by ID
- Duplicate request with same idempotency key → same response

#### Regression Tests
Full functional coverage — run on every PR, moderate speed (<2min).
- All payment method types
- All failure scenarios
- All state transitions
- Retry and failover paths
- Idempotency conflict (same key, different body)
- Invalid inputs (missing fields, invalid amounts, unsupported currencies)

#### Integration Tests (Testcontainers)
Real infra — run on merge to main, slower (<5min).
- Full create payment flow with real Postgres + Redis + Kafka
- Outbox processor publishes events to Kafka
- WebSocket receives status update in real-time
- Reconciliation job recovers stuck payments
- Circuit breaker opens after provider failures

### Test Cases Document

See `docs/test-cases.md` for the full matrix of 50+ test cases including:
- Happy path scenarios (all payment methods)
- Negative scenarios (declined, invalid, expired card)
- Edge cases (zero amount, max amount, concurrent requests)
- Chaos scenarios (provider down, Redis down, Kafka down)

---

## 17. Performance Considerations

### Targets
| Metric | Target | How Achieved |
|---|---|---|
| P99 latency (create payment) | < 2s | Provider timeout: 5s max; fast DB writes |
| Throughput | 1000 TPS | JVM21 virtual threads; HikariCP pool sizing |
| Idempotency check | < 5ms | Redis in-memory; Lua atomic |
| Status fetch | < 20ms | Redis cache (5min TTL) |
| Outbox lag | < 500ms | 100ms scheduler interval |

### DB Performance
- ULID primary keys: better index locality than random UUIDs
- GIN index on `metadata` JSONB for flexible queries
- `idx_outbox_unpublished` partial index — only scans unpublished rows
- Read replicas for GET endpoints (future)
- Connection pool: HikariCP, max 20 connections per instance

### JVM 21 Virtual Threads
```yaml
spring:
  threads:
    virtual:
      enabled: true   # Enables virtual threads for Tomcat + @Async
```
Each HTTP request runs on a virtual thread — blocking I/O (Postgres, Redis, provider HTTP) suspends the virtual thread without blocking an OS thread. Achieves WebFlux-level throughput without reactive complexity.

---

## 18. Evolution Roadmap

### Phase 1 (This Assignment) ✅
- Orchestration SAGA (synchronous critical path)
- Explicit domain state machine + persisted status history
- Choreography via Kafka + Outbox (async side effects)
- Local Spring application events for in-process, after-commit side effects
- REST API v1
- Merchant API key authentication before payment creation
- WebSocket real-time push
- Redis idempotency + distributed lock
- Resilience4j circuit breaker + retry
- OpenTelemetry tracing with Jaeger local backend
- Static/environment-based provider endpoint configuration; no Eureka yet
- Testcontainers integration tests

### Phase 2 (Next Quarter)
- Replace polling Outbox with Debezium CDC (zero-lag event publishing)
- gRPC provider connectors (replace simulated HTTP)
- Schema Registry for Kafka Avro schemas
- OAuth2/OIDC + PKCE for merchant dashboard users and delegated access
- Webhook retry service with exponential backoff
- Read replicas for GET endpoints

### Phase 3 (Scale)
- Split into microservices (Payment, Routing, Notification, Ledger)
- Add service discovery only if needed by deployment model: Kubernetes DNS in K8s, Eureka/Consul outside K8s
- RabbitMQ STOMP broker for clustered WebSocket
- Sharding by merchantId for DB horizontal scale
- GraphQL Federation for merchant dashboard API aggregation
- Event sourcing for complete audit trail (replace status history table)

---

## Prompts Log (Vibe Coding)

Maintained in `docs/prompts.md` — every Claude prompt used during development is logged with:
- The prompt text
- The component it generated
- Any modifications made manually
- Rationale for the modification

This fulfills the assessment requirement for documenting AI-assisted development.

---

> **Next step:** Approve this plan, then implementation begins with:  
> 1. `docker-compose.yml` (local infra)  
> 2. Flyway migrations (merchants/API keys + payment schema)  
> 3. Spring Security API key authentication  
> 4. Domain model + state machine  
> 5. Orchestration service  
> 6. REST controller + integration tests
