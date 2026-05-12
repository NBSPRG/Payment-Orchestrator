package com.yuno.payment.api.v1.dto

import com.yuno.payment.domain.model.PaymentStatus
import java.time.Instant

data class PaymentStatusResponse(
    val paymentId: String,
    val status: PaymentStatus,
    val updatedAt: Instant,
)
