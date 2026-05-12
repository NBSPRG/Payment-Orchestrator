package com.yuno.payment.persistence.repository

import com.yuno.payment.persistence.entity.PaymentStatusHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentStatusHistoryRepository : JpaRepository<PaymentStatusHistoryEntity, Long>
