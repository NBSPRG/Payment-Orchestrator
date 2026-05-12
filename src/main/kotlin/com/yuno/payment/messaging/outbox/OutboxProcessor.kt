package com.yuno.payment.messaging.outbox

import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.persistence.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Production-grade outbox processor with:
 * - PostgreSQL row claiming with SKIP LOCKED for multi-instance safety
 * - Retry count tracking
 * - Dead-letter after max retries
 * - Duplicate publish protection via Kafka idempotent producer
 */
@Component
class OutboxProcessor(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val transactionTemplate: TransactionTemplate,
    private val paymentMetrics: PaymentMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val claimOwner = "${InetAddress.getLocalHost().hostName}-${UUID.randomUUID()}"
    private val claimTimeout = Duration.ofMinutes(5)

    @Scheduled(fixedDelayString = "\${payment.outbox.fixed-delay-ms:1000}")
    fun publishPendingEvents() {
        val pending = transactionTemplate.execute {
            outboxEventRepository.claimPendingEvents(
                claimedBy = claimOwner,
                claimExpiredBefore = Instant.now().minus(claimTimeout),
                batchSize = 100,
            )
        } ?: emptyList()

        pending.forEach { event ->
            runCatching {
                kafkaTemplate.send(event.kafkaTopic, event.kafkaKey, event.payload).get()
                transactionTemplate.execute {
                    event.published = true
                    event.publishedAt = Instant.now()
                    event.claimedAt = null
                    event.claimedBy = null
                    outboxEventRepository.save(event)
                    paymentMetrics.recordOutboxPublished(event.createdAt)
                }
            }.onFailure { error ->
                transactionTemplate.execute {
                    event.retryCount++
                    event.lastError = error.message?.take(2000)
                    event.claimedAt = null
                    event.claimedBy = null
                    if (event.retryCount >= event.maxRetries) {
                        paymentMetrics.recordOutboxFailure(deadLettered = true)
                        log.error(
                            "Outbox event id={} dead-lettered after {} retries: {}",
                            event.id, event.retryCount, error.message,
                        )
                    } else {
                        paymentMetrics.recordOutboxFailure(deadLettered = false)
                        log.warn(
                            "Failed to publish outbox event id={} (attempt {}/{}): {}",
                            event.id, event.retryCount, event.maxRetries, error.message,
                        )
                    }
                    outboxEventRepository.save(event)
                }
            }
        }
    }
}
