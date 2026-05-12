package com.yuno.payment.persistence.entity

import com.yuno.payment.domain.model.PaymentMethodType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "routing_rules")
class RoutingRuleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type")
    var paymentMethodType: PaymentMethodType = PaymentMethodType.CARD,

    @Column(name = "provider_name")
    var providerName: String = "",

    @Column(name = "priority")
    var priority: Int = 0,

    @Column(name = "is_active")
    var active: Boolean = true,

    @Column(name = "min_amount_value")
    var minAmountValue: Long? = null,

    @Column(name = "max_amount_value")
    var maxAmountValue: Long? = null,

    @Column(name = "currency")
    var currency: String? = null,
)
