package co.yooz.hamresan.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections
import java.util.UUID

/**
 * Two-way voice calls over the mesh, relayed by intermediate neighbours (up to [MAX_HOPS] hops).
 *
 * Signalling (JSON "call" messages, flooded / routed by public key):
 *   invite  {m:"call", a:"invite", id, from, fromName, to, path:[pub…], ttl}   flooded until it reaches `to`
 *   accept  {m:"call", a:"accept", id, path:[pub…]}                             sent back hop by hop along `path`
 *   end     {m:"call", a:"end", id}                                             both directions along the route
 * Audio: one STREAM payload per hop and direction: "CALL" + 36-char id + direction byte ('A' caller→callee,
 * 'B' callee→caller) + raw PCM 16 kHz mono 16-bit. Relays copy bytes to the next hop without touching them.
 */
class CallEngine(
    private val ctx: Context,
    private val state: MeshState,
    private val client: ConnectionsClient,
    private val myPub: () -> String,
    private val myName: () -> String,
    private val send: suspend (String, JSONObject) -> Unit,
    private val endpointOfPub: (String) -> String?
) {
    private class Route(
        val id: String,
        val callerPub: String,
        val calleePub: String,
        val path: List<String>,          // caller … callee (pubs)
        var prevHop: String? = null,     // endpoint toward the caller
        var nextHop: String? = null,     // endpoint toward the callee
        val pipes: MutableList<PipedOutputStream> = Collections.synchronizedList(ArrayList()),
        var recorder: AudioRecord? = null,
        var track: AudioTrack? = null,
        @Volatile var alive: Boolean = true,
        @Volatile var muted: Boolean = false
    )

    private val routes = Collections.synchronizedMap(HashMap<String, Route>())
    private val seenInvites = Collections.synchronizedSet(HashSet<String>())
    private val myRouteId: String? get() = state.call.value?.id

    private fun isMe(pub: String) = pub == myPub()
    private fun nameOf(pub: String, fallback: String) = fallback.ifBlank { pub.take(8) }

    // ---------------- outgoing ----------------

    suspend fun start(toPub: String, toName: String) {
        if (state.call.value != null) { state.notices.tryEmit("یک تماس در جریان است"); return }
        if (state.peers.value.isEmpty()) { state.notices.tryEmit("همسایه‌ای متصل نیست"); return }
        val id = UUID.randomUUID().toString()
        routes[id] = Route(id, myPub(), toPub, listOf(myPub()))
        seenInvites.add(id)
        state.setCall(MeshState.Call.Outgoing(id, toName))
        val msg = JSONObject().put("m", "call").put("a", "invite").put("id", id).put("from", myPub()).put("fromName", myName())
            .put("to", toPub).put("path", JSONArray().put(myPub())).put("ttl", MAX_HOPS)
        for (ep in state.peers.value.keys) send(ep, msg)
        state.log("تماس: زنگ زدن به $toName")
    }

    // ---------------- incoming signalling ----------------

    suspend fun onMessage(fromEndpoint: String, msg: JSONObject) {
        when (msg.optString("a")) {
            "invite" -> onInvite(fromEndpoint, msg)
            "accept" -> onAccept(fromEndpoint, msg)
            "end" -> onEnd(msg.optString("id"), fromEndpoint, forward = true)
            "busy" -> if (state.call.value?.id == msg.optString("id")) { state.notices.tryEmit("مشغول است"); cleanup(msg.optString("id")) }
        }
    }

    private suspend fun onInvite(fromEndpoint: String, msg: JSONObject) {
        val id = msg.optString("id"); if (id.isBlank() || !seenInvites.add(id)) return
        val to = msg.optString("to"); val from = msg.optString("from")
        val path = jsonToList(msg.optJSONArray("path")) + listOfNotNull(if (isMe(to)) myPub() else null)
        if (isMe(to)) {
            if (state.call.value != null) { send(fromEndpoint, JSONObject().put("m", "call").put("a", "busy").put("id", id)); return }
            routes[id] = Route(id, from, myPub(), path, prevHop = fromEndpoint)
            state.setCall(MeshState.Call.Ringing(id, nameOf(from, msg.optString("fromName")), path.size - 1))
            state.notices.tryEmit("📞 تماس از ${msg.optString("fromName")}")
            state.log("تماس ورودی از ${msg.optString("fromName")} (${path.size - 1} پرش)")
            return
        }
        val ttl = msg.optInt("ttl") - 1
        if (ttl < 0 || path.contains(myPub())) return
        // remember where it came from so an accept can travel back
        routes[id] = Route(id, from, to, path + myPub(), prevHop = fromEndpoint)
        val fwd = JSONObject(msg.toString()).put("ttl", ttl).put("path", JSONArray(path + myPub()))
        for (ep in state.peers.value.keys) if (ep != fromEndpoint) send(ep, fwd)
    }

    private suspend fun onAccept(fromEndpoint: String, msg: JSONObject) {
        val id = msg.optString("id"); val r = routes[id] ?: return
        val path = jsonToList(msg.optJSONArray("path"))
        r.nextHop = fromEndpoint
        if (isMe(r.callerPub)) {
            val cur = state.call.value
            if (cur is MeshState.Call.Outgoing && cur.id == id) {
                state.setCall(MeshState.Call.Active(id, cur.peerName, path.size - 1))
                startAudio(r, direction = 'A', toEndpoint = fromEndpoint)
                state.log("تماس برقرار شد (${path.size - 1} پرش)")
            }
            return
        }
        // relay: pass the accept toward the caller
        val prev = r.prevHop ?: return
        send(prev, msg)
    }

    suspend fun accept(id: String) {
        val r = routes[id] ?: return
        val cur = state.call.value as? MeshState.Call.Ringing ?: return
        val prev = r.prevHop ?: return
        send(prev, JSONObject().put("m", "call").put("a", "accept").put("id", id).put("path", JSONArray(r.path)))
        state.setCall(MeshState.Call.Active(id, cur.peerName, cur.hops))
        startAudio(r, direction = 'B', toEndpoint = prev)
        state.log("تماس پذیرفته شد")
    }

    suspend fun end(id: String) {
        val r = routes[id]
        val msg = JSONObject().put("m", "call").put("a", "end").put("id", id)
        if (r != null) { r.prevHop?.let { send(it, msg) }; r.nextHop?.let { send(it, msg) } }
        onEnd(id, null, forward = false)
    }

    private suspend fun onEnd(id: String, fromEndpoint: String?, forward: Boolean) {
        val r = routes[id]
        if (forward && r != null) {
            val msg = JSONObject().put("m", "call").put("a", "end").put("id", id)
            listOfNotNull(r.prevHop, r.nextHop).filter { it != fromEndpoint }.forEach { send(it, msg) }
        }
        cleanup(id)
        if (state.call.value?.id == id) { state.setCall(null); state.log("تماس پایان یافت") }
    }

    suspend fun onPeerLost(endpointId: String) {
        val affected = routes.values.filter { it.prevHop == endpointId || it.nextHop == endpointId }.map { it.id }
        for (id in affected) {
            if (state.call.value?.id == id) state.notices.tryEmit("تماس قطع شد (همسایه رابط از دسترس خارج شد)")
            onEnd(id, endpointId, forward = true)
        }
    }

    fun setMuted(m: Boolean) { myRouteId?.let { routes[it]?.muted = m } }

    fun setSpeaker(on: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION") am.isSpeakerphoneOn = on
        state.setSpeaker(on)
    }

    // ---------------- audio ----------------

    @SuppressLint("MissingPermission")
    private fun startAudio(r: Route, direction: Char, toEndpoint: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeaker(state.speaker.value)
        // outgoing stream to the next hop
        try {
            val out = PipedOutputStream(); val inp = PipedInputStream(out, 64 * 1024)
            out.write(header(r.id, direction))
            r.pipes.add(out)
            client.sendPayload(toEndpoint, Payload.fromStream(inp))
        } catch (_: Exception) {}
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192))
        } catch (e: Exception) { null }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { state.notices.tryEmit("میکروفون در دسترس نیست"); return }
        r.recorder = rec
        rec.startRecording()
        Thread {
            val buf = ByteArray(RATE / 50 * 2)
            val silence = ByteArray(buf.size)
            try {
                while (r.alive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    val src = if (r.muted) silence else buf
                    val dead = ArrayList<PipedOutputStream>()
                    synchronized(r.pipes) { for (p in r.pipes) try { p.write(src, 0, n) } catch (_: Exception) { dead.add(p) } }
                    r.pipes.removeAll(dead.toSet())
                }
            } catch (_: Exception) {}
        }.start()
    }

    /**
     * A STREAM payload with a "CALL" header arrived. Returns true if it was consumed here.
     * The caller must already have read the 4 magic bytes.
     */
    fun onStream(fromEndpoint: String, input: InputStream): Boolean {
        val idBytes = ByteArray(36); var got = 0
        while (got < 36) { val n = input.read(idBytes, got, 36 - got); if (n < 0) return true; got += n }
        val id = String(idBytes)
        val dir = input.read().toChar()
        val r = routes[id] ?: return true
        val endpointHere = (dir == 'A' && isMe(r.calleePub)) || (dir == 'B' && isMe(r.callerPub))
        if (endpointHere) { play(r, input); return true }
        // relay: forward toward the far end
        val next = if (dir == 'A') r.nextHop else r.prevHop
        if (next == null) return true
        try {
            val out = PipedOutputStream(); val inp = PipedInputStream(out, 64 * 1024)
            out.write(header(id, dir))
            client.sendPayload(next, Payload.fromStream(inp))
            val buf = ByteArray(4096)
            while (r.alive) { val n = input.read(buf); if (n < 0) break; if (n > 0) out.write(buf, 0, n) }
            out.close()
        } catch (_: Exception) {}
        return true
    }

    private fun play(r: Route, input: InputStream) {
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, 16384)).build()
        r.track = track
        try {
            track.play()
            val buf = ByteArray(4096)
            while (r.alive) { val n = input.read(buf); if (n < 0) break; if (n > 0) track.write(buf, 0, n) }
        } catch (_: Exception) {
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }

    private fun cleanup(id: String) {
        val r = routes.remove(id) ?: return
        r.alive = false
        try { r.recorder?.stop(); r.recorder?.release() } catch (_: Exception) {}
        synchronized(r.pipes) { r.pipes.forEach { try { it.close() } catch (_: Exception) {} }; r.pipes.clear() }
        try { r.track?.stop() } catch (_: Exception) {}
        if (routes.values.none { isMe(it.callerPub) || isMe(it.calleePub) }) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION") am.isSpeakerphoneOn = false
        }
    }

    private fun header(id: String, dir: Char): ByteArray =
        ByteArrayOutputStream().apply { write(MAGIC.toByteArray()); write(id.toByteArray()); write(dir.code) }.toByteArray()

    private fun jsonToList(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    companion object {
        const val MAGIC = "CALL"
        const val RATE = 16000
        const val MAX_HOPS = 3
    }
}
