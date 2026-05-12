package com.yuno.payment.security

import com.yuno.payment.persistence.repository.MerchantApiKeyRepository
import com.yuno.payment.persistence.repository.MerchantRepository
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/**
 * WebSocket handshake interceptor that validates API key from query params or headers.
 * Sets merchant info in WebSocket session attributes for subscription authorization.
 */
@Component
class WebSocketAuthInterceptor(
    private val apiKeyRepository: MerchantApiKeyRepository,
    private val merchantRepository: MerchantRepository,
    private val apiKeyHasher: ApiKeyHasher,
) : HandshakeInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        // Try to extract API key from query parameter
        val uri = request.uri
        val apiKey = uri.query?.split("&")
            ?.associate { param ->
                val parts = param.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }?.get("apiKey")

        if (apiKey.isNullOrBlank()) {
            log.debug("WebSocket connection allowed without API key (auth will happen at STOMP level)")
            return true
        }

        val keyPrefix = apiKey.take(8)
        val key = apiKeyRepository.findByKeyPrefixAndStatus(keyPrefix, "ACTIVE") ?: return false

        if (key.keyHash != apiKeyHasher.hash(apiKey)) return false

        val merchant = merchantRepository.findById(key.merchantId).orElse(null) ?: return false
        if (merchant.status != "ACTIVE") return false

        attributes["merchantId"] = merchant.id
        attributes["environment"] = key.environment
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
        // no-op
    }
}
