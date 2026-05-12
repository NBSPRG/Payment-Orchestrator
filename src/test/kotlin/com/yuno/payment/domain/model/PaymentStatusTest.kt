package com.yuno.payment.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaymentStatusTest {
    @Test
    fun `allows expected happy path transitions`() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.PROCESSING)).isTrue()
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.CAPTURED)).isTrue()
    }

    @Test
    fun `rejects terminal state rewinds`() {
        assertThat(PaymentStatus.CAPTURED.canTransitionTo(PaymentStatus.PROCESSING)).isFalse()
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.CAPTURED)).isFalse()
    }
}
