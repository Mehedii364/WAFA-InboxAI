<?php
declare(strict_types=1);

namespace WafaBackend\Config;

use PDO;

final class Security
{
    public static function enforceHttps(): void
    {
        $env = getenv('APP_ENV') ?: 'production';
        $isHttps = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
            || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
        if ($env === 'production' && !$isHttps) {
            self::jsonResponse(['error' => 'HTTPS is required.'], 426);
        }
    }

    public static function sendSecurityHeaders(): void
    {
        header('Content-Type: application/json; charset=utf-8');
        header('X-Content-Type-Options: nosniff');
        header('X-Frame-Options: DENY');
        header('Referrer-Policy: no-referrer');
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
        $allowedOrigin = getenv('ALLOWED_ORIGIN') ?: '';
        if ($allowedOrigin !== '' && ($_SERVER['HTTP_ORIGIN'] ?? '') === $allowedOrigin) {
            header('Access-Control-Allow-Origin: ' . $allowedOrigin);
            header('Access-Control-Allow-Headers: Authorization, Content-Type, X-CSRF-Token');
            header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
        }
    }

    public static function hashPassword(string $password): string
    {
        return password_hash($password, PASSWORD_ARGON2ID);
    }

    public static function verifyPassword(string $password, string $hash): bool
    {
        return password_verify($password, $hash);
    }

    public static function authenticateBearer(PDO $pdo): ?array
    {
        $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
        if (!preg_match('/^Bearer\s+([A-Za-z0-9._\-]+)$/i', $auth, $m)) {
            return null;
        }
        $tokenHash = hash('sha256', $m[1]);
        $stmt = $pdo->prepare(
            'SELECT u.id, u.full_name, u.email FROM user_sessions s
             INNER JOIN users u ON u.id = s.user_id
             WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > NOW() AND u.is_active = 1
             LIMIT 1'
        );
        $stmt->execute([$tokenHash]);
        $row = $stmt->fetch();
        return $row ?: null;
    }

    public static function uuidV4(): string
    {
        $data = random_bytes(16);
        $data[6] = chr((ord($data[6]) & 0x0f) | 0x40);
        $data[8] = chr((ord($data[8]) & 0x3f) | 0x80);
        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
    }

    public static function jsonResponse(array $payload, int $status = 200): never
    {
        http_response_code($status);
        echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }
}
