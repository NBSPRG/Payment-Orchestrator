package com.yuno.payment.idempotency

import com.yuno.payment.persistence.repository.IdempotencyKeyRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class IdempotencyCleanupJobTest {
    @Test
    fun `deletes expired idempotency keys`() {
        val repository = mock<IdempotencyKeyRepository>()
        whenever(repository.deleteByExpiresAtBefore(any<Instant>())).thenReturn(3)
        val job = IdempotencyCleanupJob(repository)

        job.deleteExpiredKeys()

        verify(repository).deleteByExpiresAtBefore(any<Instant>())
    }
}
