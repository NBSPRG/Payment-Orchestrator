package com.yuno.payment.support

import java.util.UUID

object IdGenerator {
    fun paymentId(): String = compactId()
    fun attemptId(): String = compactId()
    fun apiKeyId(): String = compactId()

    private fun compactId(): String = UUID.randomUUID().toString().replace("-", "").take(26)
}
