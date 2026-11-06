package com.nuvio.tv.go3

import android.content.Context
import androidx.media3.common.util.Log
import kotlinx.coroutines.*

/** Scoped to the existing player controller; presentation changes never touch this lease. */
internal class Go3PlayerSession(context: Context) {
    private val account=Go3Account(context.applicationContext)
    private val catalogue=Go3CatalogueRepository()
    private var lease: Go3PlaybackLease?=null
    private var renewal: Job?=null
    private var savedLogLevel: Int?=null
    var isLiveEvent: Boolean=false
        private set
    var programmeStartMs: Long? = null
        private set
    var startOverEpochMs: Long?=null
        private set
    suspend fun prepare(id: String,scope: CoroutineScope,onError: (String) -> Unit): Go3Ticket {
        close(scope)
        val beginning=id.endsWith("?start=beginning")
        val publicProgramme=catalogue.programme(id.removeSuffix("?start=beginning"),true)
        val programme=if(beginning) account.playbackProgramme(publicProgramme) else publicProgramme
        isLiveEvent=programme.availability==Go3Availability.LIVE
        programmeStartMs = programme.since?.toEpochMilli()
        startOverEpochMs=if(beginning && programme.availability==Go3Availability.LIVE) {
            if(!programme.startOverAvailable) throw Go3Failure("Go3 does not offer start from beginning for this event.")
            programme.since?.toEpochMilli() ?: throw Go3Failure("Go3 did not provide this event’s start time.")
        } else null
        val next=account.prepare(programme)
        try { currentCoroutineContext().ensureActive() } catch(cancelled: CancellationException) { next.close();throw cancelled }
        lease=next
        if(savedLogLevel==null) { savedLogLevel=Log.getLogLevel();Log.setLogLevel(Log.LOG_LEVEL_OFF) }
        renewal=scope.launch {
            try { while(isActive) { delay(next.ticket.renewSeconds*1000);next.renew() } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(lease===next) onError("Go3 playback session expired. Retry or sign in again.") }
        }
        return next.ticket
    }
    fun close(scope: CoroutineScope) {
        renewal?.cancel();renewal=null
        programmeStartMs = null
        startOverEpochMs=null
        isLiveEvent=false
        val old=lease;lease=null
        if(old!=null) scope.launch(NonCancellable) { old.close() }
        savedLogLevel?.let(Log::setLogLevel);savedLogLevel=null
    }
}
