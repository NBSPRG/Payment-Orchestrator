package com.yuno.payment.ledger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LedgerServiceTest {

    private lateinit var repository: LedgerEntryRepository
    private lateinit var service: LedgerService

    @BeforeEach
    fun setup() {
        repository = mock()
        whenever(repository.save(any<LedgerEntryEntity>())).thenAnswer { it.arguments[0] }
        service = LedgerService(repository)
    }

    @Test
    fun `recordCapture creates debit and credit entries`() {
        service.recordCapture(
            paymentId = "pay_001",
            merchantId = "merchant_demo",
            amountValue = 10000,
            amountCurrency = "INR",
            eventId = "evt_001",
        )

        val captor = argumentCaptor<LedgerEntryEntity>()
        verify(repository, times(2)).save(captor.capture())

        val entries = captor.allValues
        assertEquals("DEBIT", entries[0].entryType)
        assertEquals("CREDIT", entries[1].entryType)
        assertEquals(10000L, entries[0].amountValue)
        assertEquals(10000L, entries[1].amountValue)
        assertEquals("evt_001", entries[0].eventId)
        assertEquals("evt_001", entries[1].eventId)
    }

    @Test
    fun `recordRefund creates credit and debit entries (reversal)`() {
        service.recordRefund(
            paymentId = "pay_002",
            merchantId = "merchant_demo",
            amountValue = 5000,
            amountCurrency = "INR",
            eventId = "evt_002",
        )

        val captor = argumentCaptor<LedgerEntryEntity>()
        verify(repository, times(2)).save(captor.capture())

        val entries = captor.allValues
        assertEquals("CREDIT", entries[0].entryType) // Refund: customer credited
        assertEquals("DEBIT", entries[1].entryType)  // Refund: merchant debited
    }
}
