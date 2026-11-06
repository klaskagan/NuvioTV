package com.nuvio.tv.go3

import android.content.Context
import android.os.Build
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Device-local Go3 authentication. Never participates in Nuvio sync or sign-out. */
@Singleton
class Go3Account @Inject constructor(@ApplicationContext context: Context) {
    private val secrets=ProtectedProfilePreferences(context,"go3_experimental_auth","com.nuvio.tv.go3.experimental.v1")
    private val prefs=context.getSharedPreferences("go3_experimental_device",Context.MODE_PRIVATE)
    private val uid=prefs.getString("uid",null) ?: UUID.randomUUID().toString().replace("-","").also { prefs.edit().putString("uid",it).commit() }
    internal val api=Go3ExperimentalApi(uid,listOf(Build.MODEL,Build.BOARD,"Android",Build.VERSION.RELEASE,
        Build.MANUFACTURER,"NuvioTV experimental").joinToString(";") { it.replace(';','_') }+";")
    internal fun session(): Go3Session? = secrets.read(0)?.let {
        runCatching { Go3Protocol.json(it).jsonObject }.getOrNull()?.let { o ->
            Go3Protocol.text(o,"token").takeIf(String::isNotBlank)?.let { token -> Go3Session(token,Go3Protocol.text(o,"tier")) }
        }
    }
    internal fun save(value: Go3Session) { secrets.write(0,buildJsonObject { put("token",value.token); put("tier",value.tier) }.toString()) }
    internal fun selectProfile(id: String) { prefs.edit().putString("profile",id).apply() }
    internal suspend fun profile(session: Go3Session): String {
        val profiles=api.profiles(session)
        return profiles.firstOrNull { it.id==prefs.getString("profile",null) }?.id
            ?: profiles.singleOrNull()?.id
            ?: throw Go3Failure("Choose a Go3 viewing profile in Settings → Go3.")
    }
    internal fun reset() { secrets.write(0,null); prefs.edit().remove("profile").apply(); api.forgetSessionCookies() }
    internal suspend fun playbackProgramme(programme: Go3Programme): Go3Programme {
        if(programme.availability!=Go3Availability.LIVE) return programme
        val account=session() ?: return programme
        return api.programme(account,profile(account),programme.programmeId)
    }
    internal suspend fun prepare(programme: Go3Programme): Go3PlaybackLease {
        val account=session() ?: throw Go3Failure("Sign in to Go3 in Settings → Go3.")
        if(!programme.playable) throw Go3Failure("This Go3 programme is not currently playable.")
        val live=programme.availability==Go3Availability.LIVE
        val destination=if(live) programme.channelId else programme.recordingId
            ?: throw Go3Failure("This exact Go3 replay is unavailable. Retry.")
        return Go3PlaybackLease(api,account,api.ticket(account,profile(account),destination,live))
    }
}
internal class Go3PlaybackLease(private val api: Go3ExperimentalApi,private val session: Go3Session,val ticket: Go3Ticket) {
    private var closed=false
    suspend fun renew() { if(!closed) ticket.sessionId?.let { api.renew(session,it) } }
    suspend fun close() {
        if(closed) return
        closed=true
        withContext(NonCancellable) { withTimeoutOrNull(5000) { ticket.sessionId?.let { runCatching { api.close(session,it) } } } }
    }
    override fun toString()="Go3PlaybackLease([private])"
}
