<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }

// ═══════════ 配置 ═══════════
define('CUSTOM_TITLE_PRICE', 5000);   // 自定义称号价格
$file = __DIR__ . '/cdk.json';

// ═══════════ 文件操作 ═══════════
function loadCdks() {
    global $file;
    if (!file_exists($file)) file_put_contents($file, '{}', LOCK_EX);
    $h = fopen($file, 'r');
    flock($h, LOCK_SH);
    $d = json_decode(fread($h, filesize($file)), true);
    flock($h, LOCK_UN);
    fclose($h);
    return is_array($d) ? $d : [];
}

function saveCdks($d) {
    global $file;
    $h = fopen($file, 'w');
    flock($h, LOCK_EX);
    fwrite($h, json_encode($d, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
    flock($h, LOCK_UN);
    fclose($h);
}

function generateCdkCode() {
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    $code = '';
    for ($i = 0; $i < 8; $i++) $code .= $chars[random_int(0, strlen($chars) - 1)];
    return $code;
}

// ═══════════ 处理请求 ═══════════
$input = null;
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = json_decode(file_get_contents('php://input'), true);
}
$action = $input['action'] ?? $_GET['action'] ?? '';

// ── 生成 CDK ──
if ($action === 'generate') {
    $uuid = $input['uuid'] ?? '';
    $titleName = $input['titleName'] ?? '';

    $cleanUuid = strtolower(str_replace('-', '', $uuid));
    if (!preg_match('/^[0-9a-f]{32}$/', $cleanUuid)) {
        echo json_encode(['success' => false, 'error' => 'UUID 格式不正确']); exit;
    }
    $titleName = trim($titleName);
    if (empty($titleName) || mb_strlen($titleName) > 32) {
        echo json_encode(['success' => false, 'error' => '称号名称不合法（1-32 字符）']); exit;
    }

    $cdks = loadCdks();
    $code = generateCdkCode();
    while (isset($cdks[$code])) $code = generateCdkCode(); // 防碰撞

    $cdks[$code] = [
        'uuid'      => $cleanUuid,
        'titleName' => $titleName,
        'price'     => CUSTOM_TITLE_PRICE,
        'claimed'   => false,
        'createdAt' => time(),
    ];
    saveCdks($cdks);

    echo json_encode(['success' => true, 'cdk' => $code, 'price' => CUSTOM_TITLE_PRICE], JSON_UNESCAPED_UNICODE);
    exit;
}

// ── 检查 CDK（模组调用） ──
if ($action === 'check') {
    $code = strtoupper(trim($_GET['cdk'] ?? ''));
    $cdks = loadCdks();

    if (!isset($cdks[$code])) {
        echo json_encode(['success' => false, 'error' => '兑换码不存在']); exit;
    }

    $cdk = $cdks[$code];
    echo json_encode([
        'success'   => true,
        'uuid'      => $cdk['uuid'],
        'titleName' => $cdk['titleName'],
        'price'     => $cdk['price'],
        'claimed'   => $cdk['claimed'],
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

// ── 兑换 CDK（模组调用） ──
if ($action === 'claim') {
    $code = strtoupper(trim($input['cdk'] ?? ''));
    $playerUuid = strtolower(str_replace('-', '', $input['uuid'] ?? ''));

    $cdks = loadCdks();

    if (!isset($cdks[$code])) {
        echo json_encode(['success' => false, 'error' => '兑换码不存在']); exit;
    }

    $cdk = &$cdks[$code];

    if ($cdk['claimed']) {
        echo json_encode(['success' => false, 'error' => '兑换码已被使用']); exit;
    }

    if ($cdk['uuid'] !== $playerUuid) {
        echo json_encode(['success' => false, 'error' => '此兑换码不属于你的 UUID']); exit;
    }

    $cdk['claimed'] = true;
    $cdk['claimedAt'] = time();
    saveCdks($cdks);

    echo json_encode([
        'success'   => true,
        'titleName' => $cdk['titleName'],
        'price'     => $cdk['price'],
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

echo json_encode(['success' => false, 'error' => '未知操作']);
