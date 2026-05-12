package com.yuno.payment.provider

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.provider.model.ProviderResult

/**
 * Strategy interface for payment provider adapters.
 * Each provider implementation (PROVIDER_A, PROVIDER_B, etc.) implements this.
 */
interface ProviderConnector {
    val name: String
    val supportedPaymentMethods: Set<PaymentMethodType>
        get() = PaymentMethodType.entries.toSet()

    fun charge(payment: PaymentEntity): ProviderResult

    fun refund(payment: PaymentEntity): ProviderResult {
        throw UnsupportedOperationException("Refund not supported by provider $name")
    }

    fun getStatus(providerTransactionId: String): ProviderResult {
        throw UnsupportedOperationException("Status lookup not supported by provider $name")
    }
}
