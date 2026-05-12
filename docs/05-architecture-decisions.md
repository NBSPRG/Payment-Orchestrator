# Architecture Decisions

## ADR-001: Modular Monolith First

The project remains one Spring Boot deployable with package-level module boundaries. This avoids distributed consistency, network calls, and multi-service deployment complexity until there is a real extraction need.

## ADR-002: Async Provider Processing

Payment creation writes a durable `INITIATED` payment and returns quickly. Provider charging is performed by a scheduled background processor.

Reasons:

- HTTP request latency is not coupled to provider latency.
- Provider timeout/retry/failover can evolve independently.
- Restart recovery is simpler because pending work is visible in the database.

## ADR-003: Outbox For Side Effects

Payment status changes write outbox rows in the same database transaction as domain changes. A scheduled publisher sends those events to Kafka.

The outbox processor claims rows with PostgreSQL `FOR UPDATE SKIP LOCKED` so multiple app instances can run safely.

## ADR-004: Same-Payment Locking

The system does not use a global payment lock. Different payment IDs can process concurrently. Mutations on the same payment ID are serialized with a Redis lock and backed by JPA optimistic locking.

## ADR-005: Idempotency

Create payment requests require `X-Idempotency-Key`. The same merchant/key/request body returns the original response. Reusing the same key with a different body returns an idempotency conflict.
