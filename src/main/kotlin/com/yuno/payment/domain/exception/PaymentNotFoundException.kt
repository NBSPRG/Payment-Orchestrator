package com.yuno.payment.domain.exception

class PaymentNotFoundException(paymentId: String) :
    RuntimeException("Payment $paymentId was not found")
