package com.yuno.payment.api.v1.mapper

import com.yuno.payment.api.v1.dto.MoneyResponse
import com.yuno.payment.api.v1.dto.PaymentResponse
import com.yuno.payment.persistence.entity.PaymentEntity

fun PaymentEntity.toResponse(): PaymentResponse =
    PaymentResponse(
        id = id,
        merchantId = merchantId,
        status = status,
        amount = MoneyResponse(amountValue, amountCurrency),
        paymentMethodType = paymentMethodType,
        providerName = providerName,
        providerTransactionId = providerTransactionId,
        merchantReference = merchantReference,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
