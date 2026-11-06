package com.nuvio.tv.go3

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Unofficial LT prototype. Dedicated client: no shared account/cookie jar or HTTP logging. */
internal class Go3ExperimentalApi(
    private val deviceId: String,
    private val deviceInfo: String,
    private val base: HttpUrl = "https://go3.lt/api/".toHttpUrl(),
    private val client: OkHttpClient = Go3Transport.restrictedClient().newBuilder().cookieJar(Go3MemoryCookies()).build(),
) {
    private fun builder(url: HttpUrl, session: Go3Session?, profile: String?): Request.Builder = Request.Builder()
        .url(url).header("Accept", "application/json").header("Accept-Language", "lt-LT,lt;q=0.9")
        .header("User-Agent", "NuvioTV/1.0 (experimental Android TV client)")
        .header("API-AppVersion", "1.38-(581)")
        .header("API-DeviceUid", deviceId).header("API-DeviceInfo", deviceInfo)
        .header("API-CorrelationId", "client_${UUID.randomUUID()}")
        .apply {
            session?.let { header("API-Authentication", it.token) }
            profile?.let { header("API-ProfileUid", it) }
        }

    private fun request(path: String, method: String = "GET", session: Go3Session? = null,
        profile: String? = null, params: List<Pair<String, String>> = emptyList(), body: String? = null,
        tenant: Boolean = true): Request {
        val url = requireNotNull(base.resolve(path)).newBuilder()
            .addQueryParameter("platform", "ANDROID_TV").addQueryParameter("lang", "LT")
            .apply { if (tenant) addQueryParameter("tenant", "OM_LT")
                params.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        val payload = body?.toRequestBody("application/json".toMediaType())
            ?: if (method in setOf("POST", "PUT")) ByteArray(0).toRequestBody(null) else null
        return builder(url, session, profile).method(method, payload).build()
    }

    private suspend fun exchange(request: Request): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(0 to "")
            }
            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        val bytes = it.body.byteStream().use { stream ->
                            val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                            while(out.size()<=2_000_000) {
                                val n=stream.read(buffer,0,minOf(buffer.size,2_000_001-out.size()))
                                if(n<0) break
                                out.write(buffer,0,n)
                            }
                            out.toByteArray()
                        }
                        if (bytes.size > 2_000_000) -1 to "" else it.code to bytes.toString(Charsets.UTF_8)
                    }
                } catch (_: Exception) { 0 to "" }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }
    private suspend fun get(request: Request): JsonElement {
        val (code, body) = exchange(request)
        if (code !in 200..299) throw Go3Protocol.failure(code, body)
        return if (body.isBlank()) JsonObject(emptyMap()) else Go3Protocol.json(body)
    }
    suspend fun loginCode(): Go3Login {
        val o = get(request("subscribers/devices/otc", "POST", tenant = false)).jsonObject
        val code = Go3Protocol.text(o,"code")
        if (!code.matches(Regex("[A-Za-z0-9]{4,12}"))) throw Go3Failure("Go3 did not issue a device code. Retry.")
        val expiry = runCatching { Instant.parse(Go3Protocol.text(o,"till")) }.getOrDefault(Instant.now().plusSeconds(600))
        return Go3Login(code, expiry)
    }
    suspend fun poll(code: String): Go3Session? {
        val body = buildJsonObject { put("code", code); put("rememberMe", true) }.toString()
        val (status, raw) = exchange(request("subscribers/login", "POST", body = body, tenant = false))
        if(raw.trimStart().startsWith("<")) throw Go3Failure("Go3 blocked device linking. Retry using the official account flow.")
        if ("OTC_EXPIRED" in raw) throw Go3Failure("Go3 linking code expired. Request a new code.")
        if(listOf("DEVICE_LIMIT","DEVICE_NOT_EXISTS","GEOIP","INVALID_REGION").any { it in raw.uppercase() })
            throw Go3Protocol.failure(status,raw)
        if (status in setOf(403,404) || "OTC_NOT_VERIFIED" in raw) return null
        if (status !in 200..299) throw Go3Protocol.failure(status, raw)
        val o = Go3Protocol.json(raw).jsonObject
        val token = Go3Protocol.text(o,"token")
        if (token.isBlank()) throw Go3Failure("Go3 sign-in did not complete. Retry.")
        return Go3Session(token, Go3Protocol.text(o,"tier").ifBlank { "TIER_2" })
    }
    suspend fun profiles(session: Go3Session) = Go3Protocol.profiles(get(request("subscribers/detail", session=session)))
    suspend fun channels(session: Go3Session, profile: String) = Go3Protocol.channels(get(request(
        "products/sections/v2/live_tv", session=session, profile=profile,
        params=listOf("tier" to session.tier, "recommendations" to "false"))))

    /** Bounded feasibility browser, not the final competition catalogue. Past two days only. */
    suspend fun replays(session: Go3Session, profile: String, channel: String): List<Go3Replay> {
        val now = Instant.now(); val from = now.minus(2, ChronoUnit.DAYS)
        val legacy = request("products/lives/programmes", session=session, profile=profile,
            params=listOf("liveId[]" to channel, "since" to from.toString(), "till" to now.toString()))
        val (status, raw) = exchange(legacy)
        if (status in 200..299) return Go3Protocol.replays(Go3Protocol.json(raw), channel, from, now)
        if (status != 404) throw Go3Protocol.failure(status, raw)
        suspend fun page(offset: Int, size: Int, order: String): JsonObject = get(request(
            "products/lives/programmes/catalog", session=session, profile=profile,
            params=listOf("firstResult" to "$offset", "maxResults" to "$size", "sort" to "since", "order" to order, "tier" to session.tier))).jsonObject
        val latest = page(0,1,"DESC")
        val lastStart = (Go3Protocol.array(latest,"items").firstOrNull() as? JsonObject)?.let {
            runCatching { Instant.parse(Go3Protocol.text(it,"since")) }.getOrNull() } ?: return emptyList()
        val count = Go3Protocol.text(Go3Protocol.objectValue(latest,"meta"),"totalCount").toIntOrNull() ?: return emptyList()
        var offset = (count - ((ChronoUnit.DAYS.between(from,lastStart).coerceAtLeast(0)+2)*1600)).coerceIn(0,Int.MAX_VALUE.toLong()).toInt()
        val found = linkedMapOf<String,Go3Replay>()
        repeat(40) {
            val root = page(offset,200,"ASC")
            val rows = Go3Protocol.array(root,"items")
            if (rows.isEmpty()) return found.values.toList()
            val first = (rows.firstOrNull() as? JsonObject)?.let { runCatching { Instant.parse(Go3Protocol.text(it,"since")) }.getOrNull() }
            if (found.isEmpty() && offset > 0 && first?.isAfter(from) == true) {
                offset = (offset-200).coerceAtLeast(0)
            } else {
                Go3Protocol.replays(root,channel,from,now).forEach { found[it.scheduleId] = it }
                val last = (rows.lastOrNull() as? JsonObject)?.let { runCatching { Instant.parse(Go3Protocol.text(it,"since")) }.getOrNull() }
                if (last?.isAfter(now) == true) return found.values.toList()
                offset += rows.size
            }
        }
        return found.values.toList()
    }

    suspend fun programme(session: Go3Session,profile: String,id: String): Go3Programme {
        require(id.matches(Regex("[0-9]+")))
        val root=get(request("products/lives/programmes/$id",session=session,profile=profile)) as? JsonObject
            ?: throw Go3Failure("Go3 programme is unavailable.")
        val programme=Go3CatalogueDecoder.programme(root,Instant.now()) ?: throw Go3Failure("Go3 programme is unavailable.")
        // The official frontend overlays subscriber context onto public programme metadata.
        // Authentication on the public endpoint alone still returns anonymous capability flags.
        val context=get(request("subscribers/products/lives/programmes/$id",session=session,profile=profile)) as? JsonObject
            ?: throw Go3Failure("Go3 programme capabilities are unavailable.")
        val channel=get(request("products/lives/${programme.channelId}",session=session,profile=profile)) as? JsonObject
            ?: throw Go3Failure("Go3 channel capabilities are unavailable.")
        fun flag(value: JsonObject,key: String)=(value[key] as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull==true }==true
        // Official live-event start-over also uses the channel's startOver capability.
        // Programme context alone can be false while this exact event channel enables it.
        return programme.copy(startOverAvailable=flag(context,"startoverAvailable") || flag(channel,"startOver"))
    }

    suspend fun ticket(session: Go3Session, profile: String, id: String, live: Boolean): Go3Ticket {
        require(id.matches(Regex("[0-9]+")))
        val o = get(request("products/$id/videos/player/configuration", session=session, profile=profile,
            params=listOf("videoType" to if(live) "LIVE" else "CATCHUP", "dai" to "true", "firstInSession" to "true"))).jsonObject
        val videoSession = Go3Protocol.objectValue(o,"videoSession")
        val serverSessionId=Go3Protocol.text(videoSession,"videoSessionId").ifBlank { null }
        try {
        val playlistUrl = base.resolve(Go3Protocol.text(o,"playlistUrl")) ?: throw Go3Failure("Go3 playback is unavailable.")
        // Never forward account headers to a CDN, redirect, or unrelated playlist host.
        if (playlistUrl.scheme != base.scheme || playlistUrl.host != base.host || playlistUrl.port != base.port)
            throw Go3Failure("Go3 returned an unrecognized playlist host.")
        val list = get(builder(playlistUrl.newBuilder().setQueryParameter("platform","ANDROID_TV")
            .setQueryParameter("lang","LT").setQueryParameter("tenant","OM_LT").build(), session,profile).get().build()).jsonObject
        val sources = Go3Protocol.objectValue(list,"sources")
        fun source(name: String) = (Go3Protocol.array(sources,name).firstOrNull() as? JsonObject)?.let { Go3Protocol.text(it,"src") }.orEmpty()
        fun absolute(value: String) = if(value.isBlank()) "" else base.resolve(value)?.toString().orEmpty()
        val manifest = absolute(source("DASH").ifBlank { source("DASH_HEVC") })
        val license = absolute(Go3Protocol.text(Go3Protocol.objectValue(Go3Protocol.objectValue(list,"drm"),"WIDEVINE"),"src"))
        if (!safeHttps(manifest) || !officialLicense(license)) throw Go3Failure("Go3 did not provide a usable Widevine playback route.")
        val headers = buildMap {
            put("API-Authentication", session.token); put("API-ProfileUid",profile)
            put("API-DeviceUid",deviceId); put("API-DeviceInfo",deviceInfo)
            put("API-AppVersion","1.38-(581)"); put("Content-Type","application/octet-stream")
            client.cookieJar.loadForRequest(license.toHttpUrl()).joinToString("; ") { "${it.name}=${it.value}" }
                .takeIf { it.isNotBlank() }?.let { put("Cookie",it) }
        }
        return Go3Ticket(manifest,license,serverSessionId,
            Go3Protocol.text(videoSession,"prolongInterval").toLongOrNull()?.coerceIn(5,3600) ?: 60,headers,live=live)
        } catch(error: Exception) {
            if(serverSessionId!=null) withContext(NonCancellable) {
                withTimeoutOrNull(5000) { runCatching { close(session,serverSessionId) } }
            }
            throw error
        }
    }
    suspend fun renew(session: Go3Session, id: String) { get(request("products/videosessions/${pathId(id)}","PUT",session)) }
    suspend fun close(session: Go3Session, id: String) { get(request("products/videosessions/${pathId(id)}","DELETE",session)) }
    fun forgetSessionCookies() { (client.cookieJar as? Go3MemoryCookies)?.clear() }
    private fun pathId(id: String): String {
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) throw Go3Failure("Go3 playback session is unavailable.")
        return id
    }
    companion object {
        fun safeHttps(url: String): Boolean = runCatching { val u=url.toHttpUrl(); u.isHttps && u.username.isEmpty() && u.password.isEmpty() }.getOrDefault(false)
        fun officialLicense(url: String): Boolean = safeHttps(url) && runCatching {
            val host=url.toHttpUrl().host; host=="go3.lt" || host=="go3.tv" || host.endsWith(".go3.tv")
        }.getOrDefault(false)
    }
}

/** Session cookies stay in RAM and are domain/path scoped by OkHttp's cookie matcher. */
private class Go3MemoryCookies : CookieJar {
    private val lock=Any()
    private val values=mutableListOf<Cookie>()
    fun clear() = synchronized(lock) { values.clear() }
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        cookies.forEach { next ->
            values.removeAll { it.name==next.name && it.domain==next.domain && it.path==next.path }
            values.add(next)
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        values.removeAll { it.expiresAt<=System.currentTimeMillis() }
        values.filter { it.matches(url) }
    }
}
