package com.yuno.payment.messaging.consumer

import com.yuno.payment.ledger.LedgerService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer for ledger side-effects.
 * Listens to captured and refunded payment events and records double-entry ledger rows.
 */
@Component
class LedgerConsumer(
    private val ledgerService: LedgerService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["payment.v1.captured"],
        groupId = "ledger-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentCaptured(message: String) {
        processEvent(message) { eventId, payload ->
            val amount = payload["amount"] as? Map<*, *> ?: return@processEvent
            ledgerService.recordCapture(
                paymentId = payload["paymentId"] as String,
                merchantId = payload["merchantId"] as String,
                amountValue = (amount["value"] as Number).toLong(),
                amountCurrency = amount["currency"] as String,
                eventId = eventId,
            )
        }
    }

    @KafkaListener(
        topics = ["payment.v1.refunded"],
        groupId = "ledger-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onPaymentRefunded(message: String) {
        processEvent(message) { eventId, payload ->
            val amount = payload["amount"] as? Map<*, *> ?: return@processEvent
            ledgerService.recordRefund(
                paymentId = payload["paymentId"] as String,
                merchantId = payload["merchantId"] as String,
                amountValue = (amount["value"] as Number).toLong(),
                amountCurrency = amount["currency"] as String,
                eventId = eventId,
            )
        }
    }

    private fun processEvent(message: String, handler: (String, Map<String, Any?>) -> Unit) {
        try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val envelope = mapper.readValue(message, Map::class.java) as Map<String, Any?>
            val eventId = envelope["eventId"] as? String ?: return
            val payload = envelope["payload"] as? Map<String, Any?> ?: return
            handler(eventId, payload)
        } catch (e: Exception) {
            log.error("LedgerConsumer: Failed to process event", e)
        }
    }
}
