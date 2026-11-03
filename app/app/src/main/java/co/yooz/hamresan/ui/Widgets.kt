package co.yooz.hamresan.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.yooz.hamresan.data.PostView
import kotlin.math.abs

/** Deterministic pleasant gradient for an author/community key. */
fun brushFor(key: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF0F766E), Color(0xFF5EEAD4)), listOf(Color(0xFFB23A2A), Color(0xFFF59E0B)),
        listOf(Color(0xFF1E1B4B), Color(0xFFBE185D)), listOf(Color(0xFF334155), Color(0xFF94A3B8)),
        listOf(Color(0xFF065F46), Color(0xFFA7F3D0)), listOf(Color(0xFF7C2D12), Color(0xFFFDBA74)),
        listOf(Color(0xFF1D4ED8), Color(0xFF93C5FD)), listOf(Color(0xFF6D28D9), Color(0xFFF0ABFC))
    )
    val p = palettes[abs(key.hashCode()) % palettes.size]
    return Brush.linearGradient(p)
}

@Composable
fun Avatar(label: String, key: String, size: Int = 34, ring: Brush? = null, modifier: Modifier = Modifier) {
    val outer = if (ring != null) size + 6 else size
    Box(
        modifier.size(outer.dp).clip(CircleShape).background(ring ?: SolidColor(Color.Transparent)).padding(if (ring != null) 2.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size((if (ring != null) size + 2 else size).dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(if (ring != null) 2.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(size.dp).clip(CircleShape).background(brushFor(key)), contentAlignment = Alignment.Center) {
                Text(label.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.42).sp)
            }
        }
    }
}

/** A square/4:5 tile used when a post has no photo: title on a gradient. */
@Composable
fun TextTile(text: String, key: String, modifier: Modifier = Modifier, fontSize: Int = 20) {
    Box(modifier.background(brushFor(key)).padding(22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize.sp, lineHeight = (fontSize * 1.6).sp, textAlign = TextAlign.Center)
    }
}

fun sharePost(ctx: android.content.Context, p: PostView, communityName: String) {
    val text = buildString {
        if (p.title.isNotBlank()) append(p.title).append("\n\n")
        if (p.body.isNotBlank()) append(p.body).append("\n\n")
        append("— ").append(p.authorName ?: "").append(" · ").append(communityName).append(" · همرسان")
    }
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, "اشتراک‌گذاری مطلب"))
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val bg = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
    )
}
