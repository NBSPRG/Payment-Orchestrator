package com.yuno.payment.security

import com.yuno.payment.persistence.entity.MerchantApiKeyEntity
import com.yuno.payment.persistence.repository.MerchantApiKeyRepository
import com.yuno.payment.support.IdGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

data class RotatedApiKey(
    val apiKey: String,
    val keyPrefix: String,
    val environment: String,
    val createdAt: Instant,
)

@Service
class ApiKeyRotationService(
    private val repository: MerchantApiKeyRepository,
    private val apiKeyHasher: ApiKeyHasher,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun rotate(merchantId: String, environment: String, rateLimitTier: String): RotatedApiKey {
        val rawKey = generateApiKey()
        val keyPrefix = rawKey.take(8)
        val createdAt = Instant.now()

        repository.save(
            MerchantApiKeyEntity(
                id = IdGenerator.apiKeyId(),
                merchantId = merchantId,
                keyPrefix = keyPrefix,
                keyHash = apiKeyHasher.hash(rawKey),
                environment = environment,
                status = "ACTIVE",
                rateLimitTier = rateLimitTier,
                createdAt = createdAt,
            ),
        )

        return RotatedApiKey(
            apiKey = rawKey,
            keyPrefix = keyPrefix,
            environment = environment,
            createdAt = createdAt,
        )
    }

    private fun generateApiKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "yk_$token"
    }
}
