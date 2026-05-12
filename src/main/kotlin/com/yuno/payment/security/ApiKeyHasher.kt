package com.yuno.payment.security

import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class ApiKeyHasher {
    fun hash(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
