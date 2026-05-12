package com.yuno.payment.ledger

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Double-entry ledger service. Each financial event creates paired entries.
 * Idempotent: duplicate events (same eventId) are safely ignored via unique constraint.
 */
@Service
class LedgerService(
    private val repository: LedgerEntryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun recordCapture(
        paymentId: String,
        merchantId: String,
        amountValue: Long,
        amountCurrency: String,
        eventId: String,
    ) {
        createEntry(
            paymentId = paymentId,
            merchantId = merchantId,
            entryType = "DEBIT",
            amountValue = amountValue,
            amountCurrency = amountCurrency,
            eventId = eventId,
            eventType = "payment.v1.captured",
            description = "Payment captured — customer charged",
        )
        createEntry(
            paymentId = paymentId,
            merchantId = merchantId,
            entryType = "CREDIT",
            amountValue = amountValue,
            amountCurrency = amountCurrency,
            eventId = eventId,
            eventType = "payment.v1.captured",
            description = "Payment captured — merchant credited",
        )
        log.info("Ledger: Recorded capture entries for payment={} amount={} {}", paymentId, amountValue, amountCurrency)
    }

    @Transactional
    fun recordRefund(
        paymentId: String,
        merchantId: String,
        amountValue: Long,
        amountCurrency: String,
        eventId: String,
    ) {
        createEntry(
            paymentId = paymentId,
            merchantId = merchantId,
            entryType = "CREDIT",
            amountValue = amountValue,
            amountCurrency = amountCurrency,
            eventId = eventId,
            eventType = "payment.v1.refunded",
            description = "Refund — customer credited",
        )
        createEntry(
            paymentId = paymentId,
            merchantId = merchantId,
            entryType = "DEBIT",
            amountValue = amountValue,
            amountCurrency = amountCurrency,
            eventId = eventId,
            eventType = "payment.v1.refunded",
            description = "Refund — merchant debited",
        )
        log.info("Ledger: Recorded refund entries for payment={} amount={} {}", paymentId, amountValue, amountCurrency)
    }

    private fun createEntry(
        paymentId: String,
        merchantId: String,
        entryType: String,
        amountValue: Long,
        amountCurrency: String,
        eventId: String,
        eventType: String,
        description: String,
    ) {
        try {
            repository.save(
                LedgerEntryEntity(
                    paymentId = paymentId,
                    merchantId = merchantId,
                    entryType = entryType,
                    amountValue = amountValue,
                    amountCurrency = amountCurrency,
                    eventId = eventId,
                    eventType = eventType,
                    description = description,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            log.info("Ledger: Duplicate entry skipped for eventId={} entryType={}", eventId, entryType)
        }
    }
}
