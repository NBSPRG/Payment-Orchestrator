package com.yuno.payment.webhook

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
@Table(name = "webhook_deliveries")
class WebhookDeliveryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "payment_id")
    var paymentId: String = "",

    @Column(name = "merchant_id")
    var merchantId: String = "",

    @Column(name = "webhook_url")
    var webhookUrl: String = "",

    @Column(name = "event_type")
    var eventType: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    var payload: Map<String, Any?> = emptyMap(),

    @Column(name = "status")
    var status: String = "PENDING",

    @Column(name = "http_status")
    var httpStatus: Int? = null,

    @Column(name = "attempt_count")
    var attemptCount: Int = 0,

    @Column(name = "max_attempts")
    var maxAttempts: Int = 5,

    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null,

    @Column(name = "last_error")
    var lastError: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),
)
