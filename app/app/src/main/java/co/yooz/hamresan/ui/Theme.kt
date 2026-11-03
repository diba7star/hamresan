package co.yooz.hamresan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The three bundled looks. Everything is offline; switching is instant. */
enum class Skin(val id: String, val title: String, val blurb: String) {
    INSTA("insta", "استوری محله", "سبک اینستاگرام: استوری‌ها، پست‌های تمام‌عرض، لایک و دیدگاه"),
    MAGAZINE("magazine", "نشریه", "سبک وب‌سایت حرفه‌ای: سرلوحه، تیتر سریف، کاغذ کرم"),
    BROWSER("browser", "مرورگر محله", "سبک مرورگر: هر محله یک تب، نوار نشانی، نشان امضا");

    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: INSTA }
}

/** Extra, skin-specific colours that Material's scheme has no slot for. */
data class SkinPalette(
    val accentBrush: Brush,       // story ring / logo
    val chrome: Color,            // browser bars / masthead rule
    val onChrome: Color,
    val paper: Color,             // page background
    val titleFamily: FontFamily,
    val cardCorner: Int,          // dp
    val likeColor: Color
)

val LocalSkin = staticCompositionLocalOf { Skin.INSTA }
val LocalPalette = staticCompositionLocalOf<SkinPalette> { error("no palette") }

private val instaRing = Brush.sweepGradient(listOf(Color(0xFFF9CE34), Color(0xFFEE2A7B), Color(0xFF6228D7), Color(0xFFF9CE34)))

private fun instaLight() = lightColorScheme(
    primary = Color(0xFF0095F6), onPrimary = Color.White, primaryContainer = Color(0xFFE0F2FE), onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFFEE2A7B), secondaryContainer = Color(0xFFFCE7F3), onSecondaryContainer = Color(0xFF831843),
    background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFF5F5F5),
    onSurface = Color(0xFF0F0F0F), onSurfaceVariant = Color(0xFF737373), outline = Color(0xFFDBDBDB)
)
private fun instaDark() = darkColorScheme(
    primary = Color(0xFF3B9DF8), onPrimary = Color.White, primaryContainer = Color(0xFF0C4A6E), onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFFF472B6), secondaryContainer = Color(0xFF831843), onSecondaryContainer = Color(0xFFFCE7F3),
    background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF1A1A1A),
    onSurface = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFFA8A8A8), outline = Color(0xFF363636)
)
private fun magLight() = lightColorScheme(
    primary = Color(0xFFB23A2A), onPrimary = Color.White, primaryContainer = Color(0xFFF6E1DC), onPrimaryContainer = Color(0xFF5A1A12),
    secondary = Color(0xFF1F4E4A), secondaryContainer = Color(0xFFDDEBE8), onSecondaryContainer = Color(0xFF0E2423),
    background = Color(0xFFFBF8F2), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF2EDE3),
    onSurface = Color(0xFF141414), onSurfaceVariant = Color(0xFF6F6A60), outline = Color(0xFFD9D2C3)
)
private fun magDark() = darkColorScheme(
    primary = Color(0xFFE2857A), onPrimary = Color(0xFF3A0E08), primaryContainer = Color(0xFF5A1A12), onPrimaryContainer = Color(0xFFF6E1DC),
    secondary = Color(0xFF8CC5BE), secondaryContainer = Color(0xFF1F4E4A), onSecondaryContainer = Color(0xFFDDEBE8),
    background = Color(0xFF1A1814), surface = Color(0xFF221F1A), surfaceVariant = Color(0xFF2C2822),
    onSurface = Color(0xFFF2EDE3), onSurfaceVariant = Color(0xFFB8B0A0), outline = Color(0xFF4A443A)
)
private fun browserLight() = lightColorScheme(
    primary = Color(0xFF4F8CFF), onPrimary = Color.White, primaryContainer = Color(0xFFE0EBFF), onPrimaryContainer = Color(0xFF0B2A6B),
    secondary = Color(0xFF22C55E), secondaryContainer = Color(0xFFDCFCE7), onSecondaryContainer = Color(0xFF14532D),
    background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF1F3F7),
    onSurface = Color(0xFF111318), onSurfaceVariant = Color(0xFF5F6675), outline = Color(0xFFE6E8EE)
)
private fun browserDark() = darkColorScheme(
    primary = Color(0xFF7FAAFF), onPrimary = Color(0xFF0B2A6B), primaryContainer = Color(0xFF1E3A8A), onPrimaryContainer = Color(0xFFE0EBFF),
    secondary = Color(0xFF4ADE80), secondaryContainer = Color(0xFF14532D), onSecondaryContainer = Color(0xFFDCFCE7),
    background = Color(0xFF0B0C10), surface = Color(0xFF17181C), surfaceVariant = Color(0xFF202228),
    onSurface = Color(0xFFE7E9EE), onSurfaceVariant = Color(0xFF9AA0AD), outline = Color(0xFF2A2D35)
)

@Composable
fun HamresanTheme(skin: Skin, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme
    val palette: SkinPalette
    val typography: Typography
    val shapes: Shapes
    when (skin) {
        Skin.INSTA -> {
            scheme = if (dark) instaDark() else instaLight()
            palette = SkinPalette(instaRing, if (dark) Color.Black else Color.White, scheme.onSurface, scheme.background, FontFamily.SansSerif, 0, Color(0xFFFF3040))
            typography = Typography()
            shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp))
        }
        Skin.MAGAZINE -> {
            scheme = if (dark) magDark() else magLight()
            palette = SkinPalette(Brush.linearGradient(listOf(Color(0xFFC9A24A), Color(0xFFB23A2A))), scheme.onSurface, scheme.surface, scheme.background, FontFamily.Serif, 4, Color(0xFFB23A2A))
            typography = Typography(
                headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
                titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
                titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 25.sp),
                bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 28.sp)
            )
            shapes = Shapes(small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(6.dp))
        }
        Skin.BROWSER -> {
            scheme = if (dark) browserDark() else browserLight()
            palette = SkinPalette(Brush.linearGradient(listOf(Color(0xFF4F8CFF), Color(0xFF22C55E))), Color(0xFF17181C), Color(0xFFE7E9EE), scheme.background, FontFamily.SansSerif, 14, Color(0xFFEF4444))
            typography = Typography()
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp))
        }
    }
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalSkin provides skin, LocalPalette provides palette) {
            content()
        }
    }
}
