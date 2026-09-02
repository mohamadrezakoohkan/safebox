package com.calcplus.calculator.crypto

import com.calcplus.calculator.core.crypto.Pbkdf2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pbkdf2Test {
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun knownVectors() {
        // PBKDF2-HMAC-SHA256(P="password", S="salt", c=1, dkLen=32)
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(Pbkdf2.derive("password", "salt".toByteArray(), 1)),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            hex(Pbkdf2.derive("password", "salt".toByteArray(), 2)),
        )
    }

    @Test
    fun derivationIsDeterministicPerSalt() {
        val salt = Pbkdf2.randomSalt()
        val a = Pbkdf2.derive("D1|D2|ADD|D3|D4", salt, 1_000)
        val b = Pbkdf2.derive("D1|D2|ADD|D3|D4", salt, 1_000)
        assertTrue(Pbkdf2.constantTimeEquals(a, b))
    }

    @Test
    fun differentSaltsProduceDifferentHashes() {
        val a = Pbkdf2.derive("D1|D2|D3|D4", Pbkdf2.randomSalt(), 1_000)
        val b = Pbkdf2.derive("D1|D2|D3|D4", Pbkdf2.randomSalt(), 1_000)
        assertNotEquals(hex(a), hex(b))
    }

    @Test
    fun tamperedSaltFails() {
        val salt = Pbkdf2.randomSalt()
        val hash = Pbkdf2.derive("D1|D2|D3|D4", salt, 1_000)
        val tampered = salt.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(Pbkdf2.constantTimeEquals(hash, Pbkdf2.derive("D1|D2|D3|D4", tampered, 1_000)))
    }

    @Test
    fun constantTimeEqualsBasics() {
        assertTrue(Pbkdf2.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(Pbkdf2.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(Pbkdf2.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
    }

    @Test
    fun saltHasRequestedLength() {
        assertEquals(16, Pbkdf2.randomSalt().size)
    }
}
