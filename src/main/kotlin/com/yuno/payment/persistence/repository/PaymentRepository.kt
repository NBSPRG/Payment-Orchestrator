package com.yuno.payment.persistence.repository

import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface PaymentRepository : JpaRepository<PaymentEntity, String> {
    fun findByIdAndMerchantId(id: String, merchantId: String): PaymentEntity?

    fun findByMerchantIdAndIdempotencyKey(merchantId: String, idempotencyKey: String): PaymentEntity?

    fun findByMerchantId(merchantId: String, pageable: Pageable): Page<PaymentEntity>

    fun findByMerchantIdAndStatus(merchantId: String, status: PaymentStatus, pageable: Pageable): Page<PaymentEntity>

    fun findByStatusInAndUpdatedAtBefore(statuses: Collection<PaymentStatus>, cutoff: Instant): List<PaymentEntity>

    fun findTop50ByStatusOrderByCreatedAtAsc(status: PaymentStatus): List<PaymentEntity>
}
