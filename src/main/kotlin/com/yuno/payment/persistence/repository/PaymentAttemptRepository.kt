package com.yuno.payment.persistence.repository

import com.yuno.payment.persistence.entity.PaymentAttemptEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentAttemptRepository : JpaRepository<PaymentAttemptEntity, String>
