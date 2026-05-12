package com.yuno.payment.provider

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class ProviderErrorClassifierTest {
    @Test
    fun `classifies known business errors as non retryable`() {
        assertThat(ProviderErrorClassifier.isRetryable("CARD_DECLINED")).isFalse()
        assertThat(ProviderErrorClassifier.isRetryable("INVALID_AMOUNT")).isFalse()
    }

    @Test
    fun `classifies unknown provider codes as retryable`() {
        assertThat(ProviderErrorClassifier.isRetryable("PROVIDER_TIMEOUT")).isTrue()
        assertThat(ProviderErrorClassifier.isRetryable(null as String?)).isTrue()
    }

    @Test
    fun `classifies network exceptions as retryable`() {
        assertThat(ProviderErrorClassifier.isRetryable(SocketTimeoutException())).isTrue()
        assertThat(ProviderErrorClassifier.isRetryable(ConnectException())).isTrue()
        assertThat(ProviderErrorClassifier.isRetryable(IOException())).isTrue()
        assertThat(ProviderErrorClassifier.isRetryable(IllegalStateException())).isFalse()
    }
}
