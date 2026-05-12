package com.yuno.payment.provider

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProviderConnectorRegistryTest {
    @Test
    fun `returns connector by name`() {
        val connector = mock<ProviderConnector>()
        whenever(connector.name).thenReturn("PROVIDER_A")

        val registry = ProviderConnectorRegistry(listOf(connector))

        assertThat(registry.get("PROVIDER_A")).isSameAs(connector)
    }

    @Test
    fun `throws for missing connector`() {
        val registry = ProviderConnectorRegistry(emptyList())

        assertThrows(IllegalStateException::class.java) {
            registry.get("MISSING")
        }
    }
}
