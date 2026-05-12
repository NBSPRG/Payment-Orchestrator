package com.yuno.payment.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiKeyHasherTest {
    private val hasher = ApiKeyHasher()

    @Test
    fun `hash is stable and does not expose raw key`() {
        val hash = hasher.hash("test_api_key_123")

        assertThat(hash).isEqualTo(hasher.hash("test_api_key_123"))
        assertThat(hash).isNotEqualTo("test_api_key_123")
        assertThat(hash).hasSize(64)
    }
}
