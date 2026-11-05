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
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    http_response_code(405); header('Allow: POST'); echo json_encode(['ok'=>false,'error'=>'method_not_allowed']); exit;
}
cms_verify_csrf();
$action = (string)($_POST['action'] ?? '');
$source = (string)($_POST['source_type'] ?? '');
$id = (int)($_POST['source_id'] ?? 0);
if (!in_array($action, ['toggle_track_like','save_track_to_library'], true) ||
    !in_array($source, ['catalog','user'], true) || $id <= 0) {
    http_response_code(400); echo json_encode(['ok'=>false,'error'=>'invalid_request']); exit;
}
try {
    cms_rate_limit('mobile_track_action_'.(int)$user['id'], 90, 60);
    app_require_premium_track_action((int)$user['id']);
    $track = app_track_source_row($source, $id, (int)$user['id']);
    if (!$track) { http_response_code(404); echo json_encode(['ok'=>false,'error'=>'track_not_found']); exit; }
    if ($action === 'toggle_track_like') app_toggle_track_like((int)$user['id'], $source, $id);
    else app_save_track_to_library((int)$user['id'], $source, $id);
    $saved = (bool)db_value('SELECT id FROM user_saved_tracks WHERE user_id=? AND source_type=? AND source_id=? LIMIT 1', [(int)$user['id'],$source,$id]);
    echo json_encode(['ok'=>true,'source_type'=>$source,'source_id'=>$id,
        'liked'=>app_track_user_liked((int)$user['id'],$source,$id),
        'likes'=>app_track_likes_count($source,$id),'saved'=>$saved,
        'track'=>['src'=>app_media_url((string)$track['audio_path']), 'title'=>(string)$track['title'],
            'artist'=>(string)($track['owner_name'] ?? 'LESKOV'),
            'cover'=>empty($track['cover_path']) ? '' : app_media_url((string)$track['cover_path']),
            'source_type'=>$source,'source_id'=>$id]], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
} catch (Throwable $e) {
    http_response_code(400); echo json_encode(['ok'=>false,'error'=>'action_failed']);
}
