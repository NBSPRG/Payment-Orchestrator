package com.yuno.payment.api.v1.dto

import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import java.time.Instant
import java.time.YearMonth

data class CreatePaymentRequest(
    @field:Valid
    @field:NotNull
    val amount: MoneyRequest,

    @field:Valid
    @field:NotNull
    val paymentMethod: PaymentMethodRequest,

    @field:Size(max = 255)
    val merchantReference: String? = null,

    @field:Size(max = 500)
    val description: String? = null,

    val metadata: Map<String, Any?> = emptyMap(),

    @field:URL(protocol = "https", message = "webhookUrl must be a valid HTTPS URL")
    @field:Size(max = 2048)
    val webhookUrl: String? = null,

    @field:URL(message = "returnUrl must be a valid URL")
    @field:Size(max = 2048)
    val returnUrl: String? = null,
) {
    @AssertTrue(message = "metadata must contain at most 50 entries")
    fun isMetadataSizeValid(): Boolean = metadata.size <= 50
}

data class MoneyRequest(
    @field:Positive
    val value: Long,

    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    val currency: String,
) {
    @AssertTrue(message = "currency is not supported")
    fun isSupportedCurrency(): Boolean = currency in supportedCurrencies

    companion object {
        private val supportedCurrencies = setOf("INR", "USD", "EUR", "GBP")
    }
}

data class PaymentMethodRequest(
    @field:NotNull
    val type: PaymentMethodType,
    @field:Valid
    val card: CardRequest? = null,
    @field:Valid
    val upi: UpiRequest? = null,
) {
    @AssertTrue(message = "card details are required only for CARD payments")
    fun isCardDetailsValid(): Boolean =
        (type == PaymentMethodType.CARD && card != null && upi == null) ||
            (type != PaymentMethodType.CARD && card == null)

    @AssertTrue(message = "upi details are required only for UPI payments")
    fun isUpiDetailsValid(): Boolean =
        (type == PaymentMethodType.UPI && upi != null && card == null) ||
            (type != PaymentMethodType.UPI && upi == null)
}

data class CardRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{12,19}$", message = "card number must contain 12 to 19 digits")
    val number: String,
    @field:NotBlank
    @field:Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "expiryMonth must be 01-12")
    val expiryMonth: String,
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{4}$", message = "expiryYear must be 4 digits")
    val expiryYear: String,
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{3,4}$", message = "cvv must contain 3 or 4 digits")
    val cvv: String,
    @field:Size(max = 255)
    val holderName: String? = null,
) {
    @AssertTrue(message = "card expiry must not be in the past")
    fun isExpiryValid(): Boolean {
        val month = expiryMonth.toIntOrNull() ?: return true
        val year = expiryYear.toIntOrNull() ?: return true
        if (month !in 1..12) return true
        return !YearMonth.of(year, month).isBefore(YearMonth.now())
    }
}

data class UpiRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$", message = "vpa must be a valid UPI address")
    val vpa: String,
)

data class PaymentResponse(
    val id: String,
    val merchantId: String,
    val status: PaymentStatus,
    val amount: MoneyResponse,
    val paymentMethodType: PaymentMethodType,
    val providerName: String?,
    val providerTransactionId: String?,
    val merchantReference: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MoneyResponse(
    val value: Long,
    val currency: String,
)

data class ErrorResponse(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String? = null,
    val errorCode: String? = null,
    val traceId: String? = null,
    val timestamp: String? = null,
)
