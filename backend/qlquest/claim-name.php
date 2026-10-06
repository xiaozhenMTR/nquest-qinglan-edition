<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }

$file = __DIR__ . '/uuidname.json';

function loadNames() {
    global $file;
    if (!file_exists($file)) file_put_contents($file, '{}', LOCK_EX);
    $d = json_decode(file_get_contents($file), true);
    return is_array($d) ? $d : [];
}
function saveNames($d) {
    global $file;
    file_put_contents($file, json_encode($d, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE), LOCK_EX);
}

// GET：返回当前映射
if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    echo json_encode(['success' => true, 'names' => loadNames()], JSON_UNESCAPED_UNICODE);
    exit;
}

// POST：认领
$input = json_decode(file_get_contents('php://input'), true);
$uuid = $input['uuid'] ?? '';
$name = $input['name'] ?? '';

// 基础格式校验
$cleanUuid = strtolower(str_replace('-', '', $uuid));
if (!preg_match('/^[0-9a-f]{32}$/', $cleanUuid)) {
    echo json_encode(['success' => false, 'error' => 'UUID 格式不正确'], JSON_UNESCAPED_UNICODE);
    exit;
}
$name = trim($name);
if (!preg_match('/^[A-Za-z0-9_]{1,16}$/', $name)) {
    echo json_encode(['success' => false, 'error' => '玩家名不合法（1-16 位，只能含字母数字下划线）'], JSON_UNESCAPED_UNICODE);
    exit;
}

$names = loadNames();
$names[$cleanUuid] = $name;
saveNames($names);
echo json_encode(['success' => true, 'message' => '认领成功: ' . $name], JSON_UNESCAPED_UNICODE);
