package co.yooz.hamresan.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import co.yooz.hamresan.App
import co.yooz.hamresan.R
import co.yooz.hamresan.crypto.Crypto
import co.yooz.hamresan.data.Author
import co.yooz.hamresan.data.Wire
import co.yooz.hamresan.data.WriterCert
import co.yooz.hamresan.ui.MainActivity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Collections

/**
 * Foreground service that keeps Nearby Connections advertising + discovering and
 * gossips signed items with every connected neighbour, restricted to the communities
 * both sides follow.
 *
 * Protocol (JSON, one object per payload):
 *   hello  {m:"hello", pub, name, cert?, cs:[{id,name,apub}], have:{authorPub: maxSeq}}
 *   items  {m:"items", items:[{item:{...}, author:{...}}]}
 *   cert   {m:"cert", cert:{pub,name,exp,sig}}      admin -> peer, grants writer role
 */
class MeshService : Service() {
    private val app get() = App.instance
    private val state get() = app.mesh
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: ConnectionsClient
    private val sendLock = Mutex()

    /** endpointId -> what we believe the peer has (updated as we send). */
    private val peerHave = Collections.synchronizedMap(HashMap<String, MutableMap<String, Long>>())
    /** endpointId -> communities both of us follow. */
    private val peerCommon = Collections.synchronizedMap(HashMap<String, Set<String>>())
    private val pendingRequests = Collections.synchronizedSet(HashSet<String>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        client = Nearby.getConnectionsClient(this)
        startForegroundCompat()
        state.setRunning(true)
        state.log("سرویس شبکه محلی شروع شد")
        startRadios()

        // gossip everything we accept to every other neighbour that follows that community
        scope.launch {
            app.repo.accepted.collect { (item, source) ->
                val community = app.repo.communityOfItem(item) ?: return@collect
                val author = app.db.authors().byPub(item.author)
                val msg = JSONObject().put("m", "items").put("items", JSONArray().put(Wire.bundle(item, author)))
                for (id in state.peers.value.keys) {
                    if (id == source) continue
                    val have = peerHave[id] ?: continue
                    if (community !in (peerCommon[id] ?: emptySet())) continue
                    if ((have[item.author] ?: 0L) >= item.seq) continue
                    send(id, msg)
                    have[item.author] = maxOf(have[item.author] ?: 0L, item.seq)
                    state.updatePeer(id) { it.copy(sent = it.sent + 1) }
                }
            }
        }
        // UI commands
        scope.launch {
            state.commands.collect { cmd ->
                when (cmd) {
                    is MeshState.Command.GrantWriter -> grantWriter(cmd.endpointId)
                    MeshState.Command.ResyncAll -> state.peers.value.keys.forEach { sendHello(it) }
                }
            }
        }
        // periodic safety net: re-hello and restart radios
        scope.launch {
            var n = 0
            while (true) {
                delay(5 * 60_000L)
                n++
                state.peers.value.keys.forEach { sendHello(it) }
                app.repo.housekeeping()
                if (n % 3 == 0 && state.peers.value.isEmpty()) {
                    state.log("راه‌اندازی مجدد جست‌وجو")
                    stopRadios(); delay(1500); startRadios()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        state.log("سرویس شبکه محلی متوقف شد")
        stopRadios()
        client.stopAllEndpoints()
        scope.cancel()
        state.setRunning(false)
        super.onDestroy()
    }

    // ---------------- radios ----------------

    /** Advertised as "name#shortId" so a device can recognise (and skip) itself. */
    private fun myEndpointName() = app.prefs.displayName().ifBlank { "همسایه" } + "#" + Crypto.shortId(app.prefs.pub)
    private fun isSelf(endpointName: String) = endpointName.endsWith("#" + Crypto.shortId(app.prefs.pub))
    private fun plainName(endpointName: String) = endpointName.substringBeforeLast('#')

    private fun startRadios() {
        val name = myEndpointName()
        client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { state.log("خطای اعلان حضور: ${it.message}") }
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { state.log("خطای جست‌وجو: ${it.message}") }
    }

    private fun stopRadios() {
        try { client.stopAdvertising(); client.stopDiscovery() } catch (_: Exception) {}
    }

    private fun drop(endpointId: String) {
        try { client.disconnectFromEndpoint(endpointId) } catch (_: Exception) {}
        state.removePeer(endpointId); peerHave.remove(endpointId); peerCommon.remove(endpointId)
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID || isSelf(info.endpointName)) return
            if (state.peer(endpointId) != null || !pendingRequests.add(endpointId)) return
            state.log("همسایه پیدا شد: ${plainName(info.endpointName)}")
            client.requestConnection(myEndpointName(), endpointId, lifecycle)
                .addOnFailureListener { pendingRequests.remove(endpointId) }
        }

        override fun onEndpointLost(endpointId: String) {
            pendingRequests.remove(endpointId)
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (isSelf(info.endpointName)) { client.rejectConnection(endpointId); return }
            // Everything exchanged is signed, so we accept anyone running the app.
            state.upsertPeer(Peer(endpointId, plainName(info.endpointName)))
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingRequests.remove(endpointId)
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                peerHave[endpointId] = HashMap()
                state.log("متصل شد: ${state.peer(endpointId)?.name}")
                sendHello(endpointId)
                updateNotification()
            } else {
                state.removePeer(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            state.log("قطع شد: ${state.peer(endpointId)?.name}")
            state.removePeer(endpointId)
            peerHave.remove(endpointId); peerCommon.remove(endpointId)
            updateNotification()
        }
    }

    // ---------------- payloads ----------------

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { bytes -> scope.launch { handle(endpointId, bytes) } }
                Payload.Type.STREAM -> {
                    val input = payload.asStream()?.asInputStream() ?: return
                    scope.launch {
                        try {
                            val buf = ByteArrayOutputStream()
                            val tmp = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(tmp)
                                if (n < 0) break
                                buf.write(tmp, 0, n)
                                if (buf.size() > 4_000_000) { state.log("پیام خیلی بزرگ، رد شد"); return@launch }
                            }
                            handle(endpointId, buf.toByteArray())
                        } catch (e: Exception) {
                            state.log("خطای دریافت: ${e.message}")
                        } finally {
                            try { input.close() } catch (_: Exception) {}
                        }
                    }
                }
                else -> {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private suspend fun send(endpointId: String, msg: JSONObject) {
        val bytes = msg.toString().toByteArray()
        val payload = if (bytes.size < 30_000) Payload.fromBytes(bytes)
        else Payload.fromStream(ByteArrayInputStream(bytes))
        sendLock.withLock { client.sendPayload(endpointId, payload) }
    }

    private fun sendHello(endpointId: String) {
        scope.launch {
            val p = app.prefs
            val cs = JSONArray()
            p.communities.value.forEach { cs.put(JSONObject().put("id", it.id).put("name", it.name).put("apub", it.adminPub)) }
            val msg = JSONObject().put("m", "hello").put("pub", p.pub).put("name", p.displayName())
                .put("cs", cs).put("have", Wire.haveToJson(app.repo.have()))
            p.cert.value?.let { msg.put("cert", it.toJson()) }
            send(endpointId, msg)
        }
    }

    private suspend fun handle(endpointId: String, bytes: ByteArray) {
        val msg = try { JSONObject(String(bytes)) } catch (e: Exception) { return }
        when (msg.optString("m")) {
            "hello" -> onHello(endpointId, msg)
            "items" -> onItems(endpointId, msg)
            "cert" -> onCert(endpointId, msg)
        }
    }

    private suspend fun onHello(endpointId: String, msg: JSONObject) {
        val pub = msg.optString("pub")
        if (pub == app.prefs.pub) { drop(endpointId); return }   // loopback discovery of ourselves

        // Which communities do we share? Learn missing admin keys from the neighbour (unverified).
        val theirs = msg.optJSONArray("cs") ?: JSONArray()
        val common = HashSet<String>()
        for (i in 0 until theirs.length()) {
            val c = theirs.optJSONObject(i) ?: continue
            val id = c.optString("id"); val apub = c.optString("apub")
            val mine = app.prefs.community(id)
            if (mine == null) {
                // not followed yet: remember it so the user can join it offline from "محله‌های اطراف"
                if (id.isNotBlank() && apub.isNotBlank()) state.noteNearby(MeshState.NearbyCommunity(id, c.optString("name"), apub, state.peer(endpointId)?.name ?: ""))
                continue
            }
            state.forgetNearby(id)
            if (mine.adminPub.isBlank() && apub.isNotBlank()) {
                if (app.repo.adoptAdminKey(id, c.optString("name"), apub, verified = false)) {
                    state.log("کلید محله «$id» از همسایه دریافت شد")
                    state.notices.tryEmit("به محله «${app.prefs.community(id)?.name?.ifBlank { id } ?: id}» وصل شدید")
                }
            }
            val now = app.prefs.community(id)?.adminPub ?: ""
            if (apub.isBlank() || now.isBlank() || apub == now) common.add(id)
            else state.log("کلید محله «$id» همسایه با شما فرق دارد؛ این محله رد و بدل نمی‌شود")
        }
        if (common.isEmpty()) {
            state.log("همسایه محله مشترکی با شما ندارد")
            drop(endpointId)
            return
        }
        peerCommon[endpointId] = common

        val cert = WriterCert.fromJson(msg.optJSONObject("cert"))?.takeIf { it.pub == pub }
        val certCommunity = cert?.let { c -> app.prefs.adminPubs().entries.firstOrNull { c.isValidAt(System.currentTimeMillis(), it.value) }?.key }
        val name = (if (certCommunity != null) cert.name else msg.optString("name")).take(40)
        state.updatePeer(endpointId) { it.copy(name = name.ifBlank { it.name }, pub = pub, isWriter = certCommunity != null) }
        if (pub.isNotBlank()) {
            val existing = app.db.authors().byPub(pub)
            if (existing == null || (certCommunity != null && existing.cert.isBlank())) {
                app.db.authors().upsert(Author(pub, name, if (certCommunity != null) cert!!.toJson().toString() else "", certCommunity ?: "", System.currentTimeMillis()))
            }
        }
        val theirHave = Wire.haveFromJson(msg.optJSONObject("have")).toMutableMap()
        peerHave[endpointId] = theirHave
        // stream the difference in batches until nothing is left
        var total = 0
        while (true) {
            val batch = app.repo.diffFor(theirHave, common)
            if (batch.isEmpty()) break
            send(endpointId, JSONObject().put("m", "items").put("items", Wire.arr(batch)))
            for (b in batch) {
                val it = b.getJSONObject("item")
                val a = it.getString("a"); val s = it.getLong("s")
                theirHave[a] = maxOf(theirHave[a] ?: 0L, s)
            }
            total += batch.size
            if (total > 2000) break
        }
        // mark everything of theirs we skipped as "known" so we don't re-scan it each round
        for (mine in app.repo.have()) theirHave[mine.author] = maxOf(theirHave[mine.author] ?: 0L, mine.maxSeq)
        if (total > 0) {
            state.log("$total مورد برای ${state.peer(endpointId)?.name} فرستاده شد")
            state.updatePeer(endpointId) { it.copy(sent = it.sent + total) }
        }
    }

    private suspend fun onItems(endpointId: String, msg: JSONObject) {
        val arr = msg.optJSONArray("items") ?: return
        var accepted = 0
        val have = peerHave[endpointId]
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            val it = b.optJSONObject("item")
            if (it != null && have != null) {
                val a = it.optString("a"); val s = it.optLong("s")
                have[a] = maxOf(have[a] ?: 0L, s)
            }
            if (app.repo.acceptRemote(b, endpointId)) accepted++
        }
        if (accepted > 0) {
            state.log("$accepted مورد تازه از ${state.peer(endpointId)?.name}")
            state.updatePeer(endpointId) { it.copy(received = it.received + accepted) }
            state.notices.tryEmit("$accepted مطلب/دیدگاه تازه از همسایه‌ها رسید")
        }
    }

    private suspend fun onCert(endpointId: String, msg: JSONObject) {
        val cert = WriterCert.fromJson(msg.optJSONObject("cert")) ?: return
        if (app.repo.applyOwnCert(cert)) {
            state.log("دسترسی نویسنده از ${state.peer(endpointId)?.name} دریافت شد")
            state.notices.tryEmit("شما نویسنده شدید: ${cert.name}")
            state.peers.value.keys.forEach { sendHello(it) }
        }
    }

    private suspend fun grantWriter(endpointId: String) {
        val peer = state.peer(endpointId) ?: return
        if (peer.pub.isBlank()) { state.notices.tryEmit("هنوز کلید این همسایه نرسیده، چند ثانیه بعد دوباره بزنید"); return }
        val seed = app.prefs.adminSeed.value
        val community = app.repo.adminCommunity(seed)
        if (community == null) { state.notices.tryEmit("کلید مدیر با هیچ‌یک از محله‌های شما نمی‌خواند"); return }
        if (community.id !in (peerCommon[endpointId] ?: emptySet())) { state.notices.tryEmit("این همسایه محله «${community.id}» را دنبال نمی‌کند"); return }
        val cert = app.repo.adminIssueCert(seed, peer.pub, peer.name) ?: run { state.notices.tryEmit("خطا در صدور مجوز"); return }
        app.db.authors().upsert(Author(peer.pub, cert.name, cert.toJson().toString(), community.id, System.currentTimeMillis()))
        send(endpointId, JSONObject().put("m", "cert").put("cert", cert.toJson()))
        state.updatePeer(endpointId) { it.copy(isWriter = true) }
        state.log("دسترسی نویسنده به ${peer.name} داده شد")
        state.notices.tryEmit("${peer.name} نویسنده شد")
    }

    // ---------------- notification ----------------

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = state.peers.value.size
        val text = if (n == 0) "در جست‌وجوی همسایه‌ها…" else "$n همسایه متصل"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    companion object {
        const val SERVICE_ID = "co.yooz.hamresan.mesh.v1"
        private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
        private const val CHANNEL = "mesh"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "co.yooz.hamresan.STOP"

        fun start(ctx: Context) {
            val i = Intent(ctx, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
