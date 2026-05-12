package com.yuno.payment.provider.model

import com.yuno.payment.domain.model.PaymentStatus

data class ProviderResult(
    val status: PaymentStatus,
    val providerName: String,
    val providerTransactionId: String?,
    val retryable: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
