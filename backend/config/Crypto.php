<?php
declare(strict_types=1);

namespace WafaBackend\Config;

use RuntimeException;

/**
 * Server-side AES-256-GCM credential encryption using APP_ENCRYPTION_KEY stored strictly
 * outside the database in server environment configuration.
 */
final class Crypto
{
    private static function key(): string
    {
        $b64 = getenv('APP_ENCRYPTION_KEY') ?: '';
        $raw = base64_decode($b64, true);
        if ($raw === false || strlen($raw) !== 32) {
            throw new RuntimeException('APP_ENCRYPTION_KEY must be a valid base64-encoded 32-byte key.');
        }
        return $raw;
    }

    /** @return array{ciphertext:string, iv_b64:string, auth_tag_b64:string, masked:string} */
    public static function encryptSecret(string $plaintext): array
    {
        $iv = random_bytes(12);
        $tag = '';
        $encrypted = openssl_encrypt(
            $plaintext,
            'aes-256-gcm',
            self::key(),
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            '',
            16
        );
        if ($encrypted === false) {
            throw new RuntimeException('Credential encryption failed.');
        }
        return [
            'ciphertext' => base64_encode($encrypted),
            'iv_b64' => base64_encode($iv),
            'auth_tag_b64' => base64_encode($tag),
            'masked' => self::maskKey($plaintext),
        ];
    }

    public static function decryptSecret(string $ciphertextB64, string $ivB64, string $tagB64): string
    {
        $ciphertext = base64_decode($ciphertextB64, true);
        $iv = base64_decode($ivB64, true);
        $tag = base64_decode($tagB64, true);
        if ($ciphertext === false || $iv === false || $tag === false) {
            throw new RuntimeException('Corrupted encrypted payload.');
        }
        $plain = openssl_decrypt(
            $ciphertext,
            'aes-256-gcm',
            self::key(),
            OPENSSL_RAW_DATA,
            $iv,
            $tag
        );
        if ($plain === false) {
            throw new RuntimeException('Credential authentication/decryption failed.');
        }
        return $plain;
    }

    public static function maskKey(string $raw): string
    {
        $trimmed = trim($raw);
        if (strlen($trimmed) <= 8) {
            return '••••••••';
        }
        return substr($trimmed, 0, 4) . '••••••' . substr($trimmed, -4);
    }
}
