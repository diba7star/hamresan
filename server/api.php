<?php
// Hamresan community server API. POST JSON {"action": "login"|"sync"|"push", ...}
// GET returns the community identity (id, name, admin public key).
declare(strict_types=1);
require __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    ok(['service' => 'hamresan', 'community' => community_info()]);
}
$raw = file_get_contents('php://input', false, null, 0, 6 * 1024 * 1024);
$req = json_decode((string) $raw, true);
if (!is_array($req)) fail('bad json');

switch ($req['action'] ?? '') {
    case 'login':
        $u = trim((string) ($req['username'] ?? ''));
        $p = (string) ($req['password'] ?? '');
        $pub = (string) ($req['pub'] ?? '');
        if ($u === '' || $p === '' || base64_decode($pub, true) === false || strlen(base64_decode($pub, true)) !== 32) fail('bad request');
        usleep(300000); // slow down guessing a little
        $st = db()->prepare('SELECT * FROM users WHERE username = ?');
        $st->execute([$u]);
        $user = $st->fetch(PDO::FETCH_ASSOC);
        if (!$user || !password_verify($p, $user['hash'])) fail('نام کاربری یا رمز درست نیست', 401);
        $cert = issue_cert($pub, $user['name']);
        upsert_author($pub, $user['name'], json_encode($cert, JSON_UNESCAPED_UNICODE));
        ok(['cert' => $cert, 'community' => community_info()]);

    case 'sync':
        $have = is_array($req['have'] ?? null) ? $req['have'] : [];
        ok(['community' => community_info(), 'have' => (object) server_have(), 'items' => diff_for($have)]);

    case 'push':
        $items = is_array($req['items'] ?? null) ? $req['items'] : [];
        if (count($items) > 200) fail('too many');
        $n = 0;
        foreach ($items as $b) if (is_array($b) && accept_bundle($b)) $n++;
        ok(['accepted' => $n]);

    default:
        fail('unknown action');
}
