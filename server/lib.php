<?php
// Shared helpers for api.php and manage.php (Hamresan server, PHP >= 7.2 with sodium).
declare(strict_types=1);

// Ed25519 without the sodium extension: pure-PHP polyfill (paragonie/sodium_compat).
if (!function_exists('sodium_crypto_sign_detached')) require_once __DIR__ . '/vendor/sodium_compat/autoload.php';

const MAX_TITLE = 120;
const MAX_BODY = 4000;
const MAX_IMAGE_BYTES = 200 * 1024;

function cfg(): array {
    static $c = null;
    if ($c === null) {
        $f = __DIR__ . '/config.php';
        if (!file_exists($f)) fail('server not installed: open install.php', 500);
        $c = require $f;
    }
    return $c;
}

function db(): PDO {
    static $pdo = null;
    if ($pdo !== null) return $pdo;
    $path = cfg()['db_path'];
    $dir = dirname($path);
    if (!is_dir($dir)) mkdir($dir, 0700, true);
    $ht = $dir . '/.htaccess';
    if (!file_exists($ht)) file_put_contents($ht, "Require all denied\nDeny from all\n");
    $pdo = new PDO('sqlite:' . $path, null, null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $pdo->exec('PRAGMA journal_mode=WAL');
    $pdo->exec('CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, hash TEXT NOT NULL, name TEXT NOT NULL, created INTEGER)');
    $pdo->exec('CREATE TABLE IF NOT EXISTS authors (pub TEXT PRIMARY KEY, name TEXT, cert TEXT, updated INTEGER)');
    $pdo->exec('CREATE TABLE IF NOT EXISTS items (
        id TEXT PRIMARY KEY, type TEXT, author TEXT, seq INTEGER, parentId TEXT, title TEXT, body TEXT,
        img TEXT, ih TEXT, c INTEGER, e INTEGER, sig TEXT, received INTEGER, UNIQUE(author, seq))');
    $pdo->exec('CREATE INDEX IF NOT EXISTS idx_items_parent ON items(parentId)');
    return $pdo;
}

function fail(string $msg, int $code = 400): void {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['ok' => false, 'error' => $msg], JSON_UNESCAPED_UNICODE);
    exit;
}

function ok(array $data): void {
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['ok' => true] + $data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function admin_keypair(): string {
    $seed = base64_decode(cfg()['admin_seed'], true);
    if ($seed === false || strlen($seed) !== SODIUM_CRYPTO_SIGN_SEEDBYTES) fail('bad admin seed', 500);
    return sodium_crypto_sign_seed_keypair($seed);
}

function admin_pub_b64(): string {
    return base64_encode(sodium_crypto_sign_publickey(admin_keypair()));
}

/** Public identity of this community, sent to every client. */
function community_info(): array {
    $c = cfg();
    return ['id' => (string) ($c['community_id'] ?? ''), 'name' => (string) ($c['community_name'] ?? ''), 'pub' => admin_pub_b64()];
}

function now_ms(): int { return (int) round(microtime(true) * 1000); }

/** Issue a writer certificate for a device public key. */
function issue_cert(string $pubB64, string $name): array {
    $exp = now_ms() + cfg()['cert_days'] * 86400000;
    $msg = implode("\n", ['cert', $pubB64, $name, (string) $exp]);
    $sig = sodium_crypto_sign_detached($msg, sodium_crypto_sign_secretkey(admin_keypair()));
    return ['pub' => $pubB64, 'name' => $name, 'exp' => $exp, 'sig' => base64_encode($sig)];
}

function verify_sig(string $pubB64, string $msg, string $sigB64): bool {
    $pub = base64_decode($pubB64, true);
    $sig = base64_decode($sigB64, true);
    if ($pub === false || $sig === false || strlen($pub) !== 32 || strlen($sig) !== 64) return false;
    return sodium_crypto_sign_verify_detached($sig, $msg, $pub);
}

function cert_valid_at(?array $cert, int $timeMs): bool {
    if (!$cert || !isset($cert['pub'], $cert['name'], $cert['exp'], $cert['sig'])) return false;
    if ($timeMs > (int) $cert['exp']) return false;
    $msg = implode("\n", ['cert', $cert['pub'], $cert['name'], (string) (int) $cert['exp']]);
    return verify_sig(admin_pub_b64(), $msg, $cert['sig']);
}

function item_canonical(array $i): string {
    return implode("\n", ['item', $i['t'], $i['id'], $i['a'], (string) (int) $i['s'], $i['p'] ?? '', $i['ti'] ?? '',
        $i['b'] ?? '', $i['ih'] ?? '', (string) (int) $i['c'], (string) (int) $i['e']]);
}

/** Mirrors Repo.acceptRemote in the app. Returns true if stored. */
function accept_bundle(array $bundle): bool {
    $i = $bundle['item'] ?? null;
    $a = $bundle['author'] ?? null;
    if (!is_array($i)) return false;
    foreach (['t', 'id', 'a', 's', 'c', 'e', 'sig'] as $k) if (!isset($i[$k])) return false;
    if (strlen($i['id']) > 64 || mb_strlen($i['ti'] ?? '') > MAX_TITLE || mb_strlen($i['b'] ?? '') > MAX_BODY) return false;
    if ((int) $i['s'] <= 0) return false;
    $img = $i['img'] ?? '';
    $ih = $i['ih'] ?? '';
    if ($img !== '') {
        $raw = base64_decode($img, true);
        if ($raw === false || strlen($raw) > MAX_IMAGE_BYTES) return false;
        if (hash('sha256', $raw) !== $ih) return false;
    } elseif ($ih !== '' && ($i['t'] === 'post' || $i['t'] === 'vpost') && (int) $i['e'] > now_ms()) {
        return false;
    }
    if (!verify_sig($i['a'], item_canonical($i), $i['sig'])) return false;

    $pdo = db();
    $st = $pdo->prepare('SELECT * FROM authors WHERE pub = ?');
    $st->execute([$i['a']]);
    $existing = $st->fetch(PDO::FETCH_ASSOC) ?: null;
    $existingCert = $existing && $existing['cert'] ? json_decode($existing['cert'], true) : null;
    $incomingCert = (is_array($a) && isset($a['cert']) && is_array($a['cert']) && ($a['cert']['pub'] ?? '') === $i['a']) ? $a['cert'] : null;

    // item kinds: post / vpost (voice post) need a writer cert; comment / vcomment (voice) / like are open
    $isPost = ($i['t'] === 'post' || $i['t'] === 'vpost');
    $isReply = ($i['t'] === 'comment' || $i['t'] === 'vcomment' || $i['t'] === 'like');
    if ($isPost) {
        $cert = null;
        if ($incomingCert && cert_valid_at($incomingCert, (int) $i['c'])) $cert = $incomingCert;
        elseif ($existingCert && cert_valid_at($existingCert, (int) $i['c'])) $cert = $existingCert;
        if (!$cert) return false;
        if (!$existingCert || (int) $cert['exp'] > (int) $existingCert['exp']) {
            upsert_author($i['a'], $cert['name'], json_encode($cert, JSON_UNESCAPED_UNICODE));
        }
    } elseif ($isReply) {
        $st = $pdo->prepare('SELECT 1 FROM items WHERE id = ?');
        $st->execute([$i['p'] ?? '']);
        if (!$st->fetchColumn()) return false;
        if ($i['t'] === 'like') {
            if (($i['b'] ?? '') !== '') return false;
            $st = $pdo->prepare("SELECT 1 FROM items WHERE type = 'like' AND author = ? AND parentId = ?");
            $st->execute([$i['a'], $i['p'] ?? '']);
            if ($st->fetchColumn()) return false;
        }
        if (!$existing) {
            $validIncoming = $incomingCert && cert_valid_at($incomingCert, now_ms()) ? $incomingCert : null;
            $name = $validIncoming ? $validIncoming['name'] : (mb_substr((string) ($a['name'] ?? ''), 0, 40) ?: substr(hash('sha256', base64_decode($i['a'])), 0, 8));
            upsert_author($i['a'], $name, $validIncoming ? json_encode($validIncoming, JSON_UNESCAPED_UNICODE) : '');
        }
    } else {
        return false;
    }

    $st = $pdo->prepare('INSERT OR IGNORE INTO items (id,type,author,seq,parentId,title,body,img,ih,c,e,sig,received) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)');
    $st->execute([$i['id'], $i['t'], $i['a'], (int) $i['s'], $i['p'] ?? '', $i['ti'] ?? '', $i['b'] ?? '', $img, $ih,
        (int) $i['c'], (int) $i['e'], $i['sig'], now_ms()]);
    return $st->rowCount() > 0;
}

function upsert_author(string $pub, string $name, string $cert): void {
    $st = db()->prepare('INSERT INTO authors (pub,name,cert,updated) VALUES (?,?,?,?)
        ON CONFLICT(pub) DO UPDATE SET name=excluded.name, cert=excluded.cert, updated=excluded.updated');
    $st->execute([$pub, $name, $cert, now_ms()]);
}

function server_have(): array {
    $have = [];
    foreach (db()->query('SELECT author, MAX(seq) m FROM items GROUP BY author') as $r) $have[$r['author']] = (int) $r['m'];
    return $have;
}

function author_json(string $pub): ?array {
    $st = db()->prepare('SELECT * FROM authors WHERE pub = ?');
    $st->execute([$pub]);
    $a = $st->fetch(PDO::FETCH_ASSOC);
    if (!$a) return null;
    $o = ['pub' => $a['pub'], 'name' => $a['name']];
    if ($a['cert']) $o['cert'] = json_decode($a['cert'], true);
    return $o;
}

function row_to_item(array $r): array {
    return ['t' => $r['type'], 'id' => $r['id'], 'a' => $r['author'], 's' => (int) $r['seq'], 'p' => $r['parentId'],
        'ti' => $r['title'], 'b' => $r['body'], 'img' => $r['img'] ?? '', 'ih' => $r['ih'], 'c' => (int) $r['c'],
        'e' => (int) $r['e'], 'sig' => $r['sig']];
}

/** Items the client lacks, bounded. */
function diff_for(array $theirHave, int $maxItems = 50, int $maxBytes = 1500000): array {
    $out = [];
    $bytes = 0;
    $pdo = db();
    foreach (server_have() as $author => $max) {
        $theirs = (int) ($theirHave[$author] ?? 0);
        if ($max <= $theirs) continue;
        $authorJson = author_json($author);
        $st = $pdo->prepare('SELECT * FROM items WHERE author = ? AND seq > ? ORDER BY seq ASC LIMIT ?');
        $st->execute([$author, $theirs, $maxItems - count($out)]);
        foreach ($st->fetchAll(PDO::FETCH_ASSOC) as $r) {
            $item = row_to_item($r);
            $bytes += strlen($item['img']) + strlen($item['b']) + 400;
            $b = ['item' => $item];
            if ($authorJson) $b['author'] = $authorJson;
            $out[] = $b;
            if (count($out) >= $maxItems || $bytes >= $maxBytes) return $out;
        }
    }
    return $out;
}
