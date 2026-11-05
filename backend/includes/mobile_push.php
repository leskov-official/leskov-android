<?php
declare(strict_types=1);

// Credentials are server-only; never place a service account key in the web root.
function app_mobile_push_credentials(): array {
    $path = app_env_value('GOOGLE_APPLICATION_CREDENTIALS', '/etc/leskov/firebase-service-account.json');
    $real = realpath($path);
    $web = realpath(__DIR__ . '/..');
    if (!$real || !$web || str_starts_with($real, $web . '/') || !is_readable($real)) {
        throw new RuntimeException('FCM service account unavailable');
    }
    $key = json_decode((string)file_get_contents($real), true, 32, JSON_THROW_ON_ERROR);
    if (($key['type'] ?? '') !== 'service_account' || ($key['project_id'] ?? '') !== 'leskov-b3863' || empty($key['private_key']) || empty($key['client_email'])) {
        throw new RuntimeException('FCM service account does not match leskov-b3863');
    }
    return $key;
}

// Authenticated device registration can report readiness without exposing credential details.
function app_mobile_push_delivery_ready(): bool {
    try { app_mobile_push_credentials(); return true; }
    catch (Throwable $e) { return false; }
}

/** Keep valid first-party absolute links intact; normalize relative site routes once. */
function app_mobile_push_target_url(string $url): string {
    $url = trim($url);
    if ($url === '') return 'https://lesv.online/notifications.php';
    $parts = parse_url($url);
    if ($parts === false) return 'https://lesv.online/notifications.php';
    if (isset($parts['scheme']) || isset($parts['host'])) {
        return ($parts['scheme'] ?? '') === 'https' && ($parts['host'] ?? '') === 'lesv.online' &&
            !isset($parts['user']) && !isset($parts['pass']) && (!isset($parts['port']) || $parts['port'] === 443)
            ? $url : 'https://lesv.online/notifications.php';
    }
    return 'https://lesv.online/' . ltrim($url, '/');
}

function app_mobile_push_schema(): void {
    db()->exec('CREATE TABLE IF NOT EXISTS mobile_push_devices (
        token_hash CHAR(64) CHARACTER SET ascii PRIMARY KEY,
        user_id BIGINT UNSIGNED NOT NULL,
        token VARCHAR(4096) NOT NULL,
        language VARCHAR(2) NOT NULL DEFAULT "ru",
        updated_at DATETIME NOT NULL,
        INDEX mobile_push_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
}

function app_mobile_push_http(string $url, string $body, array $headers): array {
    $ch = curl_init($url);
    curl_setopt_array($ch, [CURLOPT_POST => true, CURLOPT_POSTFIELDS => $body,
        CURLOPT_HTTPHEADER => $headers, CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 2, CURLOPT_TIMEOUT => 5, CURLOPT_FOLLOWLOCATION => false]);
    $response = curl_exec($ch);
    $status = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);
    if ($response === false) throw new RuntimeException('FCM network request failed');
    return [$status, json_decode((string)$response, true) ?? []];
}

function app_mobile_push_access_token(array $key): string {
    static $cached = null;
    if ($cached && $cached['expires'] > time() + 60) return $cached['token'];
    $b64 = static fn(string $value): string => rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    $now = time();
    $jwt = $b64(json_encode(['alg'=>'RS256','typ'=>'JWT'], JSON_THROW_ON_ERROR)) . '.' .
        $b64(json_encode(['iss'=>$key['client_email'], 'scope'=>'https://www.googleapis.com/auth/firebase.messaging',
            'aud'=>'https://oauth2.googleapis.com/token', 'iat'=>$now, 'exp'=>$now+3600], JSON_THROW_ON_ERROR));
    if (!openssl_sign($jwt, $signature, $key['private_key'], OPENSSL_ALGO_SHA256)) throw new RuntimeException('FCM signing failed');
    [$status, $reply] = app_mobile_push_http('https://oauth2.googleapis.com/token', http_build_query([
        'grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer', 'assertion'=>$jwt.'.'.$b64($signature)
    ]), ['Content-Type: application/x-www-form-urlencoded']);
    if ($status !== 200 || empty($reply['access_token'])) throw new RuntimeException('FCM OAuth failed (HTTP '.$status.')');
    $cached = ['token'=>(string)$reply['access_token'], 'expires'=>$now+(int)($reply['expires_in'] ?? 3600)];
    return $cached['token'];
}

function app_mobile_push_send(int $userId, array $data, int $ttl = 3600): void {
    // Push failures must never fail a message or a call. Logs contain no tokens or keys.
    try {
        $key = app_mobile_push_credentials();
        app_mobile_push_schema();
        $devices = db_all('SELECT token_hash, token FROM mobile_push_devices WHERE user_id = ? AND updated_at > DATE_SUB(NOW(), INTERVAL 60 DAY)', [$userId]);
        if (!$devices) return;
        $access = app_mobile_push_access_token($key);
        $data = array_map(static fn($value): string => (string)$value, $data);
        foreach ($devices as $device) {
            $android = ['priority'=>'HIGH', 'ttl'=>max(0,min(2419200,$ttl)).'s'];
            [$status,$reply] = app_mobile_push_http('https://fcm.googleapis.com/v1/projects/leskov-b3863/messages:send',
                json_encode(['message'=>['token'=>$device['token'],'data'=>$data,'android'=>$android]], JSON_THROW_ON_ERROR),
                ['Content-Type: application/json', 'Authorization: Bearer '.$access]);
            $unregistered = false;
            foreach (($reply['error']['details'] ?? []) as $detail) {
                if (($detail['errorCode'] ?? '') === 'UNREGISTERED') $unregistered = true;
            }
            if ($unregistered) db_exec('DELETE FROM mobile_push_devices WHERE token_hash = ?', [$device['token_hash']]);
            elseif ($status !== 200) error_log('LESKOV FCM send failed HTTP '.$status);
        }
    } catch (Throwable $e) { error_log('LESKOV FCM unavailable: '.$e->getMessage()); }
}

function app_mobile_push_call(int $calleeId, int $callId, string $engine, bool $cancel = false): void {
    if (!$cancel && !app_notification_pref_allowed($calleeId, 'message', 'push')) return;
    app_mobile_push_send($calleeId, [
        'type'=>$cancel ? 'call_cancelled' : 'incoming_call',
        'title'=>'LESKOV', 'body'=>$cancel ? '' : 'Входящий звонок',
        'target_url'=>'https://lesv.online/'.($engine === 'messages' ? 'messages.php' : 'profile.php?section=calls'),
        'call_id'=>$callId, 'collapse_id'=>'call_'.$engine.'_'.$callId,
        'expires_at'=>(string)(time()+45)
    ], 45);
}
