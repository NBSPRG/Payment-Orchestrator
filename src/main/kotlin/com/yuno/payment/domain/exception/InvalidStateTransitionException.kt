package com.yuno.payment.domain.exception

import com.yuno.payment.domain.model.PaymentStatus

class InvalidStateTransitionException(from: PaymentStatus, to: PaymentStatus) :
    RuntimeException("Invalid payment status transition from $from to $to")
