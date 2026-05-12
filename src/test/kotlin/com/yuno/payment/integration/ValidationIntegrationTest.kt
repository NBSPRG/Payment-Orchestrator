package com.yuno.payment.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class ValidationIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val apiKey = "test_api_key_123"

    @Test
    fun `invalid idempotency key returns validation error`() {
        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", "bad key")
            contentType = MediaType.APPLICATION_JSON
            content = validPaymentJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("validation_failed") }
        }
    }

    @Test
    fun `unsupported currency returns validation error`() {
        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", "validation-currency-001")
            contentType = MediaType.APPLICATION_JSON
            content = validPaymentJson(currency = "JPY")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("validation_failed") }
        }
    }

    @Test
    fun `non-https webhook url returns validation error`() {
        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", "validation-webhook-001")
            contentType = MediaType.APPLICATION_JSON
            content = validPaymentJson(extra = ""","webhookUrl":"http://merchant.example/webhook"""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("validation_failed") }
        }
    }

    private fun validPaymentJson(currency: String = "INR", extra: String = "") = """
        {
          "amount": { "value": 5000, "currency": "$currency" },
          "paymentMethod": {
            "type": "CARD",
            "card": {
              "number": "4111111111111111",
              "expiryMonth": "12",
              "expiryYear": "2027",
              "cvv": "123",
              "holderName": "Validation User"
            }
          },
          "merchantReference": "ORDER-VALIDATION-001"
          $extra
        }
    """.trimIndent()
}
