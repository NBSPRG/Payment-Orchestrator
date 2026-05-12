package com.yuno.payment.messaging.consumer

import com.yuno.payment.webhook.WebhookService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class WebhookConsumerTest {
    private val webhookService = mock<WebhookService>()
    private val consumer = WebhookConsumer(webhookService)

    @Test
    fun `terminal event with webhook url creates delivery`() {
        consumer.onPaymentEvent(
            """
            {
              "eventType": "payment.v1.captured",
              "payload": {
                "paymentId": "pay_001",
                "merchantId": "merchant_demo",
                "webhookUrl": "https://merchant.example/webhook"
              }
            }
            """.trimIndent(),
        )

        verify(webhookService).createDelivery(
            paymentId = eq("pay_001"),
            merchantId = eq("merchant_demo"),
            webhookUrl = eq("https://merchant.example/webhook"),
            eventType = eq("payment.v1.captured"),
            payload = any(),
        )
    }

    @Test
    fun `event without webhook url is ignored`() {
        consumer.onPaymentEvent(
            """
            {
              "eventType": "payment.v1.captured",
              "payload": {
                "paymentId": "pay_001",
                "merchantId": "merchant_demo"
              }
            }
            """.trimIndent(),
        )

        verify(webhookService, never()).createDelivery(any(), any(), any(), any(), any())
    }
}
