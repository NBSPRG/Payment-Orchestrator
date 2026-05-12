package com.yuno.payment.api.v1

import com.yuno.payment.security.ApiKeyRotationService
import com.yuno.payment.security.MerchantPrincipal
import com.yuno.payment.security.RotatedApiKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class MerchantApiKeyControllerTest {
    @Test
    fun `rotate returns raw API key once`() {
        val service = mock<ApiKeyRotationService>()
        whenever(service.rotate("merchant_demo", "TEST", "STANDARD")).thenReturn(
            RotatedApiKey("yk_secret", "yk_secre", "TEST", Instant.EPOCH),
        )
        val controller = MerchantApiKeyController(service)

        val response = controller.rotateApiKey(MerchantPrincipal("merchant_demo", "TEST", "STANDARD"))

        assertThat(response.apiKey).isEqualTo("yk_secret")
        assertThat(response.keyPrefix).isEqualTo("yk_secre")
    }
}
