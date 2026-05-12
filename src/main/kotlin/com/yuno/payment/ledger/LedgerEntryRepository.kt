package com.yuno.payment.ledger

import org.springframework.data.jpa.repository.JpaRepository

interface LedgerEntryRepository : JpaRepository<LedgerEntryEntity, Long> {
}
