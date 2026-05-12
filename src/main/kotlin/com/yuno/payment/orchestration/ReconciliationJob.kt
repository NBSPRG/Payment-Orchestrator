package com.yuno.payment.orchestration

import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.persistence.repository.PaymentRepository
import com.yuno.payment.provider.ProviderConnectorRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Reconciliation job that resolves payments stuck in non-terminal states.
 * Runs every 5 minutes, polls providers for actual status.
 */
@Component
class ReconciliationJob(
    private val paymentRepository: PaymentRepository,
    private val providerRegistry: ProviderConnectorRegistry,
    private val stateMachine: PaymentStateMachine,
    private val outboxPublisher: OutboxPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val paymentMetrics: PaymentMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${payment.reconciliation.interval-ms:300000}")
    fun reconcileStuckPayments() {
        val cutoff = Instant.now().minus(10, ChronoUnit.MINUTES)
        val stuckStatuses = listOf(PaymentStatus.PROCESSING, PaymentStatus.RETRY_PENDING)
        val stuckPayments = paymentRepository.findByStatusInAndUpdatedAtBefore(stuckStatuses, cutoff)

        if (stuckPayments.isEmpty()) return

        log.info("Reconciliation: Found {} stuck payments", stuckPayments.size)

        for (payment in stuckPayments) {
            try {
                val providerName = payment.providerName
                if (providerName == null) {
                    log.warn("Reconciliation: Payment {} has no provider, marking FAILED", payment.id)
                    transactionTemplate.execute {
                        stateMachine.transition(payment, PaymentStatus.FAILED, "Reconciliation: no provider assigned")
                        outboxPublisher.publish(payment, PaymentEventType.FAILED)
                    }
                    paymentMetrics.recordReconciliation("failed_no_provider")
                    continue
                }

                val connector = providerRegistry.get(providerName)
                val providerTxnId = payment.providerTransactionId
                if (providerTxnId == null) {
                    log.warn("Reconciliation: Payment {} has no provider txn ID, marking FAILED", payment.id)
                    transactionTemplate.execute {
                        stateMachine.transition(payment, PaymentStatus.FAILED, "Reconciliation: no provider transaction ID")
                        outboxPublisher.publish(payment, PaymentEventType.FAILED)
                    }
                    paymentMetrics.recordReconciliation("failed_no_provider_transaction")
                    continue
                }

                val result = connector.getStatus(providerTxnId)
                transactionTemplate.execute {
                    val targetStatus = result.status
                    stateMachine.transition(payment, targetStatus, "Reconciliation: resolved from provider", result)
                    val eventType = when (targetStatus) {
                        PaymentStatus.CAPTURED -> PaymentEventType.CAPTURED
                        PaymentStatus.FAILED -> PaymentEventType.FAILED
                        else -> PaymentEventType.STATUS_CHANGED
                    }
                    outboxPublisher.publish(payment, eventType)
                }
                paymentMetrics.recordReconciliation(result.status.name.lowercase())
                log.info("Reconciliation: Payment {} resolved to {}", payment.id, result.status)
            } catch (e: Exception) {
                paymentMetrics.recordReconciliation("error")
                log.error("Reconciliation: Failed to reconcile payment {}", payment.id, e)
            }
        }
    }
}
