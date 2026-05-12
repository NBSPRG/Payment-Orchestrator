package com.yuno.payment.routing

import com.yuno.payment.domain.model.Money
import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.persistence.repository.RoutingRuleRepository
import org.springframework.stereotype.Component

@Component
class RoutingEngine(
    private val routingRuleRepository: RoutingRuleRepository,
) {
    fun resolveProviderChain(
        paymentMethodType: PaymentMethodType,
        amount: Money,
    ): List<String> {
        return routingRuleRepository.findByPaymentMethodTypeAndActiveOrderByPriorityAsc(paymentMethodType)
            .filter { rule ->
                (rule.currency == null || rule.currency == amount.currency) &&
                    (rule.minAmountValue == null || amount.value >= rule.minAmountValue!!) &&
                    (rule.maxAmountValue == null || amount.value <= rule.maxAmountValue!!)
            }
            .map { it.providerName }
    }
}
