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
@Table(name = "idempotency_keys")
class IdempotencyKeyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "merchant_id")
    var merchantId: String = "",

    @Column(name = "idempotency_key")
    var idempotencyKey: String = "",

    @Column(name = "request_hash")
    var requestHash: String = "",

    @Column(name = "response_status")
    var responseStatus: Int = 201,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    var responseBody: Map<String, Any?> = emptyMap(),

    @Column(name = "payment_id")
    var paymentId: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    var expiresAt: Instant = Instant.now(),
)
