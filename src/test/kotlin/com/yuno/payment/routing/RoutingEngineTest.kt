package com.yuno.payment.routing

import com.yuno.payment.domain.model.Money
import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.persistence.entity.RoutingRuleEntity
import com.yuno.payment.persistence.repository.RoutingRuleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RoutingEngineTest {
    private val repository = mock(RoutingRuleRepository::class.java)
    private val routingEngine = RoutingEngine(repository)

    @Test
    fun `returns providers that match currency and amount in priority order`() {
        `when`(repository.findByPaymentMethodTypeAndActiveOrderByPriorityAsc(PaymentMethodType.CARD))
            .thenReturn(
                listOf(
                    rule("PROVIDER_A", priority = 1, currency = "INR", maxAmountValue = 5_000),
                    rule("PROVIDER_B", priority = 2, currency = "INR", minAmountValue = 5_001),
                    rule("PROVIDER_C", priority = 3, currency = "USD"),
                ),
            )

        val providers = routingEngine.resolveProviderChain(
            paymentMethodType = PaymentMethodType.CARD,
            amount = Money(value = 10_000, currency = "INR"),
        )

        assertThat(providers).containsExactly("PROVIDER_B")
    }

    private fun rule(
        providerName: String,
        priority: Int,
        currency: String? = null,
        minAmountValue: Long? = null,
        maxAmountValue: Long? = null,
    ) = RoutingRuleEntity(
        paymentMethodType = PaymentMethodType.CARD,
        providerName = providerName,
        priority = priority,
        currency = currency,
        minAmountValue = minAmountValue,
        maxAmountValue = maxAmountValue,
    )
}
