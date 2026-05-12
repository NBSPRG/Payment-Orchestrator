package com.yuno.payment.orchestration

import com.yuno.payment.domain.exception.InvalidStateTransitionException
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.entity.PaymentStatusHistoryEntity
import com.yuno.payment.persistence.repository.PaymentRepository
import com.yuno.payment.persistence.repository.PaymentStatusHistoryRepository
import com.yuno.payment.provider.model.ProviderResult
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PaymentStateMachine(
    private val paymentRepository: PaymentRepository,
    private val historyRepository: PaymentStatusHistoryRepository,
) {
    fun transition(
        payment: PaymentEntity,
        target: PaymentStatus,
        reason: String? = null,
        result: ProviderResult? = null,
    ): PaymentEntity {
        val current = payment.status
        if (!current.canTransitionTo(target)) {
            throw InvalidStateTransitionException(current, target)
        }

        payment.status = target
        payment.updatedAt = Instant.now()
        if (result != null) {
            payment.providerName = result.providerName
            payment.providerTransactionId = result.providerTransactionId
            payment.failureErrorCode = result.errorCode
            payment.failureReason = result.errorMessage
        }

        historyRepository.save(
            PaymentStatusHistoryEntity(
                paymentId = payment.id,
                fromStatus = current,
                toStatus = target,
                reason = reason ?: result?.errorMessage,
                metadata = mapOf("provider" to result?.providerName),
            ),
        )

        return paymentRepository.save(payment)
    }
}
