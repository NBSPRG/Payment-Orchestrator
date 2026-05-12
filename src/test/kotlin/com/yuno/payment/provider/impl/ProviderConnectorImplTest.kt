package com.yuno.payment.provider.impl

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProviderConnectorImplTest {
    @Test
    fun `provider A charges refunds and checks status`() {
        val provider = ProviderAConnector()
        val payment = payment()

        assertThat(provider.supportedPaymentMethods).contains(PaymentMethodType.CARD, PaymentMethodType.UPI)
        assertThat(provider.charge(payment).status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(provider.refund(payment).status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(provider.getStatus("txn_001").providerTransactionId).isEqualTo("txn_001")
    }

    @Test
    fun `provider B charges refunds and checks status`() {
        val provider = ProviderBConnector()
        val payment = payment()

        assertThat(provider.supportedPaymentMethods).contains(PaymentMethodType.CARD, PaymentMethodType.UPI)
        assertThat(provider.charge(payment).status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(provider.refund(payment).status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(provider.getStatus("txn_002").providerTransactionId).isEqualTo("txn_002")
    }

    private fun payment() = PaymentEntity(
        id = "pay_001",
        merchantId = "merchant_demo",
        idempotencyKey = "idem_001",
        paymentMethodType = PaymentMethodType.CARD,
        amountValue = 1000,
        amountCurrency = "INR",
    )
}
