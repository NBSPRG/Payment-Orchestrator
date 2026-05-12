package com.yuno.payment.ledger

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "ledger_entries")
class LedgerEntryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "payment_id")
    var paymentId: String = "",

    @Column(name = "merchant_id")
    var merchantId: String = "",

    @Column(name = "entry_type")
    var entryType: String = "",

    @Column(name = "amount_value")
    var amountValue: Long = 0,

    @Column(name = "amount_currency")
    var amountCurrency: String = "",

    @Column(name = "event_id")
    var eventId: String = "",

    @Column(name = "event_type")
    var eventType: String = "",

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),
)
