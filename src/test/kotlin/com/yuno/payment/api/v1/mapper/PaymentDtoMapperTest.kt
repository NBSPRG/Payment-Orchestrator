package com.yuno.payment.api.v1.mapper

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaymentDtoMapperTest {
    @Test
    fun `maps payment entity to response`() {
        val entity = PaymentEntity(
            id = "pay_001",
            merchantId = "merchant_001",
            idempotencyKey = "idem_001",
            merchantReference = "ORDER-1",
            status = PaymentStatus.CAPTURED,
            paymentMethodType = PaymentMethodType.CARD,
            amountValue = 1000,
            amountCurrency = "INR",
            providerName = "PROVIDER_A",
            providerTransactionId = "txn_001",
        )

        val response = entity.toResponse()

        assertThat(response.id).isEqualTo("pay_001")
        assertThat(response.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(response.amount.value).isEqualTo(1000)
        assertThat(response.amount.currency).isEqualTo("INR")
        assertThat(response.providerName).isEqualTo("PROVIDER_A")
    }
}
