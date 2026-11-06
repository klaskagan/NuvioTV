package com.nuvio.tv.go3

enum class Go3PlaybackChoice { LIVE, BEGINNING }

internal fun Go3Programme.playbackLocator(choice: Go3PlaybackChoice): String =
    if(availability==Go3Availability.LIVE && choice==Go3PlaybackChoice.BEGINNING) "$id?start=beginning" else id

/** Exact programme start within the licensed live DVR window, never its unrelated earliest segment. */
internal fun go3StartOverPosition(programmeStartMs: Long,windowStartMs: Long?,durationMs: Long,seekable: Boolean): Long? {
    if(!seekable || windowStartMs==null || durationMs<0) return null
    val target=try { Math.subtractExact(programmeStartMs,windowStartMs) } catch(_: ArithmeticException) { return null }
    return target.takeIf { it>=0 && it<=durationMs }
}


internal fun go3DvrRange(
    programmeStartMs: Long?,
    windowStartMs: Long?,
    durationMs: Long,
    livePositionMs: Long,
    seekable: Boolean,
): LongRange? {
    if (!seekable || programmeStartMs == null || windowStartMs == null ||
        livePositionMs !in 1..durationMs) return null
    val start = try {
        Math.subtractExact(programmeStartMs, windowStartMs).coerceAtLeast(0L)
    } catch (_: ArithmeticException) {
        return null
    }
    return if (start < livePositionMs) start..livePositionMs else null
}

internal fun LongRange.isAtGo3LiveEdge(positionMs: Long): Boolean = positionMs >= last - 6_000
