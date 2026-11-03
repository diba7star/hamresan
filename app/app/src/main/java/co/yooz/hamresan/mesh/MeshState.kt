package co.yooz.hamresan.mesh

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Peer(
    val endpointId: String,
    val name: String,
    val pub: String = "",
    val isWriter: Boolean = false,
    val connectedAt: Long = System.currentTimeMillis(),
    val received: Int = 0,
    val sent: Int = 0
)

/** Shared between the service and the UI (process-local singleton held by App). */
class MeshState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers

    /** Communities neighbours follow that we don't (id -> name, admin key, who had it). Lets users join fully offline. */
    data class NearbyCommunity(val id: String, val name: String, val adminPub: String, val peerName: String)
    private val _nearby = MutableStateFlow<Map<String, NearbyCommunity>>(emptyMap())
    val nearby: StateFlow<Map<String, NearbyCommunity>> = _nearby
    fun noteNearby(c: NearbyCommunity) { _nearby.value = _nearby.value + (c.id to c) }
    fun forgetNearby(id: String) { _nearby.value = _nearby.value - id }

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    /** One-shot UI notices, e.g. "you were granted writer access". */
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Commands from UI to the service. */
    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)

    sealed class Command {
        data class GrantWriter(val endpointId: String) : Command()
        object ResyncAll : Command()
    }

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun setRunning(v: Boolean) { _running.value = v; if (!v) _peers.value = emptyMap() }

    fun upsertPeer(p: Peer) { _peers.value = _peers.value + (p.endpointId to p) }
    fun updatePeer(id: String, f: (Peer) -> Peer) {
        val cur = _peers.value[id] ?: return
        _peers.value = _peers.value + (id to f(cur))
    }
    fun removePeer(id: String) { _peers.value = _peers.value - id }
    fun peer(id: String): Peer? = _peers.value[id]

    fun log(msg: String) {
        val line = "${fmt.format(Date())}  $msg"
        _log.value = (listOf(line) + _log.value).take(60)
    }
}
