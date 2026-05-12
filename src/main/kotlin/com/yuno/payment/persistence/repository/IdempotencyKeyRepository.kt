package com.yuno.payment.persistence.repository

import com.yuno.payment.persistence.entity.IdempotencyKeyEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKeyEntity, Long> {
    fun findByMerchantIdAndIdempotencyKey(merchantId: String, idempotencyKey: String): IdempotencyKeyEntity?

    fun deleteByExpiresAtBefore(cutoff: Instant): Long
}
