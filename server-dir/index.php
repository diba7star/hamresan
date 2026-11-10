<?php
// Hamresan Directory: the only central piece. Maps a community ID to its admin public key.
// Stores NO server addresses. IDs are first-come and can only be registered with a signature
// made by the admin key itself, so an ID can never be taken over.
//
//   GET  ?id=<id>        -> {ok:true, id, pub, name, created}   or {ok:false, error:"not_found"}
//   GET  ?check=<id>     -> {ok:true, available:true|false}
//   POST {action:"register", id, pub, name, ts, sig}   sig = Ed25519(pub) over "register\n{id}\n{pub}\n{name}\n{ts}"
declare(strict_types=1);
if (!function_exists('sodium_crypto_sign_verify_detached')) require_once __DIR__ . '/vendor/sodium_compat/autoload.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

const ID_RE = '/^[a-z0-9][a-z0-9-]{2,30}$/';

function out(array $o, int $code = 200): void {
    http_response_code($code);
    echo json_encode($o, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function db(): PDO {
    $dir = __DIR__ . '/data';
    if (!is_dir($dir)) mkdir($dir, 0700, true);
    if (!file_exists("$dir/.htaccess")) file_put_contents("$dir/.htaccess", "Require all denied\nDeny from all\n");
    $pdo = new PDO('sqlite:' . $dir . '/directory.sqlite', null, null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $pdo->exec('CREATE TABLE IF NOT EXISTS communities (id TEXT PRIMARY KEY, pub TEXT NOT NULL UNIQUE, name TEXT NOT NULL, created INTEGER, ip TEXT)');
    return $pdo;
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    if (isset($_GET['check'])) {
        $id = strtolower(trim((string) $_GET['check']));
        if (!preg_match(ID_RE, $id)) out(['ok' => false, 'error' => 'bad_id']);
        $st = db()->prepare('SELECT 1 FROM communities WHERE id = ?');
        $st->execute([$id]);
        out(['ok' => true, 'id' => $id, 'available' => !$st->fetchColumn()]);
    }
    if (isset($_GET['list'])) {
        // Public catalogue: every registered community (the app shows it so users never type IDs by hand).
        $q = mb_substr(trim((string) ($_GET['q'] ?? '')), 0, 40);
        $page = max(0, (int) ($_GET['page'] ?? 0));
        $pdo = db();
        if ($q !== '') {
            $st = $pdo->prepare('SELECT id, pub, name, created FROM communities WHERE id LIKE ? OR name LIKE ? ORDER BY name LIMIT 100 OFFSET ?');
            $st->execute(['%' . $q . '%', '%' . $q . '%', $page * 100]);
        } else {
            $st = $pdo->prepare('SELECT id, pub, name, created FROM communities ORDER BY name LIMIT 100 OFFSET ?');
            $st->execute([$page * 100]);
        }
        $rows = $st->fetchAll(PDO::FETCH_ASSOC);
        $total = (int) $pdo->query('SELECT COUNT(*) FROM communities')->fetchColumn();
        out(['ok' => true, 'total' => $total, 'page' => $page, 'items' => $rows]);
    }
    if (isset($_GET['id'])) {
        $id = strtolower(trim((string) $_GET['id']));
        if (!preg_match(ID_RE, $id)) out(['ok' => false, 'error' => 'bad_id']);
        $st = db()->prepare('SELECT id, pub, name, created FROM communities WHERE id = ?');
        $st->execute([$id]);
        $r = $st->fetch(PDO::FETCH_ASSOC);
        if (!$r) out(['ok' => false, 'error' => 'not_found'], 404);
        out(['ok' => true] + $r);
    }
    // Root: identity of this center (the app shows the name after the user types the center ID).
    $cfg = file_exists(__DIR__ . '/config.php') ? (require __DIR__ . '/config.php') : [];
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $path = rtrim(dirname($_SERVER['SCRIPT_NAME'] ?? '/'), '/\\');
    $total = (int) db()->query('SELECT COUNT(*) FROM communities')->fetchColumn();
    out(['ok' => true, 'service' => 'hamresan-directory', 'version' => 2,
        'center' => ['id' => (string) ($cfg['center_id'] ?? ($host . $path)), 'name' => (string) ($cfg['center_name'] ?? ''), 'communities' => $total]]);
}

$req = json_decode((string) file_get_contents('php://input', false, null, 0, 16384), true);
if (!is_array($req) || ($req['action'] ?? '') !== 'register') out(['ok' => false, 'error' => 'bad_request'], 400);

$id = strtolower(trim((string) ($req['id'] ?? '')));
$pub = (string) ($req['pub'] ?? '');
$name = mb_substr(trim((string) ($req['name'] ?? '')), 0, 60);
$ts = (int) ($req['ts'] ?? 0);
$sig = (string) ($req['sig'] ?? '');

if (!preg_match(ID_RE, $id)) out(['ok' => false, 'error' => 'bad_id'], 400);
if ($name === '') out(['ok' => false, 'error' => 'bad_name'], 400);
$pubRaw = base64_decode($pub, true);
$sigRaw = base64_decode($sig, true);
if ($pubRaw === false || strlen($pubRaw) !== 32 || $sigRaw === false || strlen($sigRaw) !== 64) out(['ok' => false, 'error' => 'bad_key'], 400);
if (abs(time() * 1000 - $ts) > 15 * 60 * 1000) out(['ok' => false, 'error' => 'clock'], 400);
$msg = implode("\n", ['register', $id, $pub, $name, (string) $ts]);
if (!sodium_crypto_sign_verify_detached($sigRaw, $msg, $pubRaw)) out(['ok' => false, 'error' => 'bad_signature'], 403);

$pdo = db();
$st = $pdo->prepare('SELECT pub FROM communities WHERE id = ?');
$st->execute([$id]);
$existing = $st->fetchColumn();
if ($existing !== false) {
    if ($existing === $pub) out(['ok' => true, 'id' => $id, 'already' => true]);
    out(['ok' => false, 'error' => 'taken'], 409);
}
$st = $pdo->prepare('SELECT id FROM communities WHERE pub = ?');
$st->execute([$pub]);
if ($st->fetchColumn()) out(['ok' => false, 'error' => 'key_in_use'], 409);

$st = $pdo->prepare('INSERT INTO communities (id, pub, name, created, ip) VALUES (?,?,?,?,?)');
$st->execute([$id, $pub, $name, (int) round(microtime(true) * 1000), $_SERVER['REMOTE_ADDR'] ?? '']);
out(['ok' => true, 'id' => $id]);
