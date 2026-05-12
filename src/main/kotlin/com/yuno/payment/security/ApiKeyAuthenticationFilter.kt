package com.yuno.payment.security

import com.yuno.payment.persistence.repository.MerchantApiKeyRepository
import com.yuno.payment.persistence.repository.MerchantRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class ApiKeyAuthenticationFilter(
    private val apiKeyRepository: MerchantApiKeyRepository,
    private val merchantRepository: MerchantRepository,
    private val apiKeyHasher: ApiKeyHasher,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator") ||
            request.requestURI.startsWith("/api/v1/health") ||
            request.requestURI.startsWith("/ws/payments") ||
            request.requestURI.startsWith("/swagger") ||
            request.requestURI.startsWith("/v3/api-docs")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val rawKey = request.getHeader("X-API-Key")
        if (rawKey.isNullOrBlank()) {
            log.warn("Authentication failed: missing API key path={} method={}", request.requestURI, request.method)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key")
            return
        }

        val keyPrefix = rawKey.take(8)
        val key = apiKeyRepository.findByKeyPrefixAndStatus(keyPrefix, "ACTIVE")
        val validKey = key != null &&
            key.keyHash == apiKeyHasher.hash(rawKey) &&
            (key.expiresAt == null || key.expiresAt!!.isAfter(Instant.now()))

        if (!validKey || key == null) {
            log.warn("Authentication failed: invalid API key prefix={} path={}", keyPrefix, request.requestURI)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key")
            return
        }

        val merchant = merchantRepository.findById(key.merchantId).orElse(null)
        if (merchant == null || merchant.status != "ACTIVE") {
            log.warn("Authentication failed: inactive merchant={} path={}", key.merchantId, request.requestURI)
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Merchant is not active")
            return
        }

        val suppliedMerchantId = request.getHeader("X-Merchant-Id")
        if (!suppliedMerchantId.isNullOrBlank() && suppliedMerchantId != merchant.id) {
            log.warn("Authentication failed: merchant header mismatch supplied={} actual={}", suppliedMerchantId, merchant.id)
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "X-Merchant-Id does not match API key")
            return
        }

        val principal = MerchantPrincipal(
            merchantId = merchant.id,
            environment = key.environment,
            rateLimitTier = key.rateLimitTier ?: "STANDARD",
        )
        val authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        SecurityContextHolder.getContext().authentication = authentication
        log.info("Authenticated merchant={} environment={} path={}", merchant.id, key.environment, request.requestURI)
        filterChain.doFilter(request, response)
    }
}
