<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/Database.php';
require_once __DIR__ . '/../config/Crypto.php';
require_once __DIR__ . '/../config/Security.php';
require_once __DIR__ . '/../jobs/Runner.php';

use WafaBackend\Config\Crypto;
use WafaBackend\Config\Database;
use WafaBackend\Config\Security;
use WafaBackend\Jobs\Runner;

Security::sendSecurityHeaders();
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}
Security::enforceHttps();

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$body = json_decode(file_get_contents('php://input') ?: '{}', true) ?: [];

if ($path === '/api/health' && $method === 'GET') {
    Security::jsonResponse([
        'status' => 'ok',
        'service' => 'WAFA InboxAI PHP Backend',
        'version' => '1.0.0',
        'message' => 'WAFA InboxAI HTTPS API operational.',
        'developer' => 'Mehedi364 (Md. Mehedi Hasan)',
    ]);
}

$pdo = Database::connection();

// Authenticated HTTP Scheduled Job Runner (strictly requires X-Cron-Token secret)
if ($path === '/api/jobs/run' && $method === 'POST') {
    $expected = getenv('CRON_RUNNER_SECRET') ?: '';
    $provided = $_SERVER['HTTP_X_CRON_TOKEN'] ?? '';
    if ($expected === '' || !hash_equals($expected, $provided)) {
        Security::jsonResponse(['error' => 'Unauthorized job runner token.'], 401);
    }
    $results = Runner::runBatch($pdo, 10);
    Security::jsonResponse(['processed' => $results, 'count' => count($results)]);
}

if ($path === '/api/auth/register' && $method === 'POST') {
    $name = trim((string)($body['full_name'] ?? ''));
    $email = strtolower(trim((string)($body['email'] ?? '')));
    $password = (string)($body['password'] ?? '');
    if (strlen($name) < 2 || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($password) < 8) {
        Security::jsonResponse(['error' => 'Valid name, email, and 8+ character password are required.'], 422);
    }
    $id = Security::uuidV4();
    $stmt = $pdo->prepare('INSERT INTO users (id, full_name, email, password_hash) VALUES (?, ?, ?, ?)');
    $stmt->execute([$id, $name, $email, Security::hashPassword($password)]);
    Security::jsonResponse(['id' => $id, 'full_name' => $name, 'email' => $email], 201);
}

if ($path === '/api/auth/login' && $method === 'POST') {
    $email = strtolower(trim((string)($body['email'] ?? '')));
    $password = (string)($body['password'] ?? '');
    $stmt = $pdo->prepare('SELECT id, full_name, email, password_hash FROM users WHERE email = ? AND is_active = 1 LIMIT 1');
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    if (!$user || !Security::verifyPassword($password, $user['password_hash'])) {
        Security::jsonResponse(['error' => 'Invalid email or password.'], 401);
    }
    $rawToken = bin2hex(random_bytes(32));
    $sessionId = Security::uuidV4();
    $insert = $pdo->prepare(
        'INSERT INTO user_sessions (id, user_id, token_hash, user_agent, ip_address, expires_at)
         VALUES (?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 30 DAY))'
    );
    $insert->execute([
        $sessionId,
        $user['id'],
        hash('sha256', $rawToken),
        substr((string)($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 250),
        (string)($_SERVER['REMOTE_ADDR'] ?? ''),
    ]);
    Security::jsonResponse([
        'token' => $rawToken,
        'user' => ['id' => $user['id'], 'full_name' => $user['full_name'], 'email' => $user['email']],
    ]);
}

// Protected user endpoints
$user = Security::authenticateBearer($pdo);
if ($user === null) {
    Security::jsonResponse(['error' => 'Authentication required.'], 401);
}

if ($path === '/api/providers/credentials' && $method === 'POST') {
    $providerType = strtoupper(trim((string)($body['provider_type'] ?? '')));
    $apiKey = trim((string)($body['api_key'] ?? ''));
    $baseUrl = trim((string)($body['base_url'] ?? ''));
    $allowed = ['GEMINI', 'GROQ', 'OPENROUTER', 'HUGGINGFACE', 'CLOUDFLARE', 'XPIKI'];
    if (!in_array($providerType, $allowed, true) || $apiKey === '' || !str_starts_with($baseUrl, 'https://')) {
        Security::jsonResponse(['error' => 'Invalid provider_type, api_key, or HTTPS base_url.'], 422);
    }
    $providerId = Security::uuidV4();
    $upsertProvider = $pdo->prepare(
        'INSERT INTO ai_providers (id, user_id, provider_type, base_url, enabled)
         VALUES (?, ?, ?, ?, 1)
         ON DUPLICATE KEY UPDATE base_url = VALUES(base_url), enabled = 1'
    );
    $upsertProvider->execute([$providerId, $user['id'], $providerType, $baseUrl]);

    $fetchId = $pdo->prepare('SELECT id FROM ai_providers WHERE user_id = ? AND provider_type = ? LIMIT 1');
    $fetchId->execute([$user['id'], $providerType]);
    $actualProviderId = $fetchId->fetchColumn();

    $enc = Crypto::encryptSecret($apiKey);
    $credId = Security::uuidV4();
    $upsertCred = $pdo->prepare(
        'INSERT INTO encrypted_api_credentials (id, user_id, ai_provider_id, ciphertext, iv_b64, auth_tag_b64, masked_preview)
         VALUES (?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE ciphertext = VALUES(ciphertext), iv_b64 = VALUES(iv_b64),
           auth_tag_b64 = VALUES(auth_tag_b64), masked_preview = VALUES(masked_preview)'
    );
    $upsertCred->execute([
        $credId, $user['id'], $actualProviderId,
        $enc['ciphertext'], $enc['iv_b64'], $enc['auth_tag_b64'], $enc['masked']
    ]);
    Security::jsonResponse(['provider_type' => $providerType, 'masked_preview' => $enc['masked']]);
}

Security::jsonResponse(['error' => 'Endpoint not found.'], 404);
