package com.calcplus.calculator.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Pbkdf2 {
    /** OWASP figure for PBKDF2-HMAC-SHA256 (not the SHA-512 210k figure). */
    const val DEFAULT_ITERATIONS = 600_000
    const val SALT_BYTES = 16
    const val KEY_BITS = 256

    /**
     * PBKDF2-HMAC-SHA256 over the "|"-joined canonical key-ID serialization.
     * ASCII input means Java's char[]→UTF-8 handling matches iOS's UTF-8 bytes.
     */
    fun derive(password: String, salt: ByteArray, iterations: Int, keyBits: Int = KEY_BITS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyBits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        try {
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison — naive byte-array equality is not constant-time. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        MessageDigest.isEqual(a, b)

    fun randomSalt(byteCount: Int = SALT_BYTES): ByteArray =
        ByteArray(byteCount).also { SecureRandom().nextBytes(it) }
}
