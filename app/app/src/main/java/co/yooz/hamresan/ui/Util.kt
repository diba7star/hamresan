package co.yooz.hamresan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import co.yooz.hamresan.data.MAX_IMAGE_BYTES
import java.io.ByteArrayOutputStream
import kotlin.math.max

fun String.fa(): String {
    val d = "۰۱۲۳۴۵۶۷۸۹"
    val sb = StringBuilder()
    for (ch in this) sb.append(if (ch in '0'..'9') d[ch - '0'] else ch)
    return sb.toString()
}

fun Int.fa() = toString().fa()
fun Long.fa() = toString().fa()

fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val d = max(0L, now - ms)
    val m = d / 60_000
    return when {
        m < 1 -> "همین حالا"
        m < 60 -> "${m.fa()} دقیقه پیش"
        m < 24 * 60 -> "${(m / 60).fa()} ساعت پیش"
        m < 30 * 24 * 60 -> "${(m / 1440).fa()} روز پیش"
        else -> "${(m / 43200).fa()} ماه پیش"
    }
}

/** Today's date in the Persian (Jalali) calendar, e.g. «سه‌شنبه ۱۲ آبان ۱۴۰۵». */
fun persianDate(): String = try {
    val loc = android.icu.util.ULocale("fa_IR@calendar=persian")
    val fmt = android.icu.text.SimpleDateFormat("EEEE d MMMM yyyy", loc)
    fmt.format(java.util.Date()).fa()
} catch (e: Exception) {
    java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale("fa")).format(java.util.Date()).fa()
}

fun remainingDays(expiresAt: Long): String {
    val d = (expiresAt - System.currentTimeMillis()) / 86_400_000L
    return if (d <= 0) "امروز منقضی می‌شود" else "${d.fa()} روز باقی‌مانده"
}

/** Decode, downscale to <=1024px and re-encode as JPEG under MAX_IMAGE_BYTES. */
fun compressImage(ctx: Context, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val probe = ctx.contentResolver.openInputStream(uri) ?: return null
    probe.use { BitmapFactory.decodeStream(it, null, bounds) }   // returns null by design in bounds mode
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val maxSide = 1024
    val scale = minOf(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
    if (scale < 1f) {
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        if (scaled !== bmp) bmp.recycle()
        bmp = scaled
    }
    var quality = 85
    var out: ByteArray
    while (true) {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        out = bos.toByteArray()
        if (out.size <= MAX_IMAGE_BYTES || quality <= 35) break
        quality -= 10
    }
    if (out.size > MAX_IMAGE_BYTES) {
        // still too big: shrink dimensions and retry once
        val small = Bitmap.createScaledBitmap(bmp, bmp.width * 2 / 3, bmp.height * 2 / 3, true)
        val bos = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 60, bos)
        out = bos.toByteArray()
    }
    return if (out.size <= MAX_IMAGE_BYTES) out else null
}
