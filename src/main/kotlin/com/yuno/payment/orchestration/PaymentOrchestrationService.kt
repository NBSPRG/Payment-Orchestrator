package com.yuno.payment.orchestration

import com.yuno.payment.api.v1.dto.CreatePaymentRequest
import com.yuno.payment.api.v1.dto.PaymentResponse
import com.yuno.payment.api.v1.mapper.toResponse
import com.yuno.payment.domain.exception.ConcurrentPaymentProcessingException
import com.yuno.payment.domain.exception.PaymentNotFoundException
import com.yuno.payment.domain.model.Money
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.idempotency.IdempotencyService
import com.yuno.payment.idempotency.RedisPaymentLock
import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.entity.PaymentStatusHistoryEntity
import com.yuno.payment.persistence.repository.PaymentRepository
import com.yuno.payment.persistence.repository.PaymentStatusHistoryRepository
import com.yuno.payment.provider.model.ProviderResult
import com.yuno.payment.routing.RoutingEngine
import com.yuno.payment.support.IdGenerator
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Result wrapper that tells the controller whether this is a new payment (201)
 * or an idempotent replay (200).
 */
data class CreatePaymentResult(
    val response: PaymentResponse,
    val isNew: Boolean,
)

@Service
class PaymentOrchestrationService(
    private val idempotencyService: IdempotencyService,
    private val paymentRepository: PaymentRepository,
    private val historyRepository: PaymentStatusHistoryRepository,
    private val routingEngine: RoutingEngine,
    private val retryCoordinator: RetryCoordinator,
    private val stateMachine: PaymentStateMachine,
    private val outboxPublisher: OutboxPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val eventPublisher: ApplicationEventPublisher,
    private val paymentLock: RedisPaymentLock,
    private val paymentMetrics: PaymentMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ----- Create Payment -----

    fun createPayment(
        request: CreatePaymentRequest,
        merchantId: String,
        idempotencyKey: String,
    ): CreatePaymentResult {
        // Idempotent replay: return cached response with isNew=false
        idempotencyService.getCachedResponse(merchantId, idempotencyKey, request)
            ?.let {
                paymentMetrics.recordIdempotencyHit()
                return CreatePaymentResult(it, isNew = false)
            }

        return runCatching { createNewPayment(request, merchantId, idempotencyKey) }
            .getOrElse { failure ->
                if (failure !is DataIntegrityViolationException) throw failure
                // Concurrent duplicate — return existing payment
                val existing = paymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                    ?: throw failure
                CreatePaymentResult(existing.toResponse(), isNew = false)
            }
    }

    private fun createNewPayment(
        request: CreatePaymentRequest,
        merchantId: String,
        idempotencyKey: String,
    ): CreatePaymentResult {
        val payment = createInitiatedPayment(request, merchantId, idempotencyKey)
        paymentMetrics.recordPaymentCreated(payment.paymentMethodType.name, merchantId)
        val response = payment.toResponse()
        idempotencyService.storeResult(merchantId, idempotencyKey, request, response)
        return CreatePaymentResult(response, isNew = true)
    }

    // ----- Provider Processing -----

    fun processPayment(paymentId: String): PaymentResponse? {
        return withConcurrentPaymentGuard(paymentId) {
            paymentLock.withPaymentLock(paymentId) {
                val payment = paymentRepository.findById(paymentId).orElse(null) ?: return@withPaymentLock null
                if (payment.status in PaymentStatus.terminalStatuses) {
                    return@withPaymentLock payment.toResponse()
                }
                if (payment.status != PaymentStatus.INITIATED) {
                    log.info("Skipping provider processing for payment={} status={}", payment.id, payment.status)
                    return@withPaymentLock payment.toResponse()
                }

                val processingPayment = transitionToProcessing(payment)
                val finalPayment = runCatching {
                    val providerChain = routingEngine.resolveProviderChain(
                        paymentMethodType = processingPayment.paymentMethodType,
                        amount = Money(processingPayment.amountValue, processingPayment.amountCurrency),
                    )
                    val result = retryCoordinator.executeWithFailover(processingPayment, providerChain)
                    persistProviderResult(processingPayment, result)
                }.getOrElse { error ->
                    log.error("Provider processing failed for payment={}", processingPayment.id, error)
                    persistProviderResult(
                        processingPayment,
                        ProviderResult(
                            status = PaymentStatus.FAILED,
                            providerName = "UNAVAILABLE",
                            providerTransactionId = null,
                            retryable = true,
                            errorCode = "PROVIDER_PROCESSING_ERROR",
                            errorMessage = error.message,
                        ),
                    )
                }

                eventPublisher.publishEvent(PaymentStatusChangedApplicationEvent(finalPayment))
                finalPayment.toResponse()
            }
        }
    }

    // ----- Cancel Payment -----

    fun cancelPayment(paymentId: String, merchantId: String): PaymentResponse {
        val cancelled = withConcurrentPaymentGuard(paymentId) {
            paymentLock.withPaymentLock(paymentId) {
                val payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                    ?: throw PaymentNotFoundException(paymentId)

                transactionTemplate.execute {
                    val updated = stateMachine.transition(payment, PaymentStatus.CANCELLED, reason = "Cancelled by merchant")
                    outboxPublisher.publish(updated, PaymentEventType.CANCELLED)
                    updated
                }!!
            }
        }

        eventPublisher.publishEvent(PaymentStatusChangedApplicationEvent(cancelled))
        return cancelled.toResponse()
    }

    // ----- Refund Payment -----

    fun refundPayment(paymentId: String, merchantId: String): PaymentResponse {
        val refunded = withConcurrentPaymentGuard(paymentId) {
            paymentLock.withPaymentLock(paymentId) {
                val payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                    ?: throw PaymentNotFoundException(paymentId)

                val providerName = payment.providerName
                    ?: error("Payment $paymentId has no provider - cannot refund")

                val refundResult = retryCoordinator.executeRefund(payment, providerName)

                transactionTemplate.execute {
                    val target = if (refundResult.status == PaymentStatus.REFUNDED) PaymentStatus.REFUNDED else PaymentStatus.FAILED
                    val updated = stateMachine.transition(payment, target, result = refundResult)
                    outboxPublisher.publish(updated, if (target == PaymentStatus.REFUNDED) PaymentEventType.REFUNDED else PaymentEventType.FAILED)
                    updated
                }!!
            }
        }

        eventPublisher.publishEvent(PaymentStatusChangedApplicationEvent(refunded))
        return refunded.toResponse()
    }

    // ----- Internal helpers -----

    private fun createInitiatedPayment(
        request: CreatePaymentRequest,
        merchantId: String,
        idempotencyKey: String,
    ): PaymentEntity = transactionTemplate.execute {
        val payment = paymentRepository.save(
            PaymentEntity(
                id = IdGenerator.paymentId(),
                merchantId = merchantId,
                idempotencyKey = idempotencyKey,
                merchantReference = request.merchantReference,
                status = PaymentStatus.INITIATED,
                paymentMethodType = request.paymentMethod.type,
                amountValue = request.amount.value,
                amountCurrency = request.amount.currency,
                description = request.description,
                webhookUrl = request.webhookUrl,
                returnUrl = request.returnUrl,
                metadata = request.metadata,
            ),
        )
        historyRepository.save(
            PaymentStatusHistoryEntity(
                paymentId = payment.id,
                fromStatus = null,
                toStatus = PaymentStatus.INITIATED,
                reason = "Payment created",
            ),
        )
        outboxPublisher.publish(payment, PaymentEventType.INITIATED)
        payment
    }!!

    private fun transitionToProcessing(payment: PaymentEntity): PaymentEntity = transactionTemplate.execute {
        val updated = stateMachine.transition(payment, PaymentStatus.PROCESSING, "Provider processing started")
        outboxPublisher.publish(updated, PaymentEventType.PROCESSING)
        updated
    }!!

    private fun persistProviderResult(payment: PaymentEntity, result: ProviderResult): PaymentEntity =
        transactionTemplate.execute {
            val target = if (result.status == PaymentStatus.CAPTURED) PaymentStatus.CAPTURED else PaymentStatus.FAILED
            val updated = stateMachine.transition(payment, target, result = result)
            outboxPublisher.publish(updated, if (target == PaymentStatus.CAPTURED) PaymentEventType.CAPTURED else PaymentEventType.FAILED)
            updated
        }!!

    private fun <T> withConcurrentPaymentGuard(paymentId: String, block: () -> T): T =
        try {
            block()
        } catch (ex: OptimisticLockingFailureException) {
            log.warn("Optimistic locking conflict while processing payment={}", paymentId, ex)
            throw ConcurrentPaymentProcessingException(paymentId)
        }
}
