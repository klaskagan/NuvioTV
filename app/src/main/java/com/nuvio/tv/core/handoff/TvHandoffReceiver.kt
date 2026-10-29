package com.nuvio.tv.core.handoff

import android.app.AlertDialog
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.math.abs

/** Receiver for explicitly approved local desktop transfers. No stream URL is accepted. */
object TvHandoffReceiver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var active: Incoming? = null

    data class Incoming(val launch: JSONObject, val request: JSONObject) {
        val id: String get() = request.getString("requestId")
        val contentId: String get() = request.getString("contentId")
        val videoId: String get() = request.getString("videoId")
        val contentType: String get() = request.getString("contentType")
        val title: String get() = request.getString("title")
        val position: Long get() = request.getLong("positionMs")
        val profileId: Int get() = request.getInt("profileId")
        val expires: Long get() = request.getLong("expiresAtEpochMs")
        @Volatile var phase = "RECEIVED"
        @Volatile var valid = true
        @Volatile var playerSession: Any? = null
        var stableSince = 0L
        var stablePosition = 0L

        fun exchange(status: JSONObject? = null): JSONObject {
            val endpoint = launch.getString("endpoint")
            val url = URL(endpoint + if (status == null) "" else "/status")
            val address = InetAddress.getByName(url.host)
            require(url.protocol == "https" && url.host.matches(Regex("[0-9.]+")))
            require((address.isSiteLocalAddress || address.isLinkLocalAddress) && !address.isLoopbackAddress)
            require(url.port in 1..65535 && url.path in setOf("/v1/handoff", "/v1/handoff/status"))
            require(url.userInfo == null && url.query == null && url.ref == null)
            val pin = launch.getString("certificatePin")
            require(pin.matches(Regex("[0-9a-f]{64}")))
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    require(chain.isNotEmpty())
                    val actual = MessageDigest.getInstance("SHA-256").digest(chain.first().encoded)
                        .joinToString("") { "%02x".format(it) }
                    require(actual == pin) { "Desktop certificate mismatch" }
                }
            }
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            val connection = url.openConnection() as HttpsURLConnection
            try {
                connection.sslSocketFactory = context.socketFactory
                connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                connection.connectTimeout = 5_000; connection.readTimeout = 5_000
                connection.setRequestProperty("Authorization", "Bearer ${launch.getString("token")}")
                if (status != null) {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(status.toString().toByteArray()) }
                }
                check(connection.responseCode == 200) { "Desktop cancelled or expired the transfer" }
                val bytes = connection.inputStream.use { it.readBytesBounded(16_384) }
                return JSONObject(String(bytes, Charsets.UTF_8))
            } finally { connection.disconnect() }
        }

        fun status(phase: String, position: Long? = null, failure: String? = null) {
            exchange(JSONObject().put("requestId", id).put("targetDeviceId", request.getString("targetDeviceId"))
                .put("phase", phase).apply {
                    if (position != null) put("positionMs", position)
                    if (failure != null) put("failureCode", failure)
                })
            this.phase = phase
        }
    }

    suspend fun accept(context: Context, parameters: String, accountId: String?, profileId: Int, currentlyPlaying: Boolean = false): Incoming? {
        var received: Incoming? = null
        return try {
            require(parameters.length <= 4096)
            val launch = JSONObject(parameters)
            require(launch.getString("token").matches(Regex("[0-9a-f]{64}")))
            val incoming = Incoming(launch, JSONObject())
            val request = withContext(Dispatchers.IO) { incoming.exchange() }
            val now = System.currentTimeMillis()
            require(request.getInt("version") == 1 && request.getString("requestId") == launch.getString("requestId"))
            require(request.getString("targetDeviceId") == launch.getString("targetDeviceId"))
            require(request.getString("senderDeviceId") == launch.getString("senderDeviceId"))
            require(request.getLong("createdAtEpochMs") <= now + 5_000 && request.getLong("expiresAtEpochMs") > now)
            require(request.getLong("expiresAtEpochMs") - request.getLong("createdAtEpochMs") in 1..120_000)
            require(request.getLong("positionMs") in 0 until request.getLong("durationMs"))
            require(request.getString("contentType") in setOf("movie", "series"))
            require(request.getInt("profileId") in 1..6)
            require(listOf("requestId", "senderDeviceId", "targetDeviceId", "contentId", "videoId", "title").all {
                request.getString(it).isNotBlank() && request.getString(it).length <= 512
            })
            if (request.getString("contentType") == "series") {
                require(request.has("season") && request.has("episode") && request.getInt("season") >= 0 && request.getInt("episode") >= 1)
            }
            val result = Incoming(launch, request)
            received = result
            withContext(Dispatchers.IO) { result.status("RECEIVED"); result.status("AWAITING_USER") }
            require(request.getString("accountId") == accountId) { "Sign in to the same Nuvio account on both devices." }
            require(request.getInt("profileId") == profileId) { "Select the matching profile on TV, then retry from desktop." }
            val preferences = context.getSharedPreferences("tv-handoff", Context.MODE_PRIVATE)
            val sender = launch.getString("senderDeviceId")
            require(sender.length <= 512 && request.getString("requestId").length <= 512)
            val seen = JSONObject(preferences.getString("seen-requests", "{}") ?: "{}")
            seen.keys().asSequence().toList().forEach { id -> if (seen.optLong(id) <= now) seen.remove(id) }
            require(!seen.has(result.id) && seen.length() < 128) { "Transfer already handled." }
            val pin = launch.getString("certificatePin")
            // New desktops are explicitly approved. Later transfers also confirm before replacing playback.
            val trusted = preferences.getString("desktop-$sender", null) == pin
            val approved = if (trusted && !currentlyPlaying) true else confirmWhileConnected(result, context,
                if (trusted) "Continue watching on this TV?" else "Pair Nuvio Desktop?",
                if (trusted) result.title else "Allow this desktop to send movies to this TV?\nDesktop code: ${pin.take(6).uppercase()}\n${result.title}")
            if (!approved) {
                withContext(Dispatchers.IO) { result.status("FAILED", failure = "USER_DECLINED") }
                return null
            }
            // Do not let a long approval dialog revive a cancelled transfer.
            withContext(Dispatchers.IO) { result.exchange(); result.status("RESOLVING") }
            seen.put(result.id, result.expires)
            withContext(Dispatchers.IO) {
                check(preferences.edit().putString("desktop-$sender", pin).putString("seen-requests", seen.toString()).commit())
            }
            active?.valid = false
            active = result
            scope.launch {
                while (active === result && result.valid && result.phase != "PLAYING") {
                    delay(1_000)
                    try {
                        if (System.currentTimeMillis() >= result.expires) error("expired")
                        result.exchange()
                    } catch (_: Exception) { result.valid = false }
                }
            }
            result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            withContext(Dispatchers.IO) { runCatching { received?.status("FAILED", failure = "ACCOUNT_PROFILE_OR_REQUEST_INVALID") } }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Cannot receive desktop playback. Check account, profile and transfer expiry.", android.widget.Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    fun resumePosition(contentId: String?, videoId: String?, profileId: Int): Long? = active?.takeIf {
        it.valid && it.phase != "PLAYING" && System.currentTimeMillis() < it.expires &&
            it.contentId == contentId && it.videoId == videoId && it.profileId == profileId
    }?.let { incoming ->
        if (incoming.phase != "LOADING") scope.launch { runCatching { incoming.status("LOADING") } }
        incoming.position
    }

    fun overlayProgress(
        saved: com.nuvio.tv.domain.model.WatchProgress?, contentId: String?, videoId: String?, profileId: Int,
    ): com.nuvio.tv.domain.model.WatchProgress? {
        val incoming = active?.takeIf { it.valid && it.phase != "PLAYING" && it.contentId == contentId &&
            it.videoId == videoId && it.profileId == profileId && System.currentTimeMillis() < it.expires } ?: return saved
        return com.nuvio.tv.domain.model.WatchProgress(
            contentId = incoming.contentId, contentType = incoming.contentType, name = incoming.title,
            poster = saved?.poster, backdrop = saved?.backdrop, logo = saved?.logo, videoId = incoming.videoId,
            season = saved?.season, episode = saved?.episode, episodeTitle = saved?.episodeTitle,
            position = incoming.position, duration = incoming.request.getLong("durationMs"),
            lastWatched = incoming.request.getLong("createdAtEpochMs"),
        )
    }

    fun beginPlayback(contentId: String?, videoId: String?, profileId: Int, session: Any) {
        active?.takeIf { it.valid && it.phase != "PLAYING" && it.contentId == contentId &&
            it.videoId == videoId && it.profileId == profileId }?.apply {
            playerSession = session
            stableSince = 0L
        }
    }

    fun playback(contentId: String?, videoId: String?, profileId: Int, position: Long, isPlaying: Boolean, session: Any) {
        val incoming = active ?: return
        if (incoming.playerSession !== session || incoming.contentId != contentId ||
            incoming.videoId != videoId || incoming.profileId != profileId) return
        if (!incoming.valid || System.currentTimeMillis() >= incoming.expires) return
        if (!isPlaying || incoming.phase != "LOADING" || abs(position - incoming.position) > 2_000) {
            incoming.stableSince = 0L
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (incoming.stableSince == 0L) {
            incoming.stableSince = now
            incoming.stablePosition = position
        } else if (now - incoming.stableSince >= 750 && position >= incoming.stablePosition + 100) {
            incoming.phase = "CONFIRMING"
            scope.launch { runCatching { incoming.status("PLAYING", position) }.onFailure { incoming.valid = false } }
        }
    }

    fun shouldStopCancelledPlayback(contentId: String?, videoId: String?, profileId: Int, session: Any): Boolean {
        val incoming = active ?: return false
        if (incoming.playerSession !== session) return false
        val cancelled = incoming.contentId == contentId && incoming.videoId == videoId && incoming.profileId == profileId &&
            incoming.phase != "PLAYING" && (!incoming.valid || System.currentTimeMillis() >= incoming.expires)
        if (cancelled && active === incoming) active = null
        return cancelled
    }

    fun isPendingVideo(videoId: String): Boolean = active?.let {
        it.valid && it.phase != "PLAYING" && it.videoId == videoId && System.currentTimeMillis() < it.expires
    } == true

    fun preferEdition(streams: List<com.nuvio.tv.domain.model.Stream>): List<com.nuvio.tv.domain.model.Stream> {
        val incoming = active?.takeIf { it.valid && it.phase != "PLAYING" } ?: return streams
        val hint = incoming.request.optString("editionHint").takeUnless { it.isBlank() || it == "null" } ?: return streams
        val matching = streams.filter { stream ->
            val text = "${stream.name.orEmpty()} ${stream.title.orEmpty()} ${stream.description.orEmpty()}".lowercase()
                .replace(Regex("[._-]+"), " ")
            if (hint == "director's cut") Regex("\\bdirector(?:s|'s)?\\s+cut\\b").containsMatchIn(text)
            else Regex("\\b${Regex.escape(hint)}\\b").containsMatchIn(text)
        }
        return matching.ifEmpty { streams }
    }

    private suspend fun confirmWhileConnected(incoming: Incoming, context: Context, title: String, message: String): Boolean {
        return withTimeoutOrNull((incoming.expires - System.currentTimeMillis()).coerceIn(1L, 90_000L)) {
            coroutineScope {
                val approval = async { confirm(context, title, message) }
                val monitor = launch(Dispatchers.IO) {
                    while (approval.isActive) {
                        delay(1_000)
                        try { incoming.exchange() } catch (_: Exception) { approval.cancel(); break }
                    }
                }
                try { approval.await() }
                catch (cancelled: kotlinx.coroutines.CancellationException) {
                    if (!isActive) throw cancelled
                    false
                } finally { monitor.cancel() }
            }
        } ?: false
    }

    private suspend fun confirm(context: Context, title: String, message: String): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val dialog = AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton("Continue") { _, _ -> if (continuation.isActive) continuation.resume(true) }
                .setNegativeButton("Cancel") { _, _ -> if (continuation.isActive) continuation.resume(false) }
                .setOnCancelListener { if (continuation.isActive) continuation.resume(false) }.create()
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
