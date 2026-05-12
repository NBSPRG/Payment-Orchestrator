package com.yuno.payment.api.v1

import com.yuno.payment.security.ApiKeyRotationService
import com.yuno.payment.security.MerchantPrincipal
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class RotateApiKeyResponse(
    val apiKey: String,
    val keyPrefix: String,
    val environment: String,
    val createdAt: Instant,
)

@RestController
@RequestMapping("/api/v1/merchant/api-keys")
class MerchantApiKeyController(
    private val apiKeyRotationService: ApiKeyRotationService,
) {
    @PostMapping("/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Rotate API key", description = "Creates a new active API key. The raw key is returned once.")
    fun rotateApiKey(
        @AuthenticationPrincipal principal: MerchantPrincipal,
    ): RotateApiKeyResponse {
        val rotated = apiKeyRotationService.rotate(
            merchantId = principal.merchantId,
            environment = principal.environment,
            rateLimitTier = principal.rateLimitTier ?: "STANDARD",
        )
        return RotateApiKeyResponse(
            apiKey = rotated.apiKey,
            keyPrefix = rotated.keyPrefix,
            environment = rotated.environment,
            createdAt = rotated.createdAt,
        )
    }
}
