package com.yuno.payment.domain.exception

class IdempotencyConflictException :
    RuntimeException("Idempotency key was already used with a different request body")
