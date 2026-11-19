package com.example.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2WithHmacSHA256 salted password hashing for local WAFA InboxAI accounts.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(password: String): String {
        require(password.length >= 8) { "Password must be at least 8 characters." }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val hashB64 = Base64.getEncoder().encodeToString(derived)
        return "pbkdf2_sha256\$$ITERATIONS\$$saltB64\$$hashB64"
    }

    fun verify(password: String, storedHash: String): Boolean {
        val parts = storedHash.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2_sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = deriveKey(password.toCharArray(), salt, iterations, expected.size * 8)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun deriveKey(
        chars: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int
    ): ByteArray {
        val spec = PBEKeySpec(chars, salt, iterations, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
