package com.yuno.payment.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration

/**
 * Per-provider Resilience4j configuration per plan §10.
 * Configures circuit breakers, retry, and time limiters for provider calls.
 */
@Configuration
class Resilience4jConfig {

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val defaultConfig = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build()

        return CircuitBreakerRegistry.of(defaultConfig)
    }

    @Bean
    fun retryRegistry(): RetryRegistry {
        val defaultConfig: RetryConfig = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(500), 2.0))
            .retryExceptions(SocketTimeoutException::class.java, IOException::class.java)
            .build()

        return RetryRegistry.of(defaultConfig)
    }

    @Bean
    fun timeLimiterRegistry(): TimeLimiterRegistry {
        val defaultConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(5))
            .build()

        return TimeLimiterRegistry.of(defaultConfig)
    }
}
