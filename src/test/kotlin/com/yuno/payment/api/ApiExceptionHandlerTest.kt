package com.yuno.payment.api

import com.yuno.payment.domain.exception.ConcurrentPaymentProcessingException
import com.yuno.payment.domain.exception.IdempotencyConflictException
import com.yuno.payment.domain.exception.InvalidStateTransitionException
import com.yuno.payment.domain.exception.PaymentNotFoundException
import com.yuno.payment.domain.model.PaymentStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiExceptionHandlerTest {
    private val handler = ApiExceptionHandler(tracer = null)

    @Test
    fun `maps domain exceptions to expected statuses`() {
        assertThat(handler.notFound(PaymentNotFoundException("pay_001")).statusCode.value()).isEqualTo(404)
        assertThat(handler.conflict(IdempotencyConflictException()).statusCode.value()).isEqualTo(409)
        assertThat(
            handler.invalidState(
                InvalidStateTransitionException(PaymentStatus.CAPTURED, PaymentStatus.PROCESSING),
            ).statusCode.value(),
        ).isEqualTo(422)
        assertThat(handler.concurrentProcessing(ConcurrentPaymentProcessingException("pay_001")).statusCode.value()).isEqualTo(409)
        assertThat(handler.invalidRequest(IllegalArgumentException("bad")).statusCode.value()).isEqualTo(400)
    }
}
