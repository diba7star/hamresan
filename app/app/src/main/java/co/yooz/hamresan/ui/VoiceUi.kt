package co.yooz.hamresan.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.yooz.hamresan.App
import co.yooz.hamresan.mesh.MeshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Playable voice note: play/stop button, duration, progress while playing. */
@Composable
fun VoiceBubble(id: String, bytes: ByteArray, seconds: Int, label: String = "", modifier: Modifier = Modifier, big: Boolean = false) {
    val ctx = LocalContext.current
    val playing by VoicePlayer.playing.collectAsStateWithLifecycle()
    val isMe = playing == id
    var progress by remember(id) { mutableStateOf(0f) }
    LaunchedEffect(isMe) {
        if (isMe) { val start = System.currentTimeMillis(); while (VoicePlayer.playing.value == id) { progress = ((System.currentTimeMillis() - start) / 1000f / maxOf(1, seconds)).coerceIn(0f, 1f); delay(200) } }
        progress = 0f
    }
    Row(
        modifier.clip(RoundedCornerShape(if (big) 18.dp else 24.dp)).background(if (big) brushFor(id) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
            .clickable { VoicePlayer.toggle(ctx, id, bytes) }.padding(if (big) 18.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (big) 56.dp else 36.dp).clip(CircleShape).background(if (big) Color.White.copy(alpha = .25f) else MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(if (isMe) Icons.Default.Close else Icons.Default.PlayArrow, if (isMe) "توقف" else "پخش", tint = Color.White, modifier = Modifier.size(if (big) 30.dp else 20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text((if (label.isNotBlank()) "$label · " else "") + "پیام صوتی · ${seconds.fa()} ثانیه", fontSize = if (big) 15.sp else 12.5.sp, fontWeight = FontWeight.Bold,
                color = if (big) Color.White else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (big) Color.White else MaterialTheme.colorScheme.primary, trackColor = if (big) Color.White.copy(alpha = .3f) else MaterialTheme.colorScheme.outline)
        }
    }
}


/** Asks for RECORD_AUDIO when needed; returns a function that runs [block] once granted. */
@Composable
fun rememberMicPermission(): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) pending?.invoke(); pending = null }
    return { block ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) block()
        else { pending = block; launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

/**
 * Hold-to-record control. While pressed it records; on release it hands back (bytes, seconds).
 * Recording stops by itself at the 60-second limit.
 */
@Composable
fun HoldToRecord(onRecorded: (ByteArray, Int) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctx = LocalContext.current
    val ask = rememberMicPermission()
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var secs by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(recording) { secs = 0; while (recording) { delay(1000); secs++; if (secs >= 60) { recorder.stop()?.let { (b, s) -> onRecorded(b, s) }; recording = false } } }
    Box(
        modifier.clip(if (compact) CircleShape else RoundedCornerShape(18.dp))
            .background(if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    var started = false
                    ask { started = recorder.start(); recording = started }
                    // give the permission dialog / recorder a moment, then wait for release
                    tryAwaitRelease()
                    if (recording) {
                        recording = false
                        val r = recorder.stop()
                        if (r != null) onRecorded(r.first, r.second) else scope.launch { App.instance.mesh.notices.tryEmit("ضبط خیلی کوتاه بود؛ دکمه را نگه دارید و صحبت کنید") }
                    } else if (!started) { /* permission flow */ }
                })
            }
            .padding(if (compact) 10.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (compact) Icon(Icons.Default.Call, "ضبط صدا", tint = Color.White, modifier = Modifier.size(22.dp))
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Call, null, tint = Color.White); Spacer(Modifier.width(10.dp))
            Text(if (recording) "در حال ضبط… ${secs.fa()} ثانیه (رها کنید تا تمام شود)" else "نگه دارید و صحبت کنید", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Live walkie-talkie button: hold to talk to every connected neighbour. */
@Composable
fun PttButton() {
    val app = App.instance
    val ask = rememberMicPermission()
    val talking by app.mesh.pttTalking.collectAsStateWithLifecycle()
    val incoming by app.mesh.pttIncoming.collectAsStateWithLifecycle()
    val peers by app.mesh.peers.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(150.dp).clip(CircleShape)
                .background(if (talking) MaterialTheme.colorScheme.error else if (peers.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                .pointerInput(peers.isEmpty()) {
                    detectTapGestures(onPress = {
                        ask { app.mesh.commands.tryEmit(MeshState.Command.PttStart) }
                        tryAwaitRelease()
                        app.mesh.commands.tryEmit(MeshState.Command.PttStop)
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Call, null, tint = if (peers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, modifier = Modifier.size(44.dp))
                Text(if (talking) "در حال صحبت" else "نگه دارید", color = if (peers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                incoming != null -> "🔊 صدای $incoming در حال پخش"
                peers.isEmpty() -> "بی‌سیم فقط با همسایه‌های متصل کار می‌کند"
                talking -> "${peers.size.fa()} همسایه شما را می‌شنوند"
                else -> "بی‌سیم زنده با ${peers.size.fa()} همسایه متصل (برد ۳۰ تا ۸۰ متر)"
            },
            fontSize = 12.5.sp, color = if (incoming != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
