package com.yuno.payment.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.orchestration.PaymentStatusChangedApplicationEvent
import com.yuno.payment.persistence.entity.PaymentEntity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate

class RealtimePublisherTest {
    @Test
    fun `publishes status change to websocket topic`() {
        val messagingTemplate = mock<SimpMessagingTemplate>()
        val publisher = WebSocketPaymentStatusPublisher(messagingTemplate)

        publisher.publishStatusChange(PaymentStatusChangedApplicationEvent(payment()))

        verify(messagingTemplate).convertAndSend(eq("/topic/payments/merchant_demo/pay_001"), any<Any>())
    }

    @Test
    fun `publishes status change to redis channel`() {
        val redisTemplate = mock<StringRedisTemplate>()
        val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
        val publisher = RedisPaymentStatusPublisher(redisTemplate, objectMapper)

        publisher.publishStatusChange(PaymentStatusChangedApplicationEvent(payment()))

        verify(redisTemplate).convertAndSend(eq("payments:merchant_demo:pay_001"), any<String>())
    }

    private fun payment() = PaymentEntity(
        id = "pay_001",
        merchantId = "merchant_demo",
        idempotencyKey = "idem_001",
        status = PaymentStatus.CAPTURED,
        paymentMethodType = PaymentMethodType.CARD,
        amountValue = 1000,
        amountCurrency = "INR",
    )
}
