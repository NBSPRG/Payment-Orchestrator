package com.yuno.payment.notification

import org.junit.jupiter.api.Test

class NotificationServiceTest {
    private val service = NotificationService()

    @Test
    fun `notification methods do not throw`() {
        service.sendPaymentConfirmation("pay_001", "merchant_demo", 1000, "INR")
        service.sendPaymentFailure("pay_001", "merchant_demo", null)
        service.sendRefundConfirmation("pay_001", "merchant_demo", 1000, "INR")
    }
}
