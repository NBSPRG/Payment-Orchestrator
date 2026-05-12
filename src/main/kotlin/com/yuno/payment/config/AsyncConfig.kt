package com.yuno.payment.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Configuration
@EnableScheduling
class AsyncConfig {
    @Bean
    fun providerExecutor(): Executor =
        ThreadPoolExecutor(
            8,
            32,
            60,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(500),
            Thread.ofVirtual().name("provider-call-", 0).factory(),
            ThreadPoolExecutor.CallerRunsPolicy(),
        )
}
