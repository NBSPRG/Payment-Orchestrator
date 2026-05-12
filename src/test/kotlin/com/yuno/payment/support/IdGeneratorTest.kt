package com.yuno.payment.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdGeneratorTest {
    @Test
    fun `generates compact ids`() {
        val paymentId = IdGenerator.paymentId()
        val attemptId = IdGenerator.attemptId()
        val apiKeyId = IdGenerator.apiKeyId()

        assertThat(paymentId).hasSize(26)
        assertThat(attemptId).hasSize(26)
        assertThat(apiKeyId).hasSize(26)
        assertThat(paymentId).doesNotContain("-")
    }
}
