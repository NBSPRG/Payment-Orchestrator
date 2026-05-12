package com.yuno.payment.security

import com.yuno.payment.persistence.entity.MerchantApiKeyEntity
import com.yuno.payment.persistence.repository.MerchantApiKeyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ApiKeyRotationServiceTest {
    private lateinit var repository: MerchantApiKeyRepository
    private lateinit var service: ApiKeyRotationService

    @BeforeEach
    fun setup() {
        repository = mock()
        whenever(repository.save(any<MerchantApiKeyEntity>())).thenAnswer { it.arguments[0] }
        service = ApiKeyRotationService(repository, ApiKeyHasher())
    }

    @Test
    fun `rotate creates active key and returns raw key once`() {
        val result = service.rotate("merchant_demo", "TEST", "STANDARD")

        val captor = argumentCaptor<MerchantApiKeyEntity>()
        verify(repository).save(captor.capture())

        val saved = captor.firstValue
        assertThat(result.apiKey).startsWith("yk_")
        assertThat(result.keyPrefix).isEqualTo(result.apiKey.take(8))
        assertThat(saved.keyPrefix).isEqualTo(result.keyPrefix)
        assertThat(saved.keyHash).isNotEqualTo(result.apiKey)
        assertThat(saved.status).isEqualTo("ACTIVE")
        assertThat(saved.merchantId).isEqualTo("merchant_demo")
    }
}
