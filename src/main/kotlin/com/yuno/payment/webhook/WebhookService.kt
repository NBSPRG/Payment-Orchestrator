package com.yuno.payment.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.persistence.repository.MerchantRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Webhook delivery service — sends payment events to merchant webhook URLs
 * with HMAC-SHA256 signature for verification.
 */
@Service
class WebhookService(
    private val repository: WebhookDeliveryRepository,
    private val merchantRepository: MerchantRepository,
    private val objectMapper: ObjectMapper,
    private val paymentMetrics: PaymentMetrics,
    restTemplateBuilder: RestTemplateBuilder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate: RestTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(3))
        .setReadTimeout(Duration.ofSeconds(5))
        .build()

    // Exponential backoff schedule: 1min, 5min, 30min, 2h, 24h
    private val retryDelaysMinutes = listOf(1L, 5L, 30L, 120L, 1440L)

    fun createDelivery(
        paymentId: String,
        merchantId: String,
        webhookUrl: String,
        eventType: String,
        payload: Map<String, Any?>,
    ) {
        val delivery = repository.save(
            WebhookDeliveryEntity(
                paymentId = paymentId,
                merchantId = merchantId,
                webhookUrl = webhookUrl,
                eventType = eventType,
                payload = payload,
                nextRetryAt = Instant.now(),
            ),
        )
        attemptDelivery(delivery)
    }

    fun attemptDelivery(delivery: WebhookDeliveryEntity) {
        try {
            val bodyJson = objectMapper.writeValueAsString(delivery.payload)
            val timestamp = Instant.now().epochSecond.toString()

            val merchant = merchantRepository.findById(delivery.merchantId).orElse(null)
            val secret = merchant?.webhookSecret ?: "default-webhook-secret"

            val signaturePayload = "$timestamp.$bodyJson"
            val signature = computeHmacSha256(secret, signaturePayload)

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Yuno-Signature", signature)
                set("X-Yuno-Timestamp", timestamp)
                set("X-Yuno-Event-Type", delivery.eventType)
            }

            val response = restTemplate.postForEntity(
                delivery.webhookUrl,
                HttpEntity(bodyJson, headers),
                String::class.java,
            )

            delivery.httpStatus = response.statusCode.value()
            delivery.attemptCount++
            delivery.status = "DELIVERED"
            delivery.updatedAt = Instant.now()
            repository.save(delivery)
            paymentMetrics.recordWebhookDelivery("DELIVERED", retryable = false)

            log.info(
                "Webhook delivered: payment={} url={} status={}",
                delivery.paymentId, delivery.webhookUrl, response.statusCode.value(),
            )
        } catch (e: HttpStatusCodeException) {
            handleDeliveryFailure(delivery, e, isRetryableStatus(e.statusCode))
        } catch (e: ResourceAccessException) {
            handleDeliveryFailure(delivery, e, retryable = true)
        } catch (e: Exception) {
            handleDeliveryFailure(delivery, e, retryable = false)
        }
    }

    private fun handleDeliveryFailure(delivery: WebhookDeliveryEntity, error: Exception, retryable: Boolean) {
        delivery.attemptCount++
        delivery.lastError = error.message?.take(2000)
        delivery.updatedAt = Instant.now()

        if (!retryable || delivery.attemptCount >= delivery.maxAttempts) {
            delivery.status = "FAILED"
            paymentMetrics.recordWebhookDelivery("FAILED", retryable)
            log.error(
                "Webhook permanently failed after {} attempts: payment={} retryable={}",
                delivery.attemptCount,
                delivery.paymentId,
                retryable,
            )
        } else {
            delivery.status = "PENDING"
            paymentMetrics.recordWebhookDelivery("PENDING", retryable = true)
            val delayIndex = (delivery.attemptCount - 1).coerceAtMost(retryDelaysMinutes.size - 1)
            delivery.nextRetryAt = Instant.now().plus(retryDelaysMinutes[delayIndex], ChronoUnit.MINUTES)
            log.warn(
                "Webhook attempt {} failed for payment={}, retrying at {}",
                delivery.attemptCount,
                delivery.paymentId,
                delivery.nextRetryAt,
            )
        }
        repository.save(delivery)
    }

    private fun isRetryableStatus(status: HttpStatusCode): Boolean =
        status.value() == 429 || status.is5xxServerError

    private fun computeHmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
