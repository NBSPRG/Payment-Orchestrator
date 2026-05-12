package com.yuno.payment.orchestration

import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentAttemptEntity
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.repository.PaymentAttemptRepository
import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.provider.ProviderConnectorRegistry
import com.yuno.payment.provider.ProviderErrorClassifier
import com.yuno.payment.provider.model.ProviderResult
import com.yuno.payment.support.IdGenerator
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@Component
class RetryCoordinator(
    private val providerRegistry: ProviderConnectorRegistry,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val timeLimiterRegistry: TimeLimiterRegistry,
    @Qualifier("providerExecutor")
    private val providerExecutor: Executor,
    private val paymentMetrics: PaymentMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun executeWithFailover(payment: PaymentEntity, providerChain: List<String>): ProviderResult {
        require(providerChain.isNotEmpty()) { "No provider route configured for ${payment.paymentMethodType}" }

        var lastResult: ProviderResult? = null
        for ((index, providerName) in providerChain.withIndex()) {
            val startedAt = Instant.now()
            val result = executeWithResilience(providerName) {
                providerRegistry.get(providerName).charge(payment)
            }
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
            paymentMetrics.recordProviderLatency(providerName, result.status.name, Duration.ofMillis(durationMs))
            if (result.status == PaymentStatus.FAILED) {
                paymentMetrics.recordProviderFailure(providerName, result.errorCode)
            }

            paymentAttemptRepository.save(
                PaymentAttemptEntity(
                    id = IdGenerator.attemptId(),
                    paymentId = payment.id,
                    attemptNumber = index + 1,
                    providerName = providerName,
                    status = result.status.name,
                    errorCode = result.errorCode,
                    errorMessage = result.errorMessage,
                    retryable = result.retryable,
                    durationMs = durationMs,
                ),
            )

            lastResult = result
            if (!result.retryable) return result

            log.warn(
                "Provider {} returned retryable error for payment={}, trying next provider",
                providerName, payment.id,
            )
        }
        return lastResult!!
    }

    fun executeRefund(payment: PaymentEntity, providerName: String): ProviderResult {
        return executeWithResilience(providerName) {
            providerRegistry.get(providerName).refund(payment)
        }
    }

    private fun executeWithResilience(providerName: String, block: () -> ProviderResult): ProviderResult {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(providerName)
        val retry = retryRegistry.retry(providerName)
        val timeLimiter = timeLimiterRegistry.timeLimiter(providerName)

        return try {
            io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(circuitBreaker) {
                io.github.resilience4j.retry.Retry.decorateSupplier(retry) {
                    TimeLimiter.decorateFutureSupplier(timeLimiter) {
                        CompletableFuture.supplyAsync({ block() }, providerExecutor)
                    }.call()
                }.get()
            }.get()
        } catch (e: Exception) {
            log.error("Provider {} call failed with exception: {}", providerName, e.message)
            val retryable = ProviderErrorClassifier.isRetryable(e)
            ProviderResult(
                status = PaymentStatus.FAILED,
                providerName = providerName,
                providerTransactionId = null,
                retryable = retryable,
                errorCode = "PROVIDER_ERROR",
                errorMessage = e.message,
            )
        }
    }
}
