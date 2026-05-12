package com.yuno.payment.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Custom Micrometer metrics for payment business observability.
 * These metrics are exported to Prometheus via the /actuator/prometheus endpoint.
 */
@Component
class PaymentMetrics(
    private val meterRegistry: MeterRegistry,
) {
    fun recordPaymentCreated(paymentMethodType: String, merchantId: String) {
        Counter.builder("payments.created")
            .tag("payment_method", paymentMethodType)
            .tag("merchant_id", merchantId)
            .description("Total payments created")
            .register(meterRegistry)
            .increment()
    }

    fun recordProviderLatency(providerName: String, status: String, duration: Duration) {
        Timer.builder("payment.provider.latency")
            .tag("provider", providerName)
            .tag("status", status)
            .description("Provider call latency")
            .register(meterRegistry)
            .record(duration)
    }

    fun recordIdempotencyHit() {
        Counter.builder("payments.idempotency.hits")
            .description("Duplicate requests caught by idempotency")
            .register(meterRegistry)
            .increment()
    }

    fun recordIdempotencyConflict() {
        Counter.builder("payments.idempotency.conflicts")
            .description("Idempotency keys reused with different request bodies")
            .register(meterRegistry)
            .increment()
    }

    fun recordProviderFailure(providerName: String, errorCode: String?) {
        Counter.builder("payments.provider.failures")
            .tag("provider", providerName)
            .tag("error_code", errorCode ?: "unknown")
            .description("Provider call failures")
            .register(meterRegistry)
            .increment()
    }

    fun recordPaymentStatus(status: String) {
        Counter.builder("payments.status")
            .tag("status", status)
            .description("Payment final status counts")
            .register(meterRegistry)
            .increment()
    }

    fun recordOutboxPublished(lagFromCreatedAt: Instant?) {
        Counter.builder("payment.outbox.published")
            .description("Outbox events published")
            .register(meterRegistry)
            .increment()
        if (lagFromCreatedAt != null) {
            Timer.builder("payment.outbox.lag")
                .description("Outbox event lag from creation to publish")
                .register(meterRegistry)
                .record(Duration.between(lagFromCreatedAt, Instant.now()))
        }
    }

    fun recordOutboxFailure(deadLettered: Boolean) {
        Counter.builder("payment.outbox.failures")
            .tag("dead_lettered", deadLettered.toString())
            .description("Outbox publish failures")
            .register(meterRegistry)
            .increment()
    }

    fun recordWebhookDelivery(status: String, retryable: Boolean) {
        Counter.builder("payment.webhook.deliveries")
            .tag("status", status)
            .tag("retryable", retryable.toString())
            .description("Webhook delivery outcomes")
            .register(meterRegistry)
            .increment()
    }

    fun recordReconciliation(result: String) {
        Counter.builder("payment.reconciliation")
            .tag("result", result)
            .description("Payment reconciliation outcomes")
            .register(meterRegistry)
            .increment()
    }
}
