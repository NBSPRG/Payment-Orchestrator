package com.yuno.payment.api.v1

import com.yuno.payment.api.v1.dto.MoneyResponse
import com.yuno.payment.api.v1.dto.PaymentResponse
import com.yuno.payment.domain.exception.PaymentNotFoundException
import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.orchestration.CreatePaymentResult
import com.yuno.payment.orchestration.PaymentOrchestrationService
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.repository.PaymentRepository
import com.yuno.payment.security.MerchantPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant

class PaymentControllerTest {
    private val orchestrationService = mock<PaymentOrchestrationService>()
    private val paymentRepository = mock<PaymentRepository>()
    private val controller = PaymentController(orchestrationService, paymentRepository)
    private val principal = MerchantPrincipal("merchant_demo", "TEST", "STANDARD")

    @Test
    fun `create returns 201 for new payment`() {
        whenever(orchestrationService.createPayment(any(), any(), any()))
            .thenReturn(CreatePaymentResult(response(), isNew = true))

        val result = controller.createPayment(principal, "idem_001", mock())

        assertThat(result.statusCode.value()).isEqualTo(201)
    }

    @Test
    fun `get payment throws when missing`() {
        whenever(paymentRepository.findByIdAndMerchantId("missing", "merchant_demo")).thenReturn(null)

        assertThrows(PaymentNotFoundException::class.java) {
            controller.getPayment(principal, "missing")
        }
    }

    @Test
    fun `list payments maps page`() {
        whenever(paymentRepository.findByMerchantId("merchant_demo", PageRequest.of(0, 20)))
            .thenReturn(PageImpl(listOf(entity())))

        val page = controller.listPayments(principal, null, PageRequest.of(0, 20))

        assertThat(page.content).hasSize(1)
        assertThat(page.content[0].id).isEqualTo("pay_001")
    }

    private fun response() = PaymentResponse(
        id = "pay_001",
        merchantId = "merchant_demo",
        status = PaymentStatus.INITIATED,
        amount = MoneyResponse(1000, "INR"),
        paymentMethodType = PaymentMethodType.CARD,
        providerName = null,
        providerTransactionId = null,
        merchantReference = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun entity() = PaymentEntity(
        id = "pay_001",
        merchantId = "merchant_demo",
        idempotencyKey = "idem_001",
        status = PaymentStatus.INITIATED,
        paymentMethodType = PaymentMethodType.CARD,
        amountValue = 1000,
        amountCurrency = "INR",
    )
}
