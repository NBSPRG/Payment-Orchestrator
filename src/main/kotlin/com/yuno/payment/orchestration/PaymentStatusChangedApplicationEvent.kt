package com.yuno.payment.orchestration

import com.yuno.payment.persistence.entity.PaymentEntity

data class PaymentStatusChangedApplicationEvent(
    val payment: PaymentEntity,
)
