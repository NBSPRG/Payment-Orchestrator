package com.yuno.payment.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.persistence.entity.MerchantEntity
import com.yuno.payment.persistence.repository.MerchantRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.util.Optional

class WebhookServiceTest {
    private lateinit var repository: WebhookDeliveryRepository
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var metrics: PaymentMetrics
    private lateinit var service: WebhookService
    private lateinit var server: MockRestServiceServer

    @BeforeEach
    fun setup() {
        repository = mock()
        merchantRepository = mock()
        metrics = mock()
        whenever(repository.save(any<WebhookDeliveryEntity>())).thenAnswer { it.arguments[0] }
        whenever(merchantRepository.findById("merchant_demo")).thenReturn(
            Optional.of(MerchantEntity(id = "merchant_demo", webhookSecret = "secret")),
        )
        service = WebhookService(repository, merchantRepository, ObjectMapper(), metrics, RestTemplateBuilder())
        val restTemplate = ReflectionTestUtils.getField(service, "restTemplate") as RestTemplate
        server = MockRestServiceServer.bindTo(restTemplate).build()
    }

    @Test
    fun `successful delivery marks webhook delivered and signs timestamp body`() {
        server.expect(requestTo("https://merchant.example/webhook"))
            .andExpect(header("X-Yuno-Event-Type", "payment.v1.captured"))
            .andRespond(withSuccess())

        service.createDelivery(
            paymentId = "pay_001",
            merchantId = "merchant_demo",
            webhookUrl = "https://merchant.example/webhook",
            eventType = "payment.v1.captured",
            payload = mapOf("paymentId" to "pay_001"),
        )

        val captor = argumentCaptor<WebhookDeliveryEntity>()
        verify(repository, org.mockito.kotlin.atLeastOnce()).save(captor.capture())
        assertThat(captor.lastValue.status).isEqualTo("DELIVERED")
        verify(metrics).recordWebhookDelivery("DELIVERED", false)
        server.verify()
    }

    @Test
    fun `retryable server error keeps delivery pending`() {
        server.expect(requestTo("https://merchant.example/webhook"))
            .andRespond(withServerError())

        service.attemptDelivery(
            WebhookDeliveryEntity(
                paymentId = "pay_001",
                merchantId = "merchant_demo",
                webhookUrl = "https://merchant.example/webhook",
                eventType = "payment.v1.failed",
                payload = mapOf("paymentId" to "pay_001"),
            ),
        )

        val captor = argumentCaptor<WebhookDeliveryEntity>()
        verify(repository).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo("PENDING")
        assertThat(captor.firstValue.nextRetryAt).isNotNull()
        verify(metrics).recordWebhookDelivery("PENDING", true)
    }
}
