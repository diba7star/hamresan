<div dir="rtl">

# ساخت برنامه از سورس

## نیازمندی‌ها

- JDK 17
- Android SDK با `compileSdk 36` (Android Studio یا command-line tools)
- اینترنت برای دانلود وابستگی‌ها در اولین بار

## ساخت نسخه debug

```
cd app
./gradlew assembleDebug        # ویندوز: .\gradlew.bat assembleDebug
```

خروجی: `app/app/build/outputs/apk/debug/app-debug.apk`

نسخه debug اتصال `http://` را مجاز می‌داند و دایرکتوری را روی `http://10.0.2.2:8090/index.php` (شبیه‌ساز → سیستم شما) می‌بیند تا بتوانید دایرکتوری و سرور را محلی تست کنید:

```
php -S 127.0.0.1:8090 -t server-dir
php -S 127.0.0.1:8089 -t server
```

## ساخت نسخه release (امضاشده)

۱. یک keystore بسازید:

```
keytool -genkeypair -v -keystore my-release.jks -alias hamresan -keyalg RSA -keysize 2048 -validity 10000
```

۲. فایل `app/keystore.properties` بسازید (در `.gitignore` است):

```
storeFile=../path/to/my-release.jks
storePassword=...
keyAlias=hamresan
keyPassword=...
```

۳. در `app/app/build.gradle.kts` مقدار `DIRECTORY_URL` را روی دایرکتوری خودتان بگذارید (یا پیش‌فرض را نگه دارید) و `versionCode`/`versionName` را بالا ببرید.

۴. `./gradlew assembleRelease` → `app/app/build/outputs/apk/release/app-release.apk`

**کلید امضا را گم نکنید؛** به‌روزرسانی روی گوشی‌ها فقط با همان کلید ممکن است.

## ساختار کد

| مسیر | توضیح |
|---|---|
| `crypto/Crypto.kt` | Ed25519، SHA-256، base64 |
| `data/Entities.kt`, `Dao.kt` | Room: `items` (post/comment/like) و `authors` |
| `data/Repo.kt` | ساخت و پذیرش آیتم‌های امضاشده، محاسبه تفاوت برای همگام‌سازی، محله‌ها، صدور مجوز |
| `data/Prefs.kt` | هویت گوشی، محله‌های دنبال‌شده، تنظیمات، تم |
| `mesh/MeshService.kt` | سرویس پس‌زمینه Nearby Connections و پروتکل hello/items/cert |
| `net/ServerApi.kt` | دایرکتوری، ورود، همگام‌سازی با سرور |
| `ui/Theme.kt` | سه ظاهر (Skin) |
| `ui/Screens.kt` | همه صفحه‌ها |

</div>
