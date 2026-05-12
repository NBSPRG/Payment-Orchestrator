package com.yuno.payment.messaging.consumer

import com.yuno.payment.notification.NotificationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class NotificationConsumerTest {
    private val notificationService = mock<NotificationService>()
    private val consumer = NotificationConsumer(notificationService)

    @Test
    fun `captured event sends confirmation`() {
        consumer.onPaymentCaptured(event("payment.v1.captured"))

        verify(notificationService).sendPaymentConfirmation("pay_001", "merchant_demo", 1000, "INR")
    }

    @Test
    fun `failed event sends failure notification`() {
        consumer.onPaymentFailed(
            """
            {
              "payload": {
                "paymentId": "pay_001",
                "merchantId": "merchant_demo",
                "errorMessage": "declined"
              }
            }
            """.trimIndent(),
        )

        verify(notificationService).sendPaymentFailure("pay_001", "merchant_demo", "declined")
    }

    @Test
    fun `refunded event sends refund confirmation`() {
        consumer.onPaymentRefunded(event("payment.v1.refunded"))

        verify(notificationService).sendRefundConfirmation("pay_001", "merchant_demo", 1000, "INR")
    }

    private fun event(type: String) = """
        {
          "eventType": "$type",
          "payload": {
            "paymentId": "pay_001",
            "merchantId": "merchant_demo",
            "amount": { "value": 1000, "currency": "INR" }
          }
        }
    """.trimIndent()
}
