package com.yuno.payment.webhook

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WebhookRetryProcessorTest {
    @Test
    fun `retries pending deliveries`() {
        val repository = mock<WebhookDeliveryRepository>()
        val service = mock<WebhookService>()
        val delivery = WebhookDeliveryEntity(id = 1, paymentId = "pay_001")
        whenever(repository.findByStatusAndNextRetryAtBefore(org.mockito.kotlin.eq("PENDING"), any()))
            .thenReturn(listOf(delivery))

        WebhookRetryProcessor(repository, service).retryPendingDeliveries()

        verify(service).attemptDelivery(delivery)
    }
}
