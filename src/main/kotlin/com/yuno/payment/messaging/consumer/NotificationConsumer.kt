package com.yuno.payment.messaging.consumer

import com.yuno.payment.notification.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer for notification side-effects.
 * Listens to captured and failed payment events and triggers notifications.
 */
@Component
class NotificationConsumer(
    private val notificationService: NotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["payment.v1.captured"],
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentCaptured(message: String) {
        processEvent(message) { payload ->
            val amount = payload["amount"] as? Map<*, *> ?: return@processEvent
            notificationService.sendPaymentConfirmation(
                paymentId = payload["paymentId"] as String,
                merchantId = payload["merchantId"] as String,
                amount = (amount["value"] as Number).toLong(),
                currency = amount["currency"] as String,
            )
        }
    }

    @KafkaListener(
        topics = ["payment.v1.failed"],
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentFailed(message: String) {
        processEvent(message) { payload ->
            notificationService.sendPaymentFailure(
                paymentId = payload["paymentId"] as String,
                merchantId = payload["merchantId"] as String,
                reason = payload["errorMessage"] as? String,
            )
        }
    }

    @KafkaListener(
        topics = ["payment.v1.refunded"],
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentRefunded(message: String) {
        processEvent(message) { payload ->
            val amount = payload["amount"] as? Map<*, *> ?: return@processEvent
            notificationService.sendRefundConfirmation(
                paymentId = payload["paymentId"] as String,
                merchantId = payload["merchantId"] as String,
                amount = (amount["value"] as Number).toLong(),
                currency = amount["currency"] as String,
            )
        }
    }

    private fun processEvent(message: String, handler: (Map<String, Any?>) -> Unit) {
        try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val envelope = mapper.readValue(message, Map::class.java) as Map<String, Any?>
            val payload = envelope["payload"] as? Map<String, Any?> ?: return
            handler(payload)
        } catch (e: Exception) {
            log.error("NotificationConsumer: Failed to process event", e)
        }
    }
}
