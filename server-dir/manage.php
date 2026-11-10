<?php
// Center admin: list and delete registered community IDs. Open: manage.php?token=ADMIN_TOKEN
declare(strict_types=1);
$cfgFile = __DIR__ . '/config.php';
if (!file_exists($cfgFile)) { http_response_code(500); exit('center not installed: open install.php'); }
$cfg = require $cfgFile;
$token = (string) ($_REQUEST['token'] ?? '');
if ($token === '' || !hash_equals((string) $cfg['admin_token'], $token)) { http_response_code(403); exit('forbidden'); }
$pdo = new PDO('sqlite:' . __DIR__ . '/data/directory.sqlite', null, null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$pdo->exec('CREATE TABLE IF NOT EXISTS communities (id TEXT PRIMARY KEY, pub TEXT NOT NULL UNIQUE, name TEXT NOT NULL, created INTEGER, ip TEXT)');
$msg = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['do'] ?? '') === 'del') {
    $st = $pdo->prepare('DELETE FROM communities WHERE id = ?'); $st->execute([(string) ($_POST['id'] ?? '')]);
    $msg = 'حذف شد. (گوشی‌هایی که این محله را دنبال می‌کنند، مطالبش را همچنان دارند؛ فقط شناسه آزاد شد.)';
}
$rows = $pdo->query('SELECT id, name, created FROM communities ORDER BY created DESC')->fetchAll(PDO::FETCH_ASSOC);
$h = fn($s) => htmlspecialchars((string) $s, ENT_QUOTES, 'UTF-8');
?>
<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex">
<title>پنل مرکز — <?= $h($cfg['center_name']) ?></title>
<style>body{font-family:system-ui,Tahoma,sans-serif;background:#f1f5f9;margin:0;padding:24px;color:#0f172a}.card{background:#fff;border-radius:12px;padding:20px;max-width:720px;margin:0 auto 16px;box-shadow:0 1px 3px rgba(0,0,0,.08)}h1{font-size:20px;margin:0 0 8px}table{width:100%;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid #e2e8f0;text-align:right;font-size:14px}button{background:#b91c1c;color:#fff;border:0;border-radius:8px;padding:6px 12px;cursor:pointer}.muted{color:#64748b;font-size:13px}.msg{background:#ccfbf1;color:#134e4a;padding:10px 14px;border-radius:8px;margin-bottom:12px}code{direction:ltr;display:inline-block}</style></head>
<body>
<div class="card"><h1><?= $h($cfg['center_name']) ?></h1><div class="muted">شناسه مرکز: <code><?= $h($cfg['center_id']) ?></code> · <?= count($rows) ?> محله ثبت‌شده</div></div>
<?php if ($msg): ?><div class="card msg"><?= $h($msg) ?></div><?php endif; ?>
<div class="card">
<?php if (!$rows): ?><div class="muted">هنوز محله‌ای ثبت نشده.</div><?php else: ?>
<table><tr><th>شناسه</th><th>نام</th><th>تاریخ</th><th></th></tr>
<?php foreach ($rows as $r): ?>
<tr><td><code><?= $h($r['id']) ?></code></td><td><?= $h($r['name']) ?></td><td class="muted"><?= date('Y-m-d', (int) ($r['created'] / 1000)) ?></td>
<td><form method="post" onsubmit="return confirm('شناسه حذف شود؟')"><input type="hidden" name="token" value="<?= $h($token) ?>"><input type="hidden" name="do" value="del"><input type="hidden" name="id" value="<?= $h($r['id']) ?>"><button>حذف</button></form></td></tr>
<?php endforeach; ?></table><?php endif; ?>
</div></body></html>
