package com.yuno.payment.realtime

import com.yuno.payment.api.v1.mapper.toResponse
import com.yuno.payment.orchestration.PaymentStatusChangedApplicationEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class WebSocketPaymentStatusPublisher(
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @EventListener
    fun publishStatusChange(event: PaymentStatusChangedApplicationEvent) {
        val payment = event.payment
        messagingTemplate.convertAndSend(
            "/topic/payments/${payment.merchantId}/${payment.id}",
            payment.toResponse(),
        )
    }
}
