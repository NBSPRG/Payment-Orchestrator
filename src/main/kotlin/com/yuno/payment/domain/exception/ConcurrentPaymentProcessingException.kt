package com.yuno.payment.domain.exception

class ConcurrentPaymentProcessingException(paymentId: String) :
    RuntimeException("Payment $paymentId is already being processed concurrently")
