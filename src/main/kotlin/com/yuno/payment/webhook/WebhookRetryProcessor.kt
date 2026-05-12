package com.yuno.payment.webhook

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Scheduled processor that retries failed webhook deliveries.
 * Uses exponential backoff with delays configured in WebhookService.
 */
@Component
class WebhookRetryProcessor(
    private val repository: WebhookDeliveryRepository,
    private val webhookService: WebhookService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${payment.webhook.retry-interval-ms:30000}")
    fun retryPendingDeliveries() {
        val pending = repository.findByStatusAndNextRetryAtBefore("PENDING", Instant.now())

        if (pending.isNotEmpty()) {
            log.info("WebhookRetryProcessor: Found {} pending deliveries to retry", pending.size)
        }

        pending.forEach { delivery ->
            webhookService.attemptDelivery(delivery)
        }
    }
}
