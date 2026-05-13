# AI Prompt History

This file documents the AI-assisted development prompts and how they evolved during the project. The prompts are summarized rather than copied as a verbatim chat transcript.

## Prompt 1: Assignment Understanding

Goal:

- Build a simplified payment orchestration system similar to Yuno.
- Use Java or Kotlin with Spring Boot.
- Include create payment, fetch payment, routing, retry/failover, idempotency, and status tracking.
- Include documentation and tests.

Resulting direction:

- Kotlin and Spring Boot 3 were selected.
- PostgreSQL was selected as durable storage.
- Redis was selected for runtime coordination use cases.
- Kafka was selected as an optional bonus for asynchronous side effects.
- The project was shaped as a modular monolith rather than multiple services.

## Prompt 2: Architecture Planning

Goal:

- Design layers for controller, orchestration service, routing engine, provider connectors, persistence, and idempotency.
- Keep the design understandable and demonstrable for an assignment.

Resulting direction:

- `PaymentController` owns HTTP request handling and validation.
- `PaymentOrchestrationService` owns payment creation, cancellation, refund, and provider processing coordination.
- `RoutingEngine` resolves provider chains from database rules.
- `ProviderConnector` abstracts Provider A and Provider B.
- `PaymentStateMachine` centralizes allowed status transitions.
- Flyway migrations own the schema.

## Prompt 3: Persistence And Idempotency

Goal:

- Add durable payment persistence.
- Add idempotency behavior so duplicate requests do not create duplicate payments.
- Detect same key with different request body.

Resulting direction:

- Added `payments`, `idempotency_keys`, `payment_attempts`, and `payment_status_history`.
- Idempotency stores request hash and response body in PostgreSQL.
- Same merchant, same key, same body returns cached response.
- Same merchant, same key, different body returns conflict.

## Prompt 4: Provider Routing And Retry

Goal:

- Implement CARD to Provider A and UPI to Provider B.
- Add provider failover and retry behavior.

Resulting direction:

- Routing rules are database-backed and priority-sorted.
- CARD primary route is Provider A with Provider B as failover.
- UPI primary route is Provider B with Provider A as failover.
- `RetryCoordinator` records attempts and uses Resilience4j retry/circuit breaker wrappers.

## Prompt 5: Asynchronous Processing

Goal:

- Keep payment creation fast.
- Move provider processing to a background flow.
- Store and expose payment status.

Resulting direction:

- `POST /api/v1/payments` creates an `INITIATED` payment.
- `PaymentProcessingJob` processes initiated payments asynchronously.
- Status can be checked with `GET /api/v1/payments/{id}/status`.
- Status history is persisted.

## Prompt 6: Messaging And Side Effects

Goal:

- Add reliable asynchronous side effects.
- Avoid publishing Kafka events directly inside domain transactions.

Resulting direction:

- Added outbox table and `OutboxPublisher`.
- Added scheduled `OutboxProcessor`.
- Added Kafka consumers for ledger, notification, and webhooks.
- Ledger entries are idempotent by event id and entry type.

## Prompt 7: Reliability And Security

Goal:

- Add assignment standout features.
- Improve runtime reliability and operational safety.

Resulting direction:

- Added Resilience4j configuration.
- Added Redis payment lock.
- Added Redis-backed rate limiting.
- Added request body size filter.
- Added API key rotation.
- Added webhook signing and retry scheduling.

## Prompt 8: Observability And API Documentation

Goal:

- Add health checks, metrics, structured logs, tracing configuration, and OpenAPI docs.

Resulting direction:

- Added Spring Boot Actuator.
- Added Micrometer metrics.
- Added Prometheus registry.
- Added OpenTelemetry tracing bridge and OTLP exporter configuration.
- Added structured JSON logging.
- Added Swagger/OpenAPI configuration.

## Prompt 9: Test Coverage

Goal:

- Add positive and negative tests.
- Add integration tests for the main service flow.

Resulting direction:

- Added unit tests for validation, state machine, routing, provider registry, retry coordinator, idempotency, security, webhook, ledger, notification, metrics, and real-time publisher.
- Added Testcontainers-backed integration tests for health, create payment, idempotency replay, idempotency conflict, validation failures, missing API key, list payments, API key rotation, and async capture with Provider A.

## Prompt 10: Local And Remote Demo Setup

Goal:

- Run dependencies on an Azure VM but run the app locally.
- Connect to PostgreSQL, Redis, Kafka, and Jaeger through SSH tunnels.

Resulting direction:

- Use `docker compose up -d` on the VM.
- Use SSH local port forwarding for `5432`, `6379`, `9092`, `4318`, and `16686`.
- Run `bootRun` locally against forwarded `localhost` ports.
- Verified payment creation, async capture, notification, ledger, and refund flows.
