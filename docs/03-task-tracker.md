# Payment Orchestration Task Tracker

Status legend:

- `[x]` Done
- `[ ]` Pending
- `[~]` Partially done / needs hardening

## Current Baseline

- [x] Kotlin Spring Boot modular monolith structure
- [x] Java 21 Gradle toolchain configuration
- [x] Gradle Wrapper present
- [x] PostgreSQL, Redis, Kafka, and Jaeger Docker Compose setup
- [x] Flyway migrations for merchants, API keys, payments, attempts, status history, outbox, providers, routing rules, ledger entries, and webhook deliveries
- [x] Merchant API key authentication
- [x] Rate limiting filter using Redis
- [x] Payment create/get/status/cancel/refund/list endpoints
- [x] Explicit payment state machine
- [x] Idempotency key persistence and replay support
- [x] Database uniqueness for duplicate payment create requests
- [x] Redis payment lock wired into create processing, cancel, and refund
- [x] Redis lock release uses atomic Lua compare-and-delete
- [x] Optimistic lock conflicts mapped to concurrent payment errors
- [x] Provider processing runs in a background scheduled worker instead of blocking payment creation
- [x] Provider routing
- [x] Provider retry/failover coordinator
- [x] Resilience4j circuit breaker and retry registry configuration
- [x] Bounded provider executor for timeout-wrapped provider calls
- [x] Outbox event creation
- [x] Scheduled outbox publisher with row claiming, retry count, and dead-letter logging
- [x] Kafka consumers for ledger, notification, and webhook flows
- [x] Double-entry ledger service
- [x] Webhook delivery with HMAC signature and retry scheduling
- [x] Webhook delivery has HTTP timeouts and retryable status classification
- [x] WebSocket payment status updates
- [x] Redis pub/sub payment status fan-out
- [x] Reconciliation job for stuck payments
- [x] Micrometer payment metrics
- [x] Structured logging config
- [x] OpenAPI configuration
- [x] Focused unit tests for validation, state machine, routing, retry coordinator, and ledger service
- [~] Testcontainers integration tests exist, but require Docker to run locally
- [x] Fast test suite is split from Docker-backed integration suite
- [x] JaCoCo coverage gate configured at 75%

## P0 - Production Correctness

- [x] Move provider processing out of the HTTP request path
  - Create payment as `INITIATED`
  - Store the initial idempotency response
  - Return quickly with accepted/created response
  - Process provider calls in a background worker
  - Update status asynchronously
- [x] Add outbox row claiming for multi-instance safety
  - Use PostgreSQL row locking, for example `FOR UPDATE SKIP LOCKED`
  - Prevent two app instances from publishing the same pending event
  - Track claimed/published/dead-lettered states clearly
- [x] Make Redis lock release atomic
  - Replace get-then-delete with Lua compare-and-delete
  - Keep lock ownership token validation
- [x] Add concurrency/idempotency integration tests
  - Same idempotency key and same body creates only one payment
  - Same idempotency key and different body returns conflict
  - Different payments continue processing in parallel
- [ ] Add optimistic-lock conflict integration tests

## P1 - Reliability

- [x] Add provider call timeouts to actual connector execution
- [ ] Make provider result handling durable across app restarts
- [ ] Add reconciliation coverage for provider status polling results
- [ ] Add dead-letter table or status for permanently failed outbox events
- [x] Add cleanup job for expired idempotency keys
- [x] Add retry policies by error category, not only by provider chain
- [x] Add bounded executor/thread pool settings for background processing

## P1 - Webhook Hardening

- [x] Configure webhook HTTP connection/read timeouts
- [x] Sign timestamp plus body, not only body
- [x] Reject non-HTTPS webhook URLs at request validation
- [x] Retry only retryable HTTP statuses and network errors
- [x] Add webhook delivery unit tests
- [ ] Add webhook delivery integration tests
- [x] Add webhook dead-letter visibility after max attempts

## P1 - API And Validation

- [x] Validate idempotency key length and allowed characters
- [x] Validate currency format and supported currencies
- [x] Validate positive amount
- [x] Tighten card expiry validation
- [x] Add metadata size limits
- [x] Add request body size limits
- [x] Document core error responses in OpenAPI
- [ ] Add examples for create, cancel, refund, status, and list APIs

## P2 - Security

- [x] Add API key rotation flow
- [x] Add merchant-level audit logs
- [x] Mask card/payment sensitive fields in logs
- [ ] Review WebSocket subscription authorization by merchant and payment
- [ ] Add stronger webhook secret management
- [ ] Add optional IP allowlisting for merchant API keys

## P2 - Observability

- [x] Add provider latency metrics by provider and outcome
- [x] Add idempotency replay metrics
- [x] Add outbox lag metrics
- [x] Add webhook retry/failure metrics
- [x] Add reconciliation metrics
- [ ] Add dashboard notes for local Prometheus/Grafana setup if added

## P2 - Developer Experience

- [x] Update README to use `.\gradlew.bat` commands instead of global `gradle`
- [x] Add troubleshooting section for Java 21, Docker, and Testcontainers
- [x] Add local smoke-test commands
- [x] Add sample Postman/curl collection
- [x] Add architecture decision notes for async processing and outbox locking
- [x] Add JaCoCo coverage command and integration test command

## Recently Completed

- [x] Installed/verified Java 21 locally
- [x] Added Gradle toolchain resolver configuration
- [x] Added Java auto-detect/auto-download Gradle properties
- [x] Updated IntelliJ project metadata to Java 21
- [x] Verified Gradle Wrapper runs with Java 21
- [x] Fixed Resilience4j retry config for current API
- [x] Wired Redis payment lock into payment mutations
- [x] Added background processor for initiated payments
- [x] Hardened Redis lock release with Lua compare-and-delete
- [x] Added outbox row claiming with PostgreSQL `FOR UPDATE SKIP LOCKED`
- [x] Hardened webhook delivery with timeouts, retry classification, and timestamp-body signatures
- [x] Added idempotency key, metadata, HTTPS webhook URL, and card expiry validation
- [x] Wired provider calls through Resilience4j time limiter
- [x] Updated README runbook and troubleshooting notes
- [x] Added idempotency cleanup job, request body size guard, and supported-currency validation
- [x] Added provider executor bounds and expanded runtime metrics
- [x] Added API key rotation endpoint
- [x] Added smoke-test docs, architecture decisions, and Postman collection
- [x] Added 75% JaCoCo coverage gate and expanded fast test suite
- [x] Added smoke, validation, API-key, and full payment-flow integration tests
- [x] Removed unused repository methods from ledger and webhook repositories
- [x] Verified Kotlin compilation
- [x] Verified focused non-Docker unit tests

## Verification Commands

Use these for quick local checks:

```powershell
.\gradlew.bat compileKotlin compileTestKotlin
```

```powershell
.\gradlew.bat test jacocoTestCoverageVerification
```

Use this when Docker Desktop is running:

```powershell
.\gradlew.bat integrationTest
```
