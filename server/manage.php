<?php
// Writer-account management. Open in a browser: manage.php?token=ADMIN_TOKEN
declare(strict_types=1);
require __DIR__ . '/lib.php';

$token = (string) ($_REQUEST['token'] ?? '');
if ($token === '' || !hash_equals((string) cfg()['admin_token'], $token)) {
    http_response_code(403);
    exit('forbidden');
}
$pdo = db();
$msg = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $do = $_POST['do'] ?? '';
    if ($do === 'add') {
        $u = trim((string) ($_POST['username'] ?? ''));
        $p = (string) ($_POST['password'] ?? '');
        $n = mb_substr(trim((string) ($_POST['name'] ?? '')), 0, 40);
        if (!preg_match('/^[a-zA-Z0-9_.-]{3,32}$/', $u)) $msg = 'نام کاربری فقط حروف انگلیسی، عدد، نقطه، خط تیره (۳ تا ۳۲)';
        elseif (strlen($p) < 8) $msg = 'رمز حداقل ۸ کاراکتر';
        elseif ($n === '') $msg = 'نام نمایشی لازم است';
        else {
            $st = $pdo->prepare('INSERT INTO users (username,hash,name,created) VALUES (?,?,?,?)
                ON CONFLICT(username) DO UPDATE SET hash=excluded.hash, name=excluded.name');
            $st->execute([$u, password_hash($p, PASSWORD_DEFAULT), $n, now_ms()]);
            $msg = "حساب «{$u}» ذخیره شد";
        }
    } elseif ($do === 'del') {
        $st = $pdo->prepare('DELETE FROM users WHERE username = ?');
        $st->execute([(string) ($_POST['username'] ?? '')]);
        $msg = 'حذف شد (مجوزهای صادرشده تا تاریخ انقضا معتبر می‌مانند)';
    }
}
$users = $pdo->query('SELECT username, name, created FROM users ORDER BY username')->fetchAll(PDO::FETCH_ASSOC);
$stats = $pdo->query("SELECT (SELECT COUNT(*) FROM items WHERE type='post') p, (SELECT COUNT(*) FROM items WHERE type='comment') c, (SELECT COUNT(*) FROM authors) a")->fetch(PDO::FETCH_ASSOC);
$h = fn($s) => htmlspecialchars((string) $s, ENT_QUOTES, 'UTF-8');
?>
<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>مدیریت همرسان</title>
<style>
body{font-family:system-ui,Tahoma,sans-serif;background:#f1f5f9;margin:0;padding:24px;color:#0f172a}
.card{background:#fff;border-radius:12px;padding:20px;max-width:640px;margin:0 auto 16px;box-shadow:0 1px 3px rgba(0,0,0,.08)}
h1{font-size:20px;margin:0 0 12px}h2{font-size:16px;margin:0 0 12px}
input{width:100%;box-sizing:border-box;padding:10px;border:1px solid #cbd5e1;border-radius:8px;margin:4px 0 10px;font-size:15px}
button{background:#0f766e;color:#fff;border:0;border-radius:8px;padding:10px 18px;font-size:15px;cursor:pointer}
button.del{background:#b91c1c;padding:6px 12px;font-size:13px}
table{width:100%;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid #e2e8f0;text-align:right}
.msg{background:#ccfbf1;color:#134e4a;padding:10px 14px;border-radius:8px;margin-bottom:12px}
.muted{color:#64748b;font-size:13px}code{background:#f1f5f9;padding:2px 6px;border-radius:4px;direction:ltr;display:inline-block}
</style>
</head>
<body>
<div class="card">
  <h1>مدیریت همرسان</h1>
  <div class="muted">مطلب‌ها: <?= (int) $stats['p'] ?> · دیدگاه‌ها: <?= (int) $stats['c'] ?> · کاربران شناخته‌شده: <?= (int) $stats['a'] ?></div>
  <div class="muted">شناسه عمومی محله: <code><?= $h(cfg()['community_id'] ?? '') ?></code> · نام: <?= $h(cfg()['community_name'] ?? '') ?></div>
  <div class="muted">کلید عمومی مدیر: <code><?= $h(admin_pub_b64()) ?></code></div>
  <details><summary class="muted" style="cursor:pointer">نمایش کلید محرمانه مدیر (برای وارد کردن در گوشی مدیر)</summary><code><?= $h(cfg()['admin_seed'] ?? '') ?></code></details>
  <div class="muted">نشانی سرور برای نویسنده‌ها: <code><?= $h((isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' ? 'https' : 'http') . '://' . ($_SERVER['HTTP_HOST'] ?? '') . rtrim(dirname($_SERVER['SCRIPT_NAME'] ?? '/'), '/\\')) ?>/api.php</code></div>
</div>
<?php if ($msg): ?><div class="card msg"><?= $h($msg) ?></div><?php endif; ?>
<div class="card">
  <h2>افزودن / تغییر حساب نویسنده</h2>
  <form method="post">
    <input type="hidden" name="token" value="<?= $h($token) ?>"><input type="hidden" name="do" value="add">
    <label>نام کاربری (انگلیسی)<input name="username" required autocomplete="off" dir="ltr"></label>
    <label>رمز (حداقل ۸ کاراکتر)<input name="password" type="password" required autocomplete="new-password" dir="ltr"></label>
    <label>نام نمایشی نویسنده<input name="name" required></label>
    <button>ذخیره</button>
  </form>
</div>
<div class="card">
  <h2>حساب‌های نویسنده</h2>
  <?php if (!$users): ?><div class="muted">هنوز حسابی ساخته نشده.</div><?php else: ?>
  <table><tr><th>نام کاربری</th><th>نام نمایشی</th><th></th></tr>
  <?php foreach ($users as $u): ?>
  <tr><td dir="ltr"><?= $h($u['username']) ?></td><td><?= $h($u['name']) ?></td>
    <td><form method="post" onsubmit="return confirm('حذف شود؟')"><input type="hidden" name="token" value="<?= $h($token) ?>"><input type="hidden" name="do" value="del"><input type="hidden" name="username" value="<?= $h($u['username']) ?>"><button class="del">حذف</button></form></td></tr>
  <?php endforeach; ?></table><?php endif; ?>
</div>
</body>
</html>
