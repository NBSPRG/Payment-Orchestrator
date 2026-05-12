package com.yuno.payment.api

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestBodySizeFilterTest {
    @Test
    fun `rejects request body larger than configured limit`() {
        val filter = RequestBodySizeFilter(maxRequestBodyBytes = 10)
        val request = MockHttpServletRequest().apply {
            setContent(ByteArray(11))
        }
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        assertEquals(413, response.status)
        verify(chain, never()).doFilter(request, response)
    }

    @Test
    fun `allows request body within configured limit`() {
        val filter = RequestBodySizeFilter(maxRequestBodyBytes = 10)
        val request = MockHttpServletRequest().apply {
            setContent(ByteArray(10))
        }
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
    }
}
