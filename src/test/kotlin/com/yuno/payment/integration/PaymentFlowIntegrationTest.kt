package com.yuno.payment.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Full integration test for the payment flow using real PostgreSQL, Redis, and Kafka.
 */
@AutoConfigureMockMvc
class PaymentFlowIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val apiKey = "test_api_key_123"

    @Test
    fun `create payment - happy path - returns 201`() {
        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", "test-idem-key-001")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "amount": { "value": 10000, "currency": "INR" },
                  "paymentMethod": {
                    "type": "CARD",
                    "card": {
                      "number": "4111111111111111",
                      "expiryMonth": "12",
                      "expiryYear": "2027",
                      "cvv": "123",
                      "holderName": "Test User"
                    }
                  },
                  "merchantReference": "ORDER-INT-001",
                  "description": "Integration test payment"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("INITIATED") }
            jsonPath("$.merchantId") { value("merchant_demo") }
        }
    }

    @Test
    fun `create payment - async processing captures card payment with provider A`() {
        val createResult = mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", "test-async-card-${System.nanoTime()}")
            contentType = MediaType.APPLICATION_JSON
            content = createCardPaymentJson("ORDER-ASYNC-CARD-${System.nanoTime()}")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("INITIATED") }
        }.andReturn()

        val paymentId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()
        waitUntilCaptured(paymentId)

        mockMvc.get("/api/v1/payments/$paymentId") {
            header("X-API-Key", apiKey)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CAPTURED") }
            jsonPath("$.providerName") { value("PROVIDER_A") }
            jsonPath("$.providerTransactionId") { exists() }
        }
    }

    @Test
    fun `idempotent replay - returns 200 with same response`() {
        val idempotencyKey = "test-idem-replay-001"

        // First request — 201
        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", idempotencyKey)
            contentType = MediaType.APPLICATION_JSON
            content = createCardPaymentJson("ORDER-REPLAY-001")
        }.andExpect {
            status { isCreated() }
        }

        // Same request — 200
        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", idempotencyKey)
            contentType = MediaType.APPLICATION_JSON
            content = createCardPaymentJson("ORDER-REPLAY-001")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `idempotency conflict - returns 409 when same key has different body`() {
        val idempotencyKey = "test-idem-conflict-001"

        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", idempotencyKey)
            contentType = MediaType.APPLICATION_JSON
            content = createCardPaymentJson("ORDER-CONFLICT-001")
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("/api/v1/payments") {
            header("X-API-Key", apiKey)
            header("X-Idempotency-Key", idempotencyKey)
            contentType = MediaType.APPLICATION_JSON
            content = createCardPaymentJson("ORDER-CONFLICT-002")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("idempotency_conflict") }
        }
    }

    @Test
    fun `get payment - not found - returns 404`() {
        mockMvc.get("/api/v1/payments/nonexistent") {
            header("X-API-Key", apiKey)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("payment_not_found") }
        }
    }

    @Test
    fun `missing API key - returns 401`() {
        mockMvc.post("/api/v1/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = createCardPaymentJson("ORDER-NOAUTH-001")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `list payments - returns paginated results`() {
        mockMvc.get("/api/v1/payments") {
            header("X-API-Key", apiKey)
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
        }
    }

    private fun createCardPaymentJson(merchantRef: String) = """
        {
          "amount": { "value": 5000, "currency": "INR" },
          "paymentMethod": {
            "type": "CARD",
            "card": {
              "number": "4111111111111111",
              "expiryMonth": "12",
              "expiryYear": "2027",
              "cvv": "123",
              "holderName": "Test User"
            }
          },
          "merchantReference": "$merchantRef"
        }
    """.trimIndent()

    private fun waitUntilCaptured(paymentId: String): MvcResult {
        val deadline = System.currentTimeMillis() + ASYNC_PROCESSING_TIMEOUT_MS
        var lastResult: MvcResult? = null

        while (System.currentTimeMillis() < deadline) {
            val result = mockMvc.get("/api/v1/payments/$paymentId/status") {
                header("X-API-Key", apiKey)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            lastResult = result
            val status = objectMapper.readTree(result.response.contentAsString).get("status").asText()
            if (status == "CAPTURED") {
                return result
            }
            Thread.sleep(ASYNC_PROCESSING_POLL_MS)
        }

        val lastBody = lastResult?.response?.contentAsString ?: "<no status response>"
        throw AssertionError("Payment $paymentId was not CAPTURED within timeout. Last response: $lastBody")
    }

    companion object {
        private const val ASYNC_PROCESSING_TIMEOUT_MS = 10_000L
        private const val ASYNC_PROCESSING_POLL_MS = 250L
    }
}
