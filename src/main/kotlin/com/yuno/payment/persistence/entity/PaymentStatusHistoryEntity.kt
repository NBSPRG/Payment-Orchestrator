package com.yuno.payment.persistence.entity

import com.yuno.payment.domain.model.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "payment_status_history")
class PaymentStatusHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "payment_id")
    var paymentId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    var fromStatus: PaymentStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    var toStatus: PaymentStatus = PaymentStatus.INITIATED,

    @Column(name = "reason")
    var reason: String? = null,

    @Column(name = "triggered_by")
    var triggeredBy: String = "ORCHESTRATOR",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: Map<String, Any?>? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),
)
