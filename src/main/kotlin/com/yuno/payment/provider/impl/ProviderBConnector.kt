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
class ProviderBConnector : ProviderConnector {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "PROVIDER_B"
    override val supportedPaymentMethods: Set<PaymentMethodType> = setOf(PaymentMethodType.UPI, PaymentMethodType.CARD)

    override fun charge(payment: PaymentEntity): ProviderResult {
        log.info("PROVIDER_B: Charging payment={} amount={} {}", payment.id, payment.amountValue, payment.amountCurrency)
        return ProviderResult(
            status = PaymentStatus.CAPTURED,
            providerName = name,
            providerTransactionId = "pb_${IdGenerator.paymentId()}",
        )
    }

    override fun refund(payment: PaymentEntity): ProviderResult {
        log.info("PROVIDER_B: Refunding payment={}", payment.id)
        return ProviderResult(
            status = PaymentStatus.REFUNDED,
            providerName = name,
            providerTransactionId = "pb_ref_${IdGenerator.paymentId()}",
        )
    }

    override fun getStatus(providerTransactionId: String): ProviderResult {
        log.info("PROVIDER_B: Checking status for txn={}", providerTransactionId)
        return ProviderResult(
            status = PaymentStatus.CAPTURED,
            providerName = name,
            providerTransactionId = providerTransactionId,
        )
    }
}
