package com.yuno.payment.orchestration

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.OutboxEventEntity
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.repository.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OutboxPublisherTest {
    @Test
    fun `publishes payment event envelope to outbox`() {
        val repository = mock<OutboxEventRepository>()
        whenever(repository.save(org.mockito.kotlin.any<OutboxEventEntity>())).thenAnswer { it.arguments[0] }
        val publisher = OutboxPublisher(repository, tracer = null)

        publisher.publish(payment(), PaymentEventType.CAPTURED)

        val captor = argumentCaptor<OutboxEventEntity>()
        verify(repository).save(captor.capture())
        val event = captor.firstValue
        assertThat(event.aggregateId).isEqualTo("pay_001")
        assertThat(event.eventType).isEqualTo("payment.v1.captured")
        assertThat(event.payload["eventType"]).isEqualTo("payment.v1.captured")
        assertThat((event.payload["payload"] as Map<*, *>)["paymentId"]).isEqualTo("pay_001")
    }

    private fun payment() = PaymentEntity(
        id = "pay_001",
        merchantId = "merchant_demo",
        idempotencyKey = "idem_001",
        status = PaymentStatus.CAPTURED,
        paymentMethodType = PaymentMethodType.CARD,
        amountValue = 1000,
        amountCurrency = "INR",
    )
}
