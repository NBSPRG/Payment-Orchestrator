package com.yuno.payment.orchestration

import com.yuno.payment.domain.exception.ConcurrentPaymentProcessingException
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Background processor for newly created payments.
 *
 * Payment creation stays fast and durable: the HTTP request creates an INITIATED
 * row, then this job performs provider processing under the per-payment lock.
 */
@Component
class PaymentProcessingJob(
    private val paymentRepository: PaymentRepository,
    private val orchestrationService: PaymentOrchestrationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${payment.processing.fixed-delay-ms:1000}")
    fun processInitiatedPayments() {
        val payments = paymentRepository.findTop50ByStatusOrderByCreatedAtAsc(PaymentStatus.INITIATED)
        if (payments.isEmpty()) {
            return
        }

        log.info("PaymentProcessingJob: found {} initiated payments to process", payments.size)
        payments.forEach { payment ->
            runCatching {
                orchestrationService.processPayment(payment.id)
            }.onFailure { error ->
                if (error is ConcurrentPaymentProcessingException) {
                    log.info("PaymentProcessingJob: payment={} is already being processed", payment.id)
                } else {
                    log.error("PaymentProcessingJob: failed to process payment={}", payment.id, error)
                }
            }
        }
    }
}
