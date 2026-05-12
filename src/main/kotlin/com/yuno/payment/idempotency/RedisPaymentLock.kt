package com.yuno.payment.idempotency

import com.yuno.payment.domain.exception.ConcurrentPaymentProcessingException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Redis distributed lock to prevent concurrent processing of the same payment.
 * Uses SETNX with TTL and Lua-based safe release.
 */
@Component
class RedisPaymentLock(
    private val redisTemplate: StringRedisTemplate,
) {
    private val releaseScript = DefaultRedisScript(
        """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """.trimIndent(),
        Long::class.java,
    )

    fun <T> withPaymentLock(paymentId: String, block: () -> T): T {
        val lockKey = "lock:payment:$paymentId"
        val token = UUID.randomUUID().toString()

        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, token, 30, TimeUnit.SECONDS)

        if (acquired != true) {
            throw ConcurrentPaymentProcessingException(paymentId)
        }

        return try {
            block()
        } finally {
            redisTemplate.execute(releaseScript, listOf(lockKey), token)
        }
    }
}
