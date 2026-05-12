package com.yuno.payment.orchestration

import com.yuno.payment.domain.exception.InvalidStateTransitionException
import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.entity.PaymentStatusHistoryEntity
import com.yuno.payment.persistence.repository.PaymentRepository
import com.yuno.payment.persistence.repository.PaymentStatusHistoryRepository
import com.yuno.payment.provider.model.ProviderResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PaymentStateMachineTest {
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var historyRepository: PaymentStatusHistoryRepository
    private lateinit var stateMachine: PaymentStateMachine

    @BeforeEach
    fun setup() {
        paymentRepository = mock()
        historyRepository = mock()
        whenever(paymentRepository.save(any<PaymentEntity>())).thenAnswer { it.arguments[0] }
        whenever(historyRepository.save(any<PaymentStatusHistoryEntity>())).thenAnswer { it.arguments[0] }
        stateMachine = PaymentStateMachine(paymentRepository, historyRepository)
    }

    @Test
    fun `valid transition updates status and writes history`() {
        val payment = payment(status = PaymentStatus.INITIATED)

        val updated = stateMachine.transition(payment, PaymentStatus.PROCESSING, "started")

        assertThat(updated.status).isEqualTo(PaymentStatus.PROCESSING)
        val history = argumentCaptor<PaymentStatusHistoryEntity>()
        verify(historyRepository).save(history.capture())
        assertThat(history.firstValue.fromStatus).isEqualTo(PaymentStatus.INITIATED)
        assertThat(history.firstValue.toStatus).isEqualTo(PaymentStatus.PROCESSING)
    }

    @Test
    fun `provider result is copied onto payment`() {
        val payment = payment(status = PaymentStatus.PROCESSING)

        stateMachine.transition(
            payment,
            PaymentStatus.CAPTURED,
            result = ProviderResult(PaymentStatus.CAPTURED, "PROVIDER_A", "txn_001"),
        )

        assertThat(payment.providerName).isEqualTo("PROVIDER_A")
        assertThat(payment.providerTransactionId).isEqualTo("txn_001")
    }

    @Test
    fun `invalid transition throws`() {
        assertThrows(InvalidStateTransitionException::class.java) {
            stateMachine.transition(payment(status = PaymentStatus.CAPTURED), PaymentStatus.PROCESSING)
        }
    }

    private fun payment(status: PaymentStatus) = PaymentEntity(
        id = "pay_001",
        merchantId = "merchant_demo",
        idempotencyKey = "idem_001",
        status = status,
        paymentMethodType = PaymentMethodType.CARD,
        amountValue = 1000,
        amountCurrency = "INR",
    )
}
