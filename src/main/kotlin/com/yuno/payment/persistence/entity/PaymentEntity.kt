package com.yuno.payment.persistence.entity

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "payments")
class PaymentEntity(
    @Id
    @Column(name = "id")
    var id: String = "",

    @Column(name = "merchant_id")
    var merchantId: String = "",

    @Column(name = "idempotency_key")
    var idempotencyKey: String = "",

    @Column(name = "merchant_reference")
    var merchantReference: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: PaymentStatus = PaymentStatus.INITIATED,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type")
    var paymentMethodType: PaymentMethodType = PaymentMethodType.CARD,

    @Column(name = "amount_value")
    var amountValue: Long = 0,

    @Column(name = "amount_currency")
    var amountCurrency: String = "",

    @Column(name = "provider_name")
    var providerName: String? = null,

    @Column(name = "provider_transaction_id")
    var providerTransactionId: String? = null,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "webhook_url")
    var webhookUrl: String? = null,

    @Column(name = "return_url")
    var returnUrl: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: Map<String, Any?>? = null,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "failure_error_code")
    var failureErrorCode: String? = null,

    @Column(name = "retry_count")
    var retryCount: Int = 0,

    @Version
    @Column(name = "version")
    var version: Long = 0,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),
)
