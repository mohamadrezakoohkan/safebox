package com.calcplus.calculator.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the passcode blob with a hardware-backed Android Keystore AES/GCM key.
 * Non-exportable, NOT auth-bound (the vault's credential is the calculator
 * passcode, not the device credential). Any failure returns null so
 * PasscodeStore can fall back to storing the blob unwrapped (§3.4) — no
 * user-visible error, no logging of the failure detail (no-logging rule).
 */
interface BlobWrapper {
    fun wrap(plain: ByteArray): ByteArray?
    fun unwrap(wrapped: ByteArray): ByteArray?
}

class KeystoreWrapper(
    private val keyAlias: String = "com.calcplus.calculator.passcode",
) : BlobWrapper {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_BYTES = 12
    }

    private fun obtainKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        existing ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
    } catch (_: Exception) {
        null
    }

    override fun wrap(plain: ByteArray): ByteArray? = try {
        val key = obtainKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        iv + ciphertext
    } catch (_: Exception) {
        null
    }

    override fun unwrap(wrapped: ByteArray): ByteArray? = try {
        if (wrapped.size <= IV_BYTES) return null
        val key = obtainKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = wrapped.copyOfRange(0, IV_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(wrapped.copyOfRange(IV_BYTES, wrapped.size))
    } catch (_: Exception) {
        null
    }
}
