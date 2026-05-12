package com.yuno.payment.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RequestBodySizeFilter(
    @Value("\${payment.api.max-request-body-bytes:1048576}")
    private val maxRequestBodyBytes: Long,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val contentLength = request.contentLengthLong
        if (contentLength > maxRequestBodyBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body is too large")
            return
        }
        filterChain.doFilter(request, response)
    }
}
