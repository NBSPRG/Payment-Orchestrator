package com.yuno.payment.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class ApiKeyIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `merchant can rotate api key`() {
        mockMvc.post("/api/v1/merchant/api-keys/rotate") {
            header("X-API-Key", "test_api_key_123")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.apiKey") { exists() }
            jsonPath("$.keyPrefix") { exists() }
        }
    }
}
