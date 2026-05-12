package com.yuno.payment.domain.model

enum class PaymentStatus {
    INITIATED,
    PROCESSING,
    AUTHORIZED,
    CAPTURED,
    RETRY_PENDING,
    FAILED,
    CANCELLED,
    REFUNDED;

    fun canTransitionTo(target: PaymentStatus): Boolean =
        validTransitions[this]?.contains(target) ?: false

    companion object {
        private val validTransitions = mapOf(
            INITIATED to setOf(PROCESSING, CANCELLED),
            PROCESSING to setOf(AUTHORIZED, CAPTURED, RETRY_PENDING, FAILED),
            AUTHORIZED to setOf(CAPTURED, FAILED),
            RETRY_PENDING to setOf(PROCESSING, FAILED),
            CAPTURED to setOf(REFUNDED),
            FAILED to emptySet(),
            CANCELLED to emptySet(),
            REFUNDED to emptySet(),
        )

        val terminalStatuses = setOf(CAPTURED, FAILED, CANCELLED, REFUNDED)
    }
}
