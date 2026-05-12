package com.yuno.payment.orchestration

enum class PaymentEventType(val topic: String) {
    INITIATED("payment.v1.initiated"),
    PROCESSING("payment.v1.processing"),
    AUTHORIZED("payment.v1.authorized"),
    CAPTURED("payment.v1.captured"),
    FAILED("payment.v1.failed"),
    CANCELLED("payment.v1.cancelled"),
    REFUNDED("payment.v1.refunded"),
    STATUS_CHANGED("payment.v1.status_changed"),
}
