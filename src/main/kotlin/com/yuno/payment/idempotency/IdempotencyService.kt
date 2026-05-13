package com.yuno.payment.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.yuno.payment.api.v1.dto.PaymentResponse
import com.yuno.payment.domain.exception.IdempotencyConflictException
import com.yuno.payment.persistence.entity.IdempotencyKeyEntity
import com.yuno.payment.persistence.repository.IdempotencyKeyRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class IdempotencyService(
    private val repository: IdempotencyKeyRepository,
    private val objectMapper: ObjectMapper,
) {
    fun getCachedResponse(merchantId: String, idempotencyKey: String, request: Any): PaymentResponse? {
        val existing = repository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey) ?: return null
        if (existing.requestHash != hashRequest(request)) {
            throw IdempotencyConflictException()
        }
        return objectMapper.readValue(objectMapper.writeValueAsString(existing.responseBody))
    }

    fun storeResult(
        merchantId: String,
        idempotencyKey: String,
        request: Any,
        response: PaymentResponse,
    ) {
        val requestHash = hashRequest(request)
        val existing = repository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
        if (existing != null) {
            if (existing.requestHash != requestHash) {
                throw IdempotencyConflictException()
            }
            return
        }
        runCatching {
            repository.save(
                IdempotencyKeyEntity(
                    merchantId = merchantId,
                    idempotencyKey = idempotencyKey,
                    requestHash = requestHash,
                    responseStatus = 201,
                    responseBody = objectMapper.readValue(objectMapper.writeValueAsString(response)),
                    paymentId = response.id,
                    expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
                ),
            )
        }.onFailure { failure ->
            if (failure !is DataIntegrityViolationException) {
                throw failure
            }
            val duplicate = repository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
            if (duplicate == null || duplicate.requestHash != requestHash) {
                throw IdempotencyConflictException()
            }
        }
    }

    private fun hashRequest(request: Any): String {
        val canonical = objectMapper.writeValueAsBytes(request)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
