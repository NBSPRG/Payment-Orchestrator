package com.yuno.payment.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit

/**
 * Redis sliding-window rate limiter per merchant.
 * Uses the rate_limit_tier from the API key to determine the allowed request rate.
 */
@Component
class RateLimitFilter(
    private val redisTemplate: StringRedisTemplate,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    // Rate limits per tier (requests per minute)
    private val tierLimits = mapOf(
        "STANDARD" to 60L,
        "PREMIUM" to 300L,
        "ENTERPRISE" to 1000L,
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator") ||
            request.requestURI.startsWith("/ws/") ||
            request.requestURI.startsWith("/swagger") ||
            request.requestURI.startsWith("/v3/api-docs")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal = org.springframework.security.core.context.SecurityContextHolder
            .getContext().authentication?.principal

        if (principal is MerchantPrincipal) {
            val merchantId = principal.merchantId
            val tier = principal.rateLimitTier ?: "STANDARD"
            val limit = tierLimits[tier] ?: 60L

            val key = "ratelimit:$merchantId:payments"
            val current = redisTemplate.opsForValue().increment(key) ?: 1L

            if (current == 1L) {
                redisTemplate.expire(key, 1, TimeUnit.MINUTES)
            }

            response.setHeader("X-RateLimit-Limit", limit.toString())
            response.setHeader("X-RateLimit-Remaining", (limit - current).coerceAtLeast(0).toString())

            if (current > limit) {
                log.warn("Rate limit exceeded for merchant={} tier={} current={}", merchantId, tier, current)
                response.sendError(429, "Rate limit exceeded")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
