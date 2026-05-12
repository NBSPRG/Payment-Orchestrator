package com.yuno.payment.provider

import org.springframework.stereotype.Component

@Component
class ProviderConnectorRegistry(connectors: List<ProviderConnector>) {
    private val byName = connectors.associateBy { it.name }

    fun get(name: String): ProviderConnector =
        byName[name] ?: error("Provider connector $name is not registered")
}
