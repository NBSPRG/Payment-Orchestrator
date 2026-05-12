package com.yuno.payment.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PaymentMetricsTest {
    @Test
    fun `records business metrics`() {
        val registry = SimpleMeterRegistry()
        val metrics = PaymentMetrics(registry)

        metrics.recordPaymentCreated("CARD", "merchant_demo")
        metrics.recordProviderLatency("PROVIDER_A", "CAPTURED", Duration.ofMillis(12))
        metrics.recordIdempotencyHit()
        metrics.recordIdempotencyConflict()
        metrics.recordProviderFailure("PROVIDER_A", "TIMEOUT")
        metrics.recordPaymentStatus("CAPTURED")
        metrics.recordOutboxPublished(Instant.now().minusMillis(10))
        metrics.recordOutboxFailure(deadLettered = false)
        metrics.recordWebhookDelivery("PENDING", retryable = true)
        metrics.recordReconciliation("captured")

        assertThat(registry.find("payments.created").counter()!!.count()).isEqualTo(1.0)
        assertThat(registry.find("payment.provider.latency").timer()!!.count()).isEqualTo(1)
        assertThat(registry.find("payments.idempotency.hits").counter()!!.count()).isEqualTo(1.0)
        assertThat(registry.find("payment.webhook.deliveries").counter()!!.count()).isEqualTo(1.0)
    }
}
