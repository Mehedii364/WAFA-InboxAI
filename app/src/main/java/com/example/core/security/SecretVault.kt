package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface SecretVault {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
    fun isHardwareBacked(): Boolean
}

object KeyMasker {
    fun mask(rawKey: String): String {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length <= 8) return "••••••••"
        val prefix = trimmed.take(4)
        val suffix = trimmed.takeLast(4)
        return "$prefix••••••$suffix"
    }

    fun redactSecrets(input: String): String {
        return input
            .replace(Regex("(Bearer\\s+)[A-Za-z0-9._\\-]{8,}", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            .replace(Regex("(x-goog-api-key[:=]\\s*)[A-Za-z0-9._\\-]{8,}", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            .replace(Regex("(AIza[0-9A-Za-z\\-_]{20,})"), "[REDACTED_GEMINI_KEY]")
            .replace(Regex("(gsk_[0-9A-Za-z]{16,})"), "[REDACTED_GROQ_KEY]")
            .replace(Regex("(sk-or-v1-[0-9A-Za-z]{16,})"), "[REDACTED_OPENROUTER_KEY]")
            .replace(Regex("(hf_[0-9A-Za-z]{16,})"), "[REDACTED_HF_KEY]")
    }
}

/**
 * Hardware-backed Android KeyStore AES-256-GCM vault for encrypting BYOK AI API keys
 * and OAuth tokens at rest on device.
 */
class AndroidKeyStoreVault(
    private val keyAlias: String = DEFAULT_ALIAS
) : SecretVault {

    private val hardwareAvailable: Boolean by lazy {
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            true
        }.getOrDefault(false)
    }

    private val fallbackVault: SoftwareAesGcmVault by lazy {
        SoftwareAesGcmVault(defaultFallbackKey())
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        if (!hardwareAvailable) return fallbackVault.encrypt(plaintext)
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "$PREFIX_KS$ivB64:$dataB64"
        }.getOrElse {
            fallbackVault.encrypt(plaintext)
        }
    }

    override fun decrypt(ciphertext: String): String {
        if (ciphertext.startsWith(PREFIX_SW)) return fallbackVault.decrypt(ciphertext)
        require(ciphertext.startsWith(PREFIX_KS)) { "Unsupported encrypted payload format" }
        val body = ciphertext.removePrefix(PREFIX_KS)
        val parts = body.split(":")
        require(parts.size == 2) { "Corrupted encrypted credential payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    override fun isHardwareBacked(): Boolean = hardwareAvailable

    private fun defaultFallbackKey(): ByteArray {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "wafa_inboxai_vault_aes256_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        const val PREFIX_KS = "ks_v1:"
        const val PREFIX_SW = "sw_v1:"
    }
}

/**
 * Pure JVM AES-256-GCM vault implementation used in unit tests and fallback environments.
 */
class SoftwareAesGcmVault(keyBytes: ByteArray) : SecretVault {
    init {
        require(keyBytes.size == 32) { "AES-256 key must be 32 bytes" }
    }

    private val secretKey = SecretKeySpec(keyBytes.copyOf(), "AES")
    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = java.util.Base64.getEncoder().encodeToString(iv)
        val dataB64 = java.util.Base64.getEncoder().encodeToString(encrypted)
        return "${AndroidKeyStoreVault.PREFIX_SW}$ivB64:$dataB64"
    }

    override fun decrypt(ciphertext: String): String {
        require(ciphertext.startsWith(AndroidKeyStoreVault.PREFIX_SW)) { "Invalid ciphertext prefix" }
        val body = ciphertext.removePrefix(AndroidKeyStoreVault.PREFIX_SW)
        val parts = body.split(":")
        require(parts.size == 2) { "Malformed ciphertext payload" }
        val iv = java.util.Base64.getDecoder().decode(parts[0])
        val encrypted = java.util.Base64.getDecoder().decode(parts[1])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    override fun isHardwareBacked(): Boolean = false
}
