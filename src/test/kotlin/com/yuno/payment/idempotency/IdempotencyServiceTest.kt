package com.yuno.payment.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.yuno.payment.api.v1.dto.MoneyResponse
import com.yuno.payment.api.v1.dto.PaymentResponse
import com.yuno.payment.domain.exception.IdempotencyConflictException
import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.IdempotencyKeyEntity
import com.yuno.payment.persistence.repository.IdempotencyKeyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class IdempotencyServiceTest {
    private lateinit var repository: IdempotencyKeyRepository
    private lateinit var service: IdempotencyService
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    @BeforeEach
    fun setup() {
        repository = mock()
        service = IdempotencyService(repository, objectMapper)
    }

    @Test
    fun `returns null when key is not cached`() {
        whenever(repository.findByMerchantIdAndIdempotencyKey("merchant", "key")).thenReturn(null)

        assertThat(service.getCachedResponse("merchant", "key", mapOf("amount" to 100))).isNull()
    }

    @Test
    fun `stores idempotency result`() {
        whenever(repository.findByMerchantIdAndIdempotencyKey("merchant", "key")).thenReturn(null)
        whenever(repository.save(any<IdempotencyKeyEntity>())).thenAnswer { it.arguments[0] }

        service.storeResult("merchant", "key", mapOf("amount" to 100), response())

        verify(repository).save(any<IdempotencyKeyEntity>())
    }

    @Test
    fun `returns cached response from stored response body`() {
        whenever(repository.findByMerchantIdAndIdempotencyKey("merchant", "key")).thenReturn(null)
        whenever(repository.save(any<IdempotencyKeyEntity>())).thenAnswer { it.arguments[0] }
        service.storeResult("merchant", "key", mapOf("amount" to 100), response())
        val saved = org.mockito.kotlin.argumentCaptor<IdempotencyKeyEntity>()
        verify(repository).save(saved.capture())
        whenever(repository.findByMerchantIdAndIdempotencyKey("merchant", "key")).thenReturn(saved.firstValue)

        val cached = service.getCachedResponse("merchant", "key", mapOf("amount" to 100))

        assertThat(cached?.amount?.currency).isEqualTo("INR")
        assertThat(cached?.providerName).isNull()
    }

    @Test
    fun `throws conflict when same key has different request hash`() {
        service.storeResult("merchant", "key", mapOf("amount" to 100), response())
        val saved = org.mockito.kotlin.argumentCaptor<IdempotencyKeyEntity>()
        verify(repository).save(saved.capture())
        whenever(repository.findByMerchantIdAndIdempotencyKey("merchant", "key")).thenReturn(saved.firstValue)

        assertThrows(IdempotencyConflictException::class.java) {
            service.getCachedResponse("merchant", "key", mapOf("amount" to 200))
        }
    }

    private fun response() = PaymentResponse(
        id = "pay_001",
        merchantId = "merchant",
        status = PaymentStatus.INITIATED,
        amount = MoneyResponse(100, "INR"),
        paymentMethodType = PaymentMethodType.CARD,
        providerName = null,
        providerTransactionId = null,
        merchantReference = "ORDER-1",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
