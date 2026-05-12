package com.yuno.payment.orchestration

import com.yuno.payment.domain.exception.ConcurrentPaymentProcessingException
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.repository.PaymentRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PaymentProcessingJobTest {
    @Test
    fun `processes initiated payments`() {
        val repository = mock<PaymentRepository>()
        val service = mock<PaymentOrchestrationService>()
        whenever(repository.findTop50ByStatusOrderByCreatedAtAsc(PaymentStatus.INITIATED))
            .thenReturn(listOf(PaymentEntity(id = "pay_001")))

        PaymentProcessingJob(repository, service).processInitiatedPayments()

        verify(service).processPayment("pay_001")
    }

    @Test
    fun `continues when payment is already locked`() {
        val repository = mock<PaymentRepository>()
        val service = mock<PaymentOrchestrationService>()
        whenever(repository.findTop50ByStatusOrderByCreatedAtAsc(PaymentStatus.INITIATED))
            .thenReturn(listOf(PaymentEntity(id = "pay_001")))
        whenever(service.processPayment("pay_001")).thenThrow(ConcurrentPaymentProcessingException("pay_001"))

        PaymentProcessingJob(repository, service).processInitiatedPayments()

        verify(service).processPayment("pay_001")
    }
}
