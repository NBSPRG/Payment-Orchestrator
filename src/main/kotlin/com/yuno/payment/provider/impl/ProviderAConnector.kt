package com.yuno.payment.provider.impl

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.provider.ProviderConnector
import com.yuno.payment.provider.model.ProviderResult
import com.yuno.payment.support.IdGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProviderAConnector : ProviderConnector {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "PROVIDER_A"
    override val supportedPaymentMethods: Set<PaymentMethodType> = setOf(PaymentMethodType.CARD, PaymentMethodType.UPI)

    override fun charge(payment: PaymentEntity): ProviderResult {
        log.info("PROVIDER_A: Charging payment={} amount={} {}", payment.id, payment.amountValue, payment.amountCurrency)
        // Simulated successful charge
        return ProviderResult(
            status = PaymentStatus.CAPTURED,
            providerName = name,
            providerTransactionId = "pa_${IdGenerator.paymentId()}",
        )
    }

    override fun refund(payment: PaymentEntity): ProviderResult {
        log.info("PROVIDER_A: Refunding payment={}", payment.id)
        return ProviderResult(
            status = PaymentStatus.REFUNDED,
            providerName = name,
            providerTransactionId = "pa_ref_${IdGenerator.paymentId()}",
        )
    }

    override fun getStatus(providerTransactionId: String): ProviderResult {
        log.info("PROVIDER_A: Checking status for txn={}", providerTransactionId)
        return ProviderResult(
            status = PaymentStatus.CAPTURED,
            providerName = name,
            providerTransactionId = providerTransactionId,
        )
    }
}
