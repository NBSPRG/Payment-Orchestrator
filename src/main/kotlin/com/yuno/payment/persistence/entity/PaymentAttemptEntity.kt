package com.yuno.payment.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "payment_attempts")
class PaymentAttemptEntity(
    @Id
    @Column(name = "id")
    var id: String = "",

    @Column(name = "payment_id")
    var paymentId: String = "",

    @Column(name = "attempt_number")
    var attemptNumber: Int = 1,

    @Column(name = "provider_name")
    var providerName: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_request", columnDefinition = "jsonb")
    var providerRequest: Map<String, Any?>? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    var providerResponse: Map<String, Any?>? = null,

    @Column(name = "status")
    var status: String = "",

    @Column(name = "error_code")
    var errorCode: String? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "is_retryable")
    var retryable: Boolean = false,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),
)
