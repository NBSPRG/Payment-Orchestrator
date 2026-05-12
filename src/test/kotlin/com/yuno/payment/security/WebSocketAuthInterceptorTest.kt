package com.yuno.payment.security

import com.yuno.payment.persistence.entity.MerchantApiKeyEntity
import com.yuno.payment.persistence.entity.MerchantEntity
import com.yuno.payment.persistence.repository.MerchantApiKeyRepository
import com.yuno.payment.persistence.repository.MerchantRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.server.ServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import java.net.URI
import java.util.Optional

class WebSocketAuthInterceptorTest {
    private val keyRepository = mock<MerchantApiKeyRepository>()
    private val merchantRepository = mock<MerchantRepository>()
    private val hasher = ApiKeyHasher()
    private val interceptor = WebSocketAuthInterceptor(keyRepository, merchantRepository, hasher)

    @Test
    fun `allows handshake without api key for stomp level auth`() {
        val attributes = mutableMapOf<String, Any>()

        val allowed = interceptor.beforeHandshake(request("ws://localhost/ws/payments"), mock(), mock(), attributes)

        assertThat(allowed).isTrue()
        assertThat(attributes).isEmpty()
    }

    @Test
    fun `valid api key populates merchant attributes`() {
        val rawKey = "test_api_key_123"
        whenever(keyRepository.findByKeyPrefixAndStatus(rawKey.take(8), "ACTIVE")).thenReturn(
            MerchantApiKeyEntity(
                merchantId = "merchant_demo",
                keyPrefix = rawKey.take(8),
                keyHash = hasher.hash(rawKey),
                environment = "TEST",
                status = "ACTIVE",
            ),
        )
        whenever(merchantRepository.findById("merchant_demo")).thenReturn(
            Optional.of(MerchantEntity(id = "merchant_demo", status = "ACTIVE")),
        )
        val attributes = mutableMapOf<String, Any>()

        val allowed = interceptor.beforeHandshake(
            request("ws://localhost/ws/payments?apiKey=$rawKey"),
            mock(),
            mock<WebSocketHandler>(),
            attributes,
        )

        assertThat(allowed).isTrue()
        assertThat(attributes["merchantId"]).isEqualTo("merchant_demo")
        assertThat(attributes["environment"]).isEqualTo("TEST")
    }

    private fun request(uri: String): ServerHttpRequest {
        val request = mock<ServerHttpRequest>()
        whenever(request.uri).thenReturn(URI.create(uri))
        return request
    }
}
