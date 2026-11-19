package com.example

import com.example.core.security.KeyMasker
import com.example.core.security.NetworkSecurity
import com.example.core.security.PasswordHasher
import com.example.core.security.SoftwareAesGcmVault
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class CryptoAndAuthTest {

    @Test
    fun `password hashing verifies valid password and rejects wrong password`() {
        val hash = PasswordHasher.hash("SuperSecurePass123!")
        assertTrue(hash.startsWith("pbkdf2_sha256$"))
        assertTrue(PasswordHasher.verify("SuperSecurePass123!", hash))
        assertFalse(PasswordHasher.verify("WrongPassword123!", hash))
    }

    @Test
    fun `aes 256 gcm vault encrypts with random iv and detects tampering`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val vault = SoftwareAesGcmVault(key)

        val secret = "sk-or-v1-example-secret-key-987654321"
        val enc1 = vault.encrypt(secret)
        val enc2 = vault.encrypt(secret)

        assertNotEquals("Randomized IV must produce unique ciphertexts", enc1, enc2)
        assertEquals(secret, vault.decrypt(enc1))
        assertEquals(secret, vault.decrypt(enc2))

        val tampered = enc1.dropLast(2) + if (enc1.endsWith("AA")) "BB" else "AA"
        assertThrows(Exception::class.java) {
            vault.decrypt(tampered)
        }
    }

    @Test
    fun `key masker masks credentials and redacts secrets from logs`() {
        assertEquals("AIza••••••9xQ2", KeyMasker.mask("AIzaSyD12345678909xQ2"))
        assertEquals("••••••••", KeyMasker.mask("short"))

        val redacted = KeyMasker.redactSecrets("Authorization: Bearer gsk_abcdef123456789012345")
        assertFalse(redacted.contains("gsk_abcdef"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `https validator enforces tls and rejects cleartext http`() {
        assertTrue(NetworkSecurity.validateHttpsUrl("https://api.xpiki.com/v1/").isSuccess)
        assertTrue(NetworkSecurity.validateHttpsUrl("http://insecure.example.com").isFailure)
        assertTrue(NetworkSecurity.validateHttpsUrl("").isFailure)
    }
}
