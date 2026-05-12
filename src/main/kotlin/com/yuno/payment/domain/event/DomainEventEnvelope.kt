package com.yuno.payment.domain.event

import java.time.Instant
import java.util.UUID

/**
 * Typed domain event envelope that wraps all Kafka events.
 * Matches the plan §5.2 event envelope specification.
 */
data class DomainEventEnvelope(
    val eventId: String = "evt_${UUID.randomUUID()}",
    val eventVersion: String = "1.0",
    val eventType: String,
    val occurredAt: Instant = Instant.now(),
    val traceId: String? = null,
    val producerService: String = "payment-orchestration",
    val schemaVersion: String = "v1",
    val payload: Map<String, Any?>,
)
