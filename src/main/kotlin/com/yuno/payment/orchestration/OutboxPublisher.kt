package com.yuno.payment.orchestration

import com.yuno.payment.domain.event.DomainEventEnvelope
import com.yuno.payment.persistence.entity.OutboxEventEntity
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.repository.OutboxEventRepository
import io.micrometer.tracing.Tracer
import org.springframework.stereotype.Component

@Component
class OutboxPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val tracer: Tracer?,
) {
    fun publish(payment: PaymentEntity, type: PaymentEventType) {
        val envelope = DomainEventEnvelope(
            eventType = type.topic,
            traceId = tracer?.currentSpan()?.context()?.traceId(),
            payload = mapOf(
                "paymentId" to payment.id,
                "merchantId" to payment.merchantId,
                "status" to payment.status.name,
                "amount" to mapOf(
                    "value" to payment.amountValue,
                    "currency" to payment.amountCurrency,
                ),
                "providerName" to payment.providerName,
                "providerTransactionId" to payment.providerTransactionId,
                "paymentMethodType" to payment.paymentMethodType.name,
            ),
        )

        outboxEventRepository.save(
            OutboxEventEntity(
                aggregateId = payment.id,
                eventType = type.topic,
                kafkaTopic = type.topic,
                kafkaKey = payment.merchantId,
                payload = mapOf(
                    "eventId" to envelope.eventId,
                    "eventVersion" to envelope.eventVersion,
                    "eventType" to envelope.eventType,
                    "occurredAt" to envelope.occurredAt.toString(),
                    "traceId" to envelope.traceId,
                    "producerService" to envelope.producerService,
                    "schemaVersion" to envelope.schemaVersion,
                    "payload" to envelope.payload,
                ),
            ),
        )
    }
}
