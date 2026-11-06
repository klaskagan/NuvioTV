package com.nuvio.tv.go3

import kotlinx.serialization.json.*
import java.time.Instant

/** Experimental protocol boundary. Raw Go3 responses never reach the UI or logs. */
internal data class Go3Profile(val id: String, val name: String)
internal data class Go3Channel(val id: String, val title: String)
internal data class Go3Replay(val scheduleId: String, val playbackId: String, val channelId: String,
    val title: String, val start: Instant)
internal class Go3Login(val code: String, val expires: Instant) {
    override fun toString() = "Go3Login([private])"
}
internal class Go3Session(val token: String, val tier: String) {
    override fun toString() = "Go3Session([private])"
}
internal class Go3Ticket(val manifest: String, val license: String, val sessionId: String?,
    val renewSeconds: Long, val headers: Map<String, String>, val live: Boolean = false) {
    override fun toString() = "Go3Ticket([private])"
}
internal class Go3Failure(val reason: String, val httpStatus: Int? = null) : Exception(reason) {
    fun diagnosticMessage(): String = reason + (httpStatus?.let { " (HTTP $it)" } ?: "")
}

internal object Go3Protocol {
    fun json(body: String): JsonElement = try { Json.parseToJsonElement(body) }
        catch (_: Exception) { throw Go3Failure("Go3 returned an unreadable response. Retry.") }
    fun text(o: JsonObject, key: String): String = (o[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun objectValue(o: JsonObject, key: String) = o[key] as? JsonObject ?: JsonObject(emptyMap())
    fun array(o: JsonObject, key: String) = o[key] as? JsonArray ?: JsonArray(emptyList())
    fun safeTitle(value: String) = value.filter { !it.isISOControl() }.take(250)
    fun profiles(root: JsonElement): List<Go3Profile> = (root as? JsonObject)?.let { o ->
        array(o, "profiles").mapNotNull { (it as? JsonObject)?.let { p ->
            val id = text(p, "uid").ifBlank { text(p, "id") }
            if (id.isBlank()) null else Go3Profile(id, safeTitle(text(p, "name")).ifBlank { "Go3 profile" })
        } }
    }.orEmpty()
    fun channels(root: JsonElement): List<Go3Channel> = (root as? JsonArray).orEmpty().flatMap { section ->
        array(section as? JsonObject ?: return@flatMap emptyList(), "elements").mapNotNull { element ->
            val item = objectValue(element as? JsonObject ?: return@mapNotNull null, "item")
            val id = text(item, "id")
            if (!id.matches(Regex("[0-9]+"))) null
            else Go3Channel(id, safeTitle(text(item, "title")).ifBlank { "Go3 channel" })
        }
    }.distinctBy { it.id }
    fun replays(root: JsonElement, channel: String, from: Instant, until: Instant): List<Go3Replay> {
        val rows = (root as? JsonArray) ?: (root as? JsonObject)?.get("items") as? JsonArray
        return rows.orEmpty().mapNotNull { raw ->
            val o = raw as? JsonObject ?: return@mapNotNull null
            val schedule = text(o, "id")
            val recording = text(o, "programRecordingId")
            val live = text(objectValue(o, "live"), "id").ifBlank { text(o, "liveId") }.ifBlank { text(o, "channelId") }
            val start = runCatching { Instant.parse(text(o, "since")) }.getOrNull() ?: return@mapNotNull null
            val availableUntil = runCatching { Instant.parse(text(o, "catchupTill")) }.getOrNull() ?: return@mapNotNull null
            // A schedule ID or live channel is not a replay recording destination.
            if (live != channel || !recording.matches(Regex("[0-9]+")) || schedule.isBlank() ||
                start < from || start >= until || availableUntil <= until) return@mapNotNull null
            Go3Replay(schedule, recording, live, safeTitle(text(o, "title")), start)
        }.distinctBy { it.scheduleId }.sortedByDescending { it.start }
    }
    fun failure(status: Int, body: String): Go3Failure {
        val marker = body.uppercase()
        val message = when {
            status == -1 -> "Go3 metadata response exceeded the size limit. Retry."
            status == 0 -> "Go3 network request failed. Retry."
            status == 401 || "AUTHENTICATION_REQUIRED" in marker || "HTTP_SESSION_EXPIRED" in marker -> "Go3 sign-in expired. Sign in again."
            "DEVICE_LIMIT" in marker || "DEVICE_NOT_EXISTS" in marker -> "Go3 device limit or registration error. Check your Go3 account."
            status == 429 || "VIDEO_SESSION_LIMIT" in marker -> "Go3 concurrent playback limit reached. Stop another Go3 stream and retry."
            status == 402 || "ITEM_NOT_PAID" in marker || "ITEM_NOT_AVAILABLE" in marker -> "This programme is unavailable with your Go3 subscription."
            "GEOIP" in marker || "INVALID_REGION" in marker -> "Go3 is unavailable in your current region."
            else -> "Go3 request failed. Retry."
        }
        return Go3Failure(message,status.takeIf { it in 100..599 })
    }
}
