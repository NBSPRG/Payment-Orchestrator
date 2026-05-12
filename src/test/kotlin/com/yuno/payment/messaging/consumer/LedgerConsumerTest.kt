package com.yuno.payment.messaging.consumer

import com.yuno.payment.ledger.LedgerService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class LedgerConsumerTest {
    private val ledgerService = mock<LedgerService>()
    private val consumer = LedgerConsumer(ledgerService)

    @Test
    fun `captured event records capture ledger entries`() {
        consumer.onPaymentCaptured(event("payment.v1.captured"))

        verify(ledgerService).recordCapture(
            paymentId = "pay_001",
            merchantId = "merchant_demo",
            amountValue = 1000,
            amountCurrency = "INR",
            eventId = "evt_001",
        )
    }

    @Test
    fun `refunded event records refund ledger entries`() {
        consumer.onPaymentRefunded(event("payment.v1.refunded"))

        verify(ledgerService).recordRefund(
            paymentId = "pay_001",
            merchantId = "merchant_demo",
            amountValue = 1000,
            amountCurrency = "INR",
            eventId = "evt_001",
        )
    }

    @Test
    fun `invalid message is ignored`() {
        consumer.onPaymentCaptured("{bad-json")

        verify(ledgerService, never()).recordCapture(
            paymentId = "pay_001",
            merchantId = "merchant_demo",
            amountValue = 1000,
            amountCurrency = "INR",
            eventId = "evt_001",
        )
    }

    private fun event(type: String) = """
        {
          "eventId": "evt_001",
          "eventType": "$type",
          "payload": {
            "paymentId": "pay_001",
            "merchantId": "merchant_demo",
            "amount": { "value": 1000, "currency": "INR" }
          }
        }
    """.trimIndent()
}
