package co.yooz.hamresan.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import co.yooz.hamresan.data.MAX_VOICE_SECONDS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Records short voice notes as small AAC files (≈15 KB per 10 s). */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean = try {
        val f = File.createTempFile("voice", ".m4a", ctx.cacheDir)
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16000)
        r.setAudioEncodingBitRate(16000)
        r.setMaxDuration(MAX_VOICE_SECONDS * 1000)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        rec = r; file = f; startedAt = System.currentTimeMillis()
        true
    } catch (e: Exception) {
        cleanup(); false
    }

    /** @return audio bytes + duration in seconds, or null if too short / failed. */
    fun stop(): Pair<ByteArray, Int>? {
        val r = rec ?: return null
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtMost(MAX_VOICE_SECONDS)
        return try {
            r.stop(); r.release()
            val bytes = file?.readBytes()
            cleanup()
            if (bytes == null || bytes.size < 1500 || secs < 1) null else bytes to maxOf(1, secs)
        } catch (e: Exception) {
            cleanup(); null
        }
    }

    private fun cleanup() {
        try { rec?.release() } catch (_: Exception) {}
        rec = null; file?.delete(); file = null
    }
}

/** One shared player so only one voice note plays at a time. */
object VoicePlayer {
    private var mp: MediaPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing

    fun toggle(ctx: Context, id: String, bytes: ByteArray) {
        if (_playing.value == id) { stop(); return }
        stop()
        try {
            val f = File(ctx.cacheDir, "play_$id.m4a"); if (!f.exists()) f.writeBytes(bytes)
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { stop() }
            p.prepare(); p.start()
            mp = p; _playing.value = id
        } catch (e: Exception) { stop() }
    }

    fun stop() {
        try { mp?.stop(); mp?.release() } catch (_: Exception) {}
        mp = null; _playing.value = null
    }

    fun durationSeconds(ctx: Context, id: String, bytes: ByteArray): Int = try {
        val f = File(ctx.cacheDir, "play_$id.m4a"); if (!f.exists()) f.writeBytes(bytes)
        val m = MediaMetadataRetriever(); m.setDataSource(f.absolutePath)
        val ms = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        m.release(); (ms / 1000).toInt()
    } catch (e: Exception) { 0 }
}
