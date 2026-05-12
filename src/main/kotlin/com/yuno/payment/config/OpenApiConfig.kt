package com.yuno.payment.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Payment Orchestration API")
                .version("1.0.0")
                .description("Production-grade payment orchestration system with multi-provider routing, SAGA orchestration, and real-time status updates.")
                .contact(Contact().name("Payment Team").email("payments@yuno.com")),
        )
        .addSecurityItem(SecurityRequirement().addList("ApiKeyAuth"))
        .components(
            Components()
                .addSecuritySchemes(
                    "ApiKeyAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .name("X-API-Key"),
                ),
        )
}
