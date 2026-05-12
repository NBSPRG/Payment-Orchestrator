package com.yuno.payment.security

import com.yuno.payment.persistence.entity.MerchantApiKeyEntity
import com.yuno.payment.persistence.entity.MerchantEntity
import com.yuno.payment.persistence.repository.MerchantApiKeyRepository
import com.yuno.payment.persistence.repository.MerchantRepository
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

class ApiKeyAuthenticationFilterTest {
    private val keyRepository = mock<MerchantApiKeyRepository>()
    private val merchantRepository = mock<MerchantRepository>()
    private val hasher = ApiKeyHasher()
    private val filter = ApiKeyAuthenticationFilter(keyRepository, merchantRepository, hasher)

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `authenticates valid API key`() {
        val rawKey = "test_api_key_123"
        whenever(keyRepository.findByKeyPrefixAndStatus(rawKey.take(8), "ACTIVE")).thenReturn(
            MerchantApiKeyEntity(
                merchantId = "merchant_demo",
                keyPrefix = rawKey.take(8),
                keyHash = hasher.hash(rawKey),
                environment = "TEST",
                status = "ACTIVE",
                rateLimitTier = "STANDARD",
            ),
        )
        whenever(merchantRepository.findById("merchant_demo")).thenReturn(
            Optional.of(MerchantEntity(id = "merchant_demo", status = "ACTIVE")),
        )
        val request = MockHttpServletRequest("GET", "/api/v1/payments").apply {
            addHeader("X-API-Key", rawKey)
        }
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication.principal).isInstanceOf(MerchantPrincipal::class.java)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `rejects missing API key`() {
        val request = MockHttpServletRequest("GET", "/api/v1/payments")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
        verify(chain, never()).doFilter(request, response)
    }

    @Test
    fun `rejects invalid API key`() {
        whenever(keyRepository.findByKeyPrefixAndStatus("bad_key_", "ACTIVE")).thenReturn(null)
        val request = MockHttpServletRequest("GET", "/api/v1/payments").apply {
            addHeader("X-API-Key", "bad_key_value")
        }
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
        verify(chain, never()).doFilter(request, response)
    }
}
