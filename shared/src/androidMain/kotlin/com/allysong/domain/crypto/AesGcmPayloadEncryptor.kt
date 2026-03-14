package com.allysong.domain.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

// ============================================================================
// AesGcmPayloadEncryptor.kt – androidMain
// ============================================================================
// AES-256-GCM encryption for mesh network payloads.
//
// Security model (prototype):
//   - Pre-shared key (PSK) hardcoded for development.
//   - In production, replace with per-peer key exchange (X25519 ECDH).
//
// Wire format: [12-byte IV] + [ciphertext + 16-byte GCM auth tag]
// ============================================================================

/** GCM initialization vector size in bytes. */
private const val IV_SIZE = 12

/** GCM authentication tag size in bits. */
private const val TAG_SIZE_BITS = 128

/**
 * AES-256-GCM payload encryptor for mesh messages.
 *
 * @param keyBytes 32-byte (256-bit) secret key. In the prototype, this is
 *                 a static PSK. In production, derive per-session keys.
 */
class AesGcmPayloadEncryptor(
    keyBytes: ByteArray = DEFAULT_PSK
) : PayloadEncryptor {

    private val secretKey = SecretKeySpec(keyBytes, "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        // Generate a fresh random IV for each encryption
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))

        val ciphertext = cipher.doFinal(plaintext)

        // Prepend IV to ciphertext for self-describing wire format
        return iv + ciphertext
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray? {
        if (ciphertext.size < IV_SIZE + TAG_SIZE_BITS / 8) return null

        return try {
            val iv = ciphertext.copyOfRange(0, IV_SIZE)
            val encrypted = ciphertext.copyOfRange(IV_SIZE, ciphertext.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))

            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            // Authentication failure → tampered or corrupted payload
            null
        }
    }

    companion object {
        /**
         * Development-only pre-shared key. MUST be replaced in production
         * with a proper key derivation/exchange mechanism.
         */
        val DEFAULT_PSK = ByteArray(32) { (it * 7 + 0x42).toByte() }
    }
}
