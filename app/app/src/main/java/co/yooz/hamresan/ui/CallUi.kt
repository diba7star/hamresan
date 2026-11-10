package co.yooz.hamresan.ui

import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.yooz.hamresan.App
import co.yooz.hamresan.mesh.MeshState
import kotlinx.coroutines.delay

/** Full-screen call UI shown above everything while a call is ringing or active. */
@Composable
fun CallOverlay() {
    val app = App.instance
    val call by app.mesh.call.collectAsStateWithLifecycle()
    val speaker by app.mesh.speaker.collectAsStateWithLifecycle()
    val c = call ?: return
    val ctx = LocalContext.current
    val ask = rememberMicPermission()
    var muted by remember(c.id) { mutableStateOf(false) }
    var elapsed by remember(c.id) { mutableStateOf(0L) }

    // ring + vibrate while an incoming call is ringing
    if (c is MeshState.Call.Ringing) DisposableEffect(c.id) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ring = try { RingtoneManager.getRingtone(ctx, uri)?.also { it.play() } } catch (_: Exception) { null }
        val vib = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(VibratorManager::class.java))?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        try { vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 0)) } catch (_: Exception) {}
        onDispose { try { ring?.stop() } catch (_: Exception) {}; try { vib?.cancel() } catch (_: Exception) {} }
    }
    if (c is MeshState.Call.Active) LaunchedEffect(c.id) { while (true) { elapsed = (System.currentTimeMillis() - c.since) / 1000; delay(1000) } }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF134E4A)))).clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Avatar(c.peerName, c.peerName, 110, ring = Brush.sweepGradient(listOf(Color(0xFF5EEAD4), Color(0xFF0EA5E9), Color(0xFF5EEAD4))))
            Spacer(Modifier.height(20.dp))
            Text(c.peerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                when (c) {
                    is MeshState.Call.Outgoing -> "در حال زنگ زدن…"
                    is MeshState.Call.Ringing -> "تماس صوتی ورودی"
                    is MeshState.Call.Active -> "%02d:%02d".format(elapsed / 60, elapsed % 60).fa()
                },
                color = Color.White.copy(alpha = .8f), fontSize = 16.sp
            )
            if (c.hops > 1) {
                Spacer(Modifier.height(6.dp))
                Text("از طریق ${(c.hops - 1).fa()} همسایه رابط", color = Color(0xFF5EEAD4), fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("بدون اینترنت · رمزنگاری Nearby", color = Color.White.copy(alpha = .5f), fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(56.dp))
            when (c) {
                is MeshState.Call.Ringing -> Row(horizontalArrangement = Arrangement.spacedBy(70.dp)) {
                    RoundAction(Icons.Default.Close, "رد", Color(0xFFDC2626)) { app.mesh.commands.tryEmit(MeshState.Command.CallEnd(c.id)) }
                    RoundAction(Icons.Default.Call, "پاسخ", Color(0xFF16A34A)) { ask { app.mesh.commands.tryEmit(MeshState.Command.CallAccept(c.id)) } }
                }
                is MeshState.Call.Active -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        RoundAction(Icons.Default.Warning, if (muted) "صدا باز" else "بی‌صدا", if (muted) Color.White else Color.White.copy(alpha = .18f), small = true, dark = muted) {
                            muted = !muted; app.mesh.commands.tryEmit(MeshState.Command.CallMute(muted))
                        }
                        RoundAction(Icons.Default.Notifications, if (speaker) "گوشی" else "بلندگو", if (speaker) Color.White else Color.White.copy(alpha = .18f), small = true, dark = speaker) {
                            app.mesh.commands.tryEmit(MeshState.Command.CallSpeaker(!speaker))
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                    RoundAction(Icons.Default.Close, "پایان تماس", Color(0xFFDC2626)) { app.mesh.commands.tryEmit(MeshState.Command.CallEnd(c.id)) }
                }
                is MeshState.Call.Outgoing -> RoundAction(Icons.Default.Close, "لغو", Color(0xFFDC2626)) { app.mesh.commands.tryEmit(MeshState.Command.CallEnd(c.id)) }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, small: Boolean = false, dark: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (small) 62.dp else 74.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (dark) Color(0xFF0F172A) else Color.White, modifier = Modifier.size(if (small) 26.dp else 32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.5.sp)
    }
}

/** Small round "call" button used next to people. */
@Composable
fun CallButton(pub: String, name: String) {
    val app = App.instance
    val ask = rememberMicPermission()
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .clickable { ask { app.mesh.commands.tryEmit(MeshState.Command.CallStart(pub, name)) } },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.Call, "تماس با $name", tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
fun ReachableSection() {
    val app = App.instance
    val reachable by app.mesh.reachable.collectAsStateWithLifecycle()
    if (reachable.isEmpty()) return
    Section("در دسترس از طریق همسایه‌ها") {
        Text("این افراد مستقیم به شما وصل نیستند؛ تماس از طریق همسایه‌ی میانی (رله) برقرار می‌شود.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        reachable.values.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(r.name.ifBlank { "?" }, r.pub, 36)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name.ifBlank { "همسایه" }, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text("از طریق ${r.viaName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CallButton(r.pub, r.name.ifBlank { "همسایه" })
            }
        }
    }
}
