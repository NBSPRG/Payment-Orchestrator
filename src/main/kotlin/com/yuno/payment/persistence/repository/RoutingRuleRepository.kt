package com.yuno.payment.persistence.repository

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.persistence.entity.RoutingRuleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RoutingRuleRepository : JpaRepository<RoutingRuleEntity, Long> {
    fun findByPaymentMethodTypeAndActiveOrderByPriorityAsc(
        paymentMethodType: PaymentMethodType,
        active: Boolean = true,
    ): List<RoutingRuleEntity>
}
