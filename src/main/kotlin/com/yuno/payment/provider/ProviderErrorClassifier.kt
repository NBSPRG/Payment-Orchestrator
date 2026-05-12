package com.yuno.payment.provider

/**
 * Classifies provider errors into retryable and non-retryable categories.
 * Retryable errors trigger failover to the next provider in the chain.
 * Non-retryable errors stop immediately.
 */
object ProviderErrorClassifier {

    private val nonRetryableErrorCodes = setOf(
        "CARD_DECLINED",
        "INSUFFICIENT_FUNDS",
        "INVALID_CARD",
        "EXPIRED_CARD",
        "INVALID_CVV",
        "INVALID_AMOUNT",
        "FRAUD_DETECTED",
        "INVALID_ACCOUNT",
    )

    fun isRetryable(errorCode: String?): Boolean {
        if (errorCode == null) return true
        return errorCode !in nonRetryableErrorCodes
    }

    fun isRetryable(exception: Throwable): Boolean = when (exception) {
        is java.net.SocketTimeoutException -> true
        is java.net.ConnectException -> true
        is java.io.IOException -> true
        else -> false
    }
}
