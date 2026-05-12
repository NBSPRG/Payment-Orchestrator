package com.yuno.payment.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.concurrent.TimeUnit

class RateLimitFilterTest {
    private val redisTemplate = mock<StringRedisTemplate>()
    private val valueOps = mock<ValueOperations<String, String>>()
    private val filter = RateLimitFilter(redisTemplate)

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `allows request within rate limit`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment("ratelimit:merchant_demo:payments")).thenReturn(1)
        val principal = MerchantPrincipal("merchant_demo", "TEST", "STANDARD")
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null)
        val request = MockHttpServletRequest("GET", "/api/v1/payments")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        verify(redisTemplate).expire("ratelimit:merchant_demo:payments", 1, TimeUnit.MINUTES)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `rejects request over rate limit`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment("ratelimit:merchant_demo:payments")).thenReturn(61)
        val principal = MerchantPrincipal("merchant_demo", "TEST", "STANDARD")
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null)
        val request = MockHttpServletRequest("GET", "/api/v1/payments")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        org.junit.jupiter.api.Assertions.assertEquals(429, response.status)
        verify(chain, never()).doFilter(request, response)
    }
}
