package com.yuno.payment.messaging.consumer

import com.yuno.payment.webhook.WebhookService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer for webhook delivery.
 * Listens to terminal payment events and fires webhooks to merchant URLs.
 */
@Component
class WebhookConsumer(
    private val webhookService: WebhookService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["payment.v1.captured", "payment.v1.failed", "payment.v1.refunded", "payment.v1.cancelled"],
        groupId = "webhook-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentEvent(message: String) {
        try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val envelope = mapper.readValue(message, Map::class.java) as Map<String, Any?>
            val eventType = envelope["eventType"] as? String ?: return
            val payload = envelope["payload"] as? Map<String, Any?> ?: return
            val paymentId = payload["paymentId"] as? String ?: return
            val merchantId = payload["merchantId"] as? String ?: return

            // Look up webhook URL from the payment (it's stored in the payment entity)
            // For now, we include webhookUrl in the event payload if available
            val webhookUrl = payload["webhookUrl"] as? String
            if (webhookUrl.isNullOrBlank()) {
                log.debug("No webhookUrl for payment={}, skipping webhook delivery", paymentId)
                return
            }

            webhookService.createDelivery(
                paymentId = paymentId,
                merchantId = merchantId,
                webhookUrl = webhookUrl,
                eventType = eventType,
                payload = envelope,
            )
        } catch (e: Exception) {
            log.error("WebhookConsumer: Failed to process event", e)
        }
    }
}
