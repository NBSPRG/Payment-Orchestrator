package com.yuno.payment.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "merchant_api_keys")
class MerchantApiKeyEntity(
    @Id
    @Column(name = "id")
    var id: String = "",

    @Column(name = "merchant_id")
    var merchantId: String = "",

    @Column(name = "key_prefix")
    var keyPrefix: String = "",

    @Column(name = "key_hash")
    var keyHash: String = "",

    @Column(name = "environment")
    var environment: String = "",

    @Column(name = "status")
    var status: String = "",

    @Column(name = "rate_limit_tier")
    var rateLimitTier: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,
)
