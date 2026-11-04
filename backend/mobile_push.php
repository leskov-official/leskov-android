<?php
declare(strict_types=1);
require __DIR__ . '/includes/app.php';
cms_send_security_headers();
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, private');
$user = app_current_user();
if (!$user || !empty($user['is_blocked']) || !empty($user['deactivated_at']) || !empty($user['deletion_requested_at'])) {
    http_response_code(401); echo json_encode(['ok'=>false,'error'=>'auth_required']); exit;
}
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
if ($method === 'GET' && ($_GET['action'] ?? '') === 'csrf') {
    echo json_encode(['ok'=>true,'csrf'=>cms_csrf_token()]); exit;
}
if ($method !== 'POST') { http_response_code(405); header('Allow: GET, POST'); echo json_encode(['ok'=>false]); exit; }
cms_verify_csrf();
try {
    cms_rate_limit('mobile_push_'.(int)$user['id'], 60, 60);
    $action = $_POST['action'] ?? '';
    $token = $_POST['token'] ?? '';
    if (!in_array($action, ['register','unregister'], true) || !is_string($token) || strlen($token)<20 || strlen($token)>4096 || !preg_match('/^[A-Za-z0-9_:\-]+$/D', $token)) {
        http_response_code(400); echo json_encode(['ok'=>false,'error'=>'invalid_request']); exit;
    }
    app_mobile_push_schema();
    $hash = hash('sha256', $token);
    if ($action === 'unregister') {
        db_exec('DELETE FROM mobile_push_devices WHERE token_hash = ? AND user_id = ?', [$hash,(int)$user['id']]);
    } else {
        $lang = is_string($_POST['language'] ?? null) && in_array($_POST['language'], ['ru','et','en'], true) ? $_POST['language'] : 'ru';
        db_exec('INSERT INTO mobile_push_devices (token_hash,user_id,token,language,updated_at) VALUES (?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),token=VALUES(token),language=VALUES(language),updated_at=NOW()', [$hash,(int)$user['id'],$token,$lang]);
    }
    echo json_encode(['ok'=>true]);
} catch (Throwable $e) {
    error_log('LESKOV mobile push registration failed');
    http_response_code(503); echo json_encode(['ok'=>false,'error'=>'temporarily_unavailable']);
}
