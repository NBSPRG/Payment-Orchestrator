package com.yuno.payment.api.v1.dto

import com.yuno.payment.domain.model.PaymentMethodType
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.YearMonth

class PaymentMethodRequestValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `accepts card details for card payments`() {
        val request = PaymentMethodRequest(
            type = PaymentMethodType.CARD,
            card = CardRequest(
                number = "4111111111111111",
                expiryMonth = "12",
                expiryYear = "2027",
                cvv = "123",
                holderName = "Demo User",
            ),
        )

        assertThat(validator.validate(request)).isEmpty()
    }

    @Test
    fun `rejects card payment without card details`() {
        val request = PaymentMethodRequest(type = PaymentMethodType.CARD)

        assertThat(validator.validate(request).map { it.message })
            .contains("card details are required only for CARD payments")
    }

    @Test
    fun `rejects mixed card and upi details`() {
        val request = PaymentMethodRequest(
            type = PaymentMethodType.UPI,
            card = CardRequest(
                number = "4111111111111111",
                expiryMonth = "12",
                expiryYear = "2027",
                cvv = "123",
            ),
            upi = UpiRequest("demo@yuno"),
        )

        assertThat(validator.validate(request).map { it.message })
            .contains("card details are required only for CARD payments")
    }

    @Test
    fun `rejects card expiry in the past`() {
        val past = YearMonth.now().minusMonths(1)
        val request = CardRequest(
            number = "4111111111111111",
            expiryMonth = "%02d".format(past.monthValue),
            expiryYear = past.year.toString(),
            cvv = "123",
        )

        assertThat(validator.validate(request).map { it.message })
            .contains("card expiry must not be in the past")
    }

    @Test
    fun `rejects oversized metadata`() {
        val request = CreatePaymentRequest(
            amount = MoneyRequest(value = 1000, currency = "INR"),
            paymentMethod = PaymentMethodRequest(
                type = PaymentMethodType.UPI,
                upi = UpiRequest("demo@yuno"),
            ),
            metadata = (1..51).associate { "key$it" to it },
        )

        assertThat(validator.validate(request).map { it.message })
            .contains("metadata must contain at most 50 entries")
    }

    @Test
    fun `rejects non-https webhook url`() {
        val request = CreatePaymentRequest(
            amount = MoneyRequest(value = 1000, currency = "INR"),
            paymentMethod = PaymentMethodRequest(
                type = PaymentMethodType.UPI,
                upi = UpiRequest("demo@yuno"),
            ),
            webhookUrl = "http://merchant.example/webhook",
        )

        assertThat(validator.validate(request).map { it.message })
            .contains("webhookUrl must be a valid HTTPS URL")
    }

    @Test
    fun `rejects unsupported currency`() {
        val request = MoneyRequest(value = 1000, currency = "JPY")

        assertThat(validator.validate(request).map { it.message })
            .contains("currency is not supported")
    }
}
