package com.yuno.payment.idempotency

import com.yuno.payment.persistence.repository.IdempotencyKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class IdempotencyCleanupJob(
    private val repository: IdempotencyKeyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Scheduled(fixedDelayString = "\${payment.idempotency.cleanup-interval-ms:3600000}")
    fun deleteExpiredKeys() {
        val deleted = repository.deleteByExpiresAtBefore(Instant.now())
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency keys", deleted)
        }
    }
}
