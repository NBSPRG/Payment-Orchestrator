package com.yuno.payment.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Notification service — currently logs notifications.
 * In production, this would integrate with email/SMS providers.
 */
@Service
class NotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendPaymentConfirmation(paymentId: String, merchantId: String, amount: Long, currency: String) {
        log.info(
            "NOTIFICATION: Payment {} captured — {} {} for merchant {}",
            paymentId, amount, currency, merchantId,
        )
    }

    fun sendPaymentFailure(paymentId: String, merchantId: String, reason: String?) {
        log.info(
            "NOTIFICATION: Payment {} failed for merchant {} — reason: {}",
            paymentId, merchantId, reason ?: "unknown",
        )
    }

    fun sendRefundConfirmation(paymentId: String, merchantId: String, amount: Long, currency: String) {
        log.info(
            "NOTIFICATION: Refund for payment {} — {} {} for merchant {}",
            paymentId, amount, currency, merchantId,
        )
    }
}
