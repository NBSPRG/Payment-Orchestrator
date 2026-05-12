package com.yuno.payment.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import com.yuno.payment.api.v1.mapper.toResponse
import com.yuno.payment.orchestration.PaymentStatusChangedApplicationEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RedisPaymentStatusPublisher(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun publishStatusChange(event: PaymentStatusChangedApplicationEvent) {
        val payment = event.payment
        val payload = objectMapper.writeValueAsString(payment.toResponse())
        runCatching {
            redisTemplate.convertAndSend("payments:${payment.merchantId}:${payment.id}", payload)
        }.onFailure {
            log.warn("Failed to publish Redis status update for paymentId={}", payment.id, it)
        }
    }
}
