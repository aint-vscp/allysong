package com.allysong.domain.crypto

// ============================================================================
// PayloadEncryptor.kt – commonMain
// ============================================================================
// Interface for encrypting/decrypting mesh network payloads.
// SOS broadcasts must be encrypted to prevent spoofing by malicious actors.
// ============================================================================

/**
 * Symmetric encryption abstraction for mesh payloads.
 *
 * The prototype uses AES-256-GCM with a pre-shared key (PSK) model.
 * In production, this would be replaced by a key-exchange protocol
 * (e.g., X25519 Diffie-Hellman per peer connection).
 */
interface PayloadEncryptor {

    /**
     * Encrypts a plaintext payload for mesh transmission.
     *
     * @param plaintext Raw payload bytes.
     * @return Encrypted bytes (IV prepended to ciphertext + auth tag).
     */
    fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Decrypts a mesh payload received from a peer.
     *
     * @param ciphertext Encrypted bytes (IV + ciphertext + auth tag).
     * @return Decrypted plaintext bytes, or null if authentication fails
     *         (tampered or corrupted payload).
     */
    fun decrypt(ciphertext: ByteArray): ByteArray?
}
