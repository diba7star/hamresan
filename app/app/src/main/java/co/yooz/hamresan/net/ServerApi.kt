package co.yooz.hamresan.net

import co.yooz.hamresan.BuildConfig
import co.yooz.hamresan.data.Prefs
import co.yooz.hamresan.data.Repo
import co.yooz.hamresan.data.Wire
import co.yooz.hamresan.data.WriterCert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Network side, used only when the device happens to have internet.
 *  - directory: resolves a community ID to its admin public key (central, no server addresses stored)
 *  - login:     username/password on a community server -> admin-signed writer certificate
 *  - sync:      exchange have-vectors and missing items with the community server (the "courier" role)
 */
class ServerApi(private val prefs: Prefs, private val repo: Repo) {

    data class SyncResult(val received: Int, val sent: Int)
    data class DirEntry(val id: String, val name: String, val pub: String)

    class NoServer : Exception("server not set")
    class WrongCommunity(val id: String) : Exception("server belongs to another community")

    // ---------------- directory ----------------

    /** Look a community up in the central directory. failure = could not reach; success(null) = not registered. */
    suspend fun directoryLookup(id: String): Result<DirEntry?> = withContext(Dispatchers.IO) {
        try {
            val res = get(BuildConfig.DIRECTORY_URL + "?id=" + URLEncoder.encode(id.trim().lowercase(), "UTF-8"))
            if (res.optBoolean("ok")) Result.success(DirEntry(res.getString("id"), res.optString("name"), res.getString("pub")))
            else if (res.optString("error") == "not_found") Result.success(null)
            else Result.failure(Exception(res.optString("error", "directory error")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Public catalogue of all registered communities (optionally filtered). failure = offline. */
    suspend fun directoryList(query: String = ""): Result<List<DirEntry>> = withContext(Dispatchers.IO) {
        try {
            val url = BuildConfig.DIRECTORY_URL + "?list=1" + (if (query.isBlank()) "" else "&q=" + URLEncoder.encode(query.trim(), "UTF-8"))
            val res = get(url)
            if (!res.optBoolean("ok")) return@withContext Result.failure(Exception(res.optString("error", "directory error")))
            val arr = res.optJSONArray("items") ?: return@withContext Result.success(emptyList())
            Result.success((0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                DirEntry(o.optString("id"), o.optString("name"), o.optString("pub"))
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Register a locally-created community in the directory (needs internet + the admin seed on this phone). */
    suspend fun registerLocalCommunity(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val c = prefs.community(id) ?: return@withContext Result.failure(Exception("unknown community"))
            val ts = System.currentTimeMillis()
            val sig = repo.registrationSignature(c.id, c.adminPub, c.name, ts) ?: return@withContext Result.failure(Exception("کلید مدیر این محله روی این گوشی نیست"))
            val res = postTo(BuildConfig.DIRECTORY_URL, JSONObject().put("action", "register").put("id", c.id).put("pub", c.adminPub)
                .put("name", c.name).put("ts", ts).put("sig", sig))
            if (!res.optBoolean("ok")) return@withContext Result.failure(Exception(res.optString("error", "register failed")))
            prefs.upsertCommunity(c.copy(verified = true, local = false))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Confirm (or learn) the admin keys of all followed communities from the directory.
     * No-op without connectivity. Returns the number of communities now verified.
     */
    suspend fun verifyCommunities(): Int {
        var n = 0
        for (c in prefs.communities.value) {
            if (c.verified) { n++; continue }
            val entry = directoryLookup(c.id).getOrNull() ?: continue
            repo.adoptAdminKey(c.id, entry.name, entry.pub, verified = true)
            if (prefs.community(c.id)?.verified == true) n++
        }
        return n
    }

    // ---------------- community server ----------------

    suspend fun login(username: String, password: String): Result<WriterCert> = withContext(Dispatchers.IO) {
        try {
            if (prefs.serverUrl.isBlank()) return@withContext Result.failure(NoServer())
            val res = post(JSONObject().put("action", "login").put("username", username.trim())
                .put("password", password).put("pub", prefs.pub))
            if (!res.optBoolean("ok")) return@withContext Result.failure(Exception(res.optString("error", "login failed")))
            adoptFromServer(res.optJSONObject("community"))?.let { return@withContext Result.failure(it) }
            val cert = WriterCert.fromJson(res.optJSONObject("cert")) ?: return@withContext Result.failure(Exception("bad cert"))
            if (!repo.applyOwnCert(cert)) return@withContext Result.failure(Exception("cert not valid for this device"))
            Result.success(cert)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sync(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            if (prefs.serverUrl.isBlank()) return@withContext Result.failure(NoServer())
            var received = 0
            var serverHave: Map<String, Long> = emptyMap()
            var serverCommunity = ""
            var rounds = 0
            while (rounds++ < 40) {
                val res = post(JSONObject().put("action", "sync").put("have", Wire.haveToJson(repo.have())))
                if (!res.optBoolean("ok")) return@withContext Result.failure(Exception(res.optString("error", "sync failed")))
                if (rounds == 1) {
                    adoptFromServer(res.optJSONObject("community"))?.let { return@withContext Result.failure(it) }
                    serverCommunity = res.optJSONObject("community")?.optString("id") ?: ""
                }
                serverHave = Wire.haveFromJson(res.optJSONObject("have"))
                val arr = res.optJSONArray("items") ?: break
                if (arr.length() == 0) break
                for (i in 0 until arr.length()) {
                    val b = arr.optJSONObject(i) ?: continue
                    if (repo.acceptRemote(b, "server")) received++
                }
            }
            // push what the server lacks (only its own community's content)
            var sent = 0
            val theirHave = serverHave.toMutableMap()
            rounds = 0
            while (rounds++ < 40) {
                val batch = repo.diffFor(theirHave, setOf(serverCommunity), maxItems = 30, maxBytes = 2_000_000)
                if (batch.isEmpty()) break
                val res = post(JSONObject().put("action", "push").put("items", Wire.arr(batch)))
                if (!res.optBoolean("ok")) return@withContext Result.failure(Exception(res.optString("error", "push failed")))
                for (b in batch) {
                    val it = b.getJSONObject("item")
                    theirHave[it.getString("a")] = maxOf(theirHave[it.getString("a")] ?: 0L, it.getLong("s"))
                }
                sent += res.optInt("accepted", batch.size)
            }
            prefs.lastServerSync = System.currentTimeMillis()
            Result.success(SyncResult(received, sent))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch the identity of a server before locking its address in. */
    suspend fun probeServer(url: String): Result<DirEntry> = withContext(Dispatchers.IO) {
        try {
            val res = get(url.trim())
            val c = res.optJSONObject("community") ?: return@withContext Result.failure(Exception("not a hamresan server"))
            Result.success(DirEntry(c.optString("id"), c.optString("name"), c.optString("pub")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** The server says who it is; it must be a community we follow. Its key is adopted only if we have none yet. */
    private suspend fun adoptFromServer(c: JSONObject?): Exception? {
        if (c == null) return Exception("bad server reply")
        val id = c.optString("id"); val pub = c.optString("pub"); val name = c.optString("name")
        val mine = prefs.community(id) ?: return WrongCommunity(id)
        if (mine.adminPub.isNotBlank() && mine.adminPub != pub) return WrongCommunity(id)
        if (mine.adminPub.isBlank()) repo.adoptAdminKey(id, name, pub, verified = false)
        return null
    }

    // ---------------- http ----------------

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("Accept", "application/json")
        return readJson(c)
    }

    private fun post(body: JSONObject): JSONObject = postTo(prefs.serverUrl, body)

    private fun postTo(url: String, body: JSONObject): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("Accept", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        return readJson(c)
    }

    private fun readJson(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return try { JSONObject(text) } catch (e: Exception) { JSONObject().put("ok", false).put("error", "HTTP $code") }
    }
}
