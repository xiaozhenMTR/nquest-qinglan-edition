<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }

// ═══════════ 配置 ═══════════
define('ADMIN_PASSWORD', 'qinglan123');
$file = __DIR__ . '/quests.json';

// ═══════════ 密码校验 ═══════════
function checkPassword() {
    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        return ($_GET['password'] ?? '') === ADMIN_PASSWORD;
    }
    $input = json_decode(file_get_contents('php://input'), true);
    return ($input['password'] ?? '') === ADMIN_PASSWORD;
}

if (!checkPassword()) {
    http_response_code(401);
    echo json_encode(['success' => false, 'error' => '密码错误'], JSON_UNESCAPED_UNICODE);
    exit;
}

// ═══════════ 文件操作 ═══════════
function load() {
    global $file;
    if (!file_exists($file)) {
        file_put_contents($file, '[]', LOCK_EX);
    }
    $d = json_decode(file_get_contents($file), true);
    return is_array($d) ? $d : [];
}

function save($d) {
    global $file;
    file_put_contents(
        $file,
        json_encode(array_values($d), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE),
        LOCK_EX
    );
}

// ═══════════ GET: 返回任务列表 ═══════════
if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    load();  // 确保文件存在
    readfile($file);
    exit;
}

// ═══════════ POST: 增删改 ═══════════
$input  = json_decode(file_get_contents('php://input'), true);
$action = $input['action'] ?? '';
$quests = load();

// ── 保存 ──
if ($action === 'save') {
    $id   = $input['id'] ?? '';
    $data = $input['data'] ?? [];

    if (empty($id)) {
        echo json_encode(['success' => false, 'error' => '缺少任务ID'], JSON_UNESCAPED_UNICODE);
        exit;
    }

    $points = $data['points'] ?? [];
    $points = array_map(function ($p) {
        $station = trim((string)($p['station'] ?? ''));
        $point = [
            'code'         => (int)($p['code'] ?? 0),
            'bossbarText'  => $p['bossbarText'] ?? '',
            'teleport'     => !empty($p['teleport']),
        ];
        // MTR 到站判定：station 非空时写入 station + ride（ride=false 表示步行进站也算到达）
        if ($station !== '') {
            $point['station'] = $station;
            $point['ride']    = !empty($p['ride']);
        }
        return $point;
    }, $points);

    $item = [
        'questId'   => $id,
        'questName' => $data['questName'] ?? $data['name'] ?? '',
        'qpReward'  => (int)($data['qpReward'] ?? $data['qp'] ?? 0),
        'type'      => $data['type'] ?? 'one_time',
        'points'    => $points,
    ];

    $found = false;
    foreach ($quests as $i => $q) {
        if (($q['questId'] ?? '') === $id) {
            $quests[$i] = $item;
            $found = true;
            break;
        }
    }
    if (!$found) {
        $quests[] = $item;
    }

    save($quests);
    echo json_encode(['success' => true], JSON_UNESCAPED_UNICODE);
    exit;
}

// ── 删除 ──
if ($action === 'delete') {
    $id = $input['id'] ?? '';
    $quests = array_values(array_filter($quests, function ($q) use ($id) {
        return ($q['questId'] ?? '') !== $id;
    }));
    save($quests);
    echo json_encode(['success' => true], JSON_UNESCAPED_UNICODE);
    exit;
}

// ── 未知 action ──
http_response_code(400);
echo json_encode(['success' => false, 'error' => '未知操作: ' . $action], JSON_UNESCAPED_UNICODE);
