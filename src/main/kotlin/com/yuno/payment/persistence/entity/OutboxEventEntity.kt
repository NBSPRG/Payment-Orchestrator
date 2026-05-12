package com.yuno.payment.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "aggregate_id")
    var aggregateId: String = "",

    @Column(name = "aggregate_type")
    var aggregateType: String = "Payment",

    @Column(name = "event_type")
    var eventType: String = "",

    @Column(name = "kafka_topic")
    var kafkaTopic: String = "",

    @Column(name = "kafka_key")
    var kafkaKey: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    var payload: Map<String, Any?> = emptyMap(),

    @Column(name = "published")
    var published: Boolean = false,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "claimed_at")
    var claimedAt: Instant? = null,

    @Column(name = "claimed_by")
    var claimedBy: String? = null,

    @Column(name = "retry_count")
    var retryCount: Int = 0,

    @Column(name = "max_retries")
    var maxRetries: Int = 10,

    @Column(name = "last_error")
    var lastError: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),
)
