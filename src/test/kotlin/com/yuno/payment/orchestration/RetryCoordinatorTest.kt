package com.yuno.payment.orchestration

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentAttemptEntity
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.repository.PaymentAttemptRepository
import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.provider.ProviderConnector
import com.yuno.payment.provider.ProviderConnectorRegistry
import com.yuno.payment.provider.model.ProviderResult
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RetryCoordinatorTest {

    private lateinit var coordinator: RetryCoordinator
    private lateinit var registry: ProviderConnectorRegistry
    private lateinit var attemptRepository: PaymentAttemptRepository
    private lateinit var providerA: ProviderConnector
    private lateinit var providerB: ProviderConnector
    private lateinit var paymentMetrics: PaymentMetrics

    @BeforeEach
    fun setup() {
        providerA = mock()
        providerB = mock()
        whenever(providerA.name).thenReturn("PROVIDER_A")
        whenever(providerB.name).thenReturn("PROVIDER_B")
        registry = ProviderConnectorRegistry(listOf(providerA, providerB))
        attemptRepository = mock()
        whenever(attemptRepository.save(any<PaymentAttemptEntity>())).thenAnswer { it.arguments[0] }
        paymentMetrics = mock()

        coordinator = RetryCoordinator(
            providerRegistry = registry,
            paymentAttemptRepository = attemptRepository,
            circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults(),
            retryRegistry = RetryRegistry.ofDefaults(),
            timeLimiterRegistry = TimeLimiterRegistry.ofDefaults(),
            providerExecutor = Runnable::run,
            paymentMetrics = paymentMetrics,
        )
    }

    private fun testPayment() = PaymentEntity(
        id = "pay_test_123",
        merchantId = "merchant_demo",
        idempotencyKey = "idem_001",
        status = PaymentStatus.PROCESSING,
        paymentMethodType = PaymentMethodType.CARD,
        amountValue = 10000,
        amountCurrency = "INR",
    )

    @Test
    fun `happy path - provider A succeeds`() {
        whenever(providerA.charge(any())).thenReturn(
            ProviderResult(PaymentStatus.CAPTURED, "PROVIDER_A", "txn_001"),
        )

        val result = coordinator.executeWithFailover(testPayment(), listOf("PROVIDER_A", "PROVIDER_B"))

        assertEquals(PaymentStatus.CAPTURED, result.status)
        assertEquals("PROVIDER_A", result.providerName)
        assertFalse(result.retryable)
    }

    @Test
    fun `failover - provider A fails retryable, provider B succeeds`() {
        whenever(providerA.charge(any())).thenReturn(
            ProviderResult(PaymentStatus.FAILED, "PROVIDER_A", null, retryable = true, errorCode = "PROVIDER_ERROR"),
        )
        whenever(providerB.charge(any())).thenReturn(
            ProviderResult(PaymentStatus.CAPTURED, "PROVIDER_B", "txn_002"),
        )

        val result = coordinator.executeWithFailover(testPayment(), listOf("PROVIDER_A", "PROVIDER_B"))

        assertEquals(PaymentStatus.CAPTURED, result.status)
        assertEquals("PROVIDER_B", result.providerName)
    }

    @Test
    fun `non-retryable error - stops immediately`() {
        whenever(providerA.charge(any())).thenReturn(
            ProviderResult(PaymentStatus.FAILED, "PROVIDER_A", null, retryable = false, errorCode = "CARD_DECLINED"),
        )

        val result = coordinator.executeWithFailover(testPayment(), listOf("PROVIDER_A", "PROVIDER_B"))

        assertEquals(PaymentStatus.FAILED, result.status)
        assertEquals("PROVIDER_A", result.providerName)
        assertFalse(result.retryable)
    }

    @Test
    fun `all providers fail - returns last result`() {
        whenever(providerA.charge(any())).thenReturn(
            ProviderResult(PaymentStatus.FAILED, "PROVIDER_A", null, retryable = true, errorCode = "TIMEOUT"),
        )
        whenever(providerB.charge(any())).thenReturn(
            ProviderResult(PaymentStatus.FAILED, "PROVIDER_B", null, retryable = true, errorCode = "TIMEOUT"),
        )

        val result = coordinator.executeWithFailover(testPayment(), listOf("PROVIDER_A", "PROVIDER_B"))

        assertEquals(PaymentStatus.FAILED, result.status)
        assertTrue(result.retryable)
    }
}
