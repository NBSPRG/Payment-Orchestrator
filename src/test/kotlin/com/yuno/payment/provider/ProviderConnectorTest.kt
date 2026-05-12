package com.yuno.payment.provider

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProviderConnectorTest {
    @Test
    fun `default connector supports all payment methods and rejects unsupported operations`() {
        val connector = object : ProviderConnector {
            override val name: String = "TEST"
            override fun charge(payment: com.yuno.payment.persistence.entity.PaymentEntity): com.yuno.payment.provider.model.ProviderResult {
                error("not used")
            }
        }

        assertThat(connector.supportedPaymentMethods).isNotEmpty()
        assertThrows(UnsupportedOperationException::class.java) { connector.refund(com.yuno.payment.persistence.entity.PaymentEntity()) }
        assertThrows(UnsupportedOperationException::class.java) { connector.getStatus("txn") }
    }
}
