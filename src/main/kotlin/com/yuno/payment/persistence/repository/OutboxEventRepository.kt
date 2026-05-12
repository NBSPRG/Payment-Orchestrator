package com.yuno.payment.persistence.repository

import com.yuno.payment.persistence.entity.OutboxEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, Long> {
    @Query(
        value = """
            UPDATE outbox_events
            SET claimed_at = now(),
                claimed_by = :claimedBy
            WHERE id IN (
                SELECT id
                FROM outbox_events
                WHERE published = FALSE
                  AND retry_count < max_retries
                  AND (claimed_at IS NULL OR claimed_at < :claimExpiredBefore)
                ORDER BY id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
        """,
        nativeQuery = true,
    )
    fun claimPendingEvents(
        @Param("claimedBy") claimedBy: String,
        @Param("claimExpiredBefore") claimExpiredBefore: Instant,
        @Param("batchSize") batchSize: Int,
    ): List<OutboxEventEntity>
}
