package com.nuvio.tv.football

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface FootballProvider {
    suspend fun matches(date: LocalDate): List<FootballMatch>
    suspend fun lineups(id: String): List<FootballTeam>
}
internal class PitchApi(private val key: String,
    private val client: OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(20,TimeUnit.SECONDS).build(),
    private val base: HttpUrl="https://api.pitchapi.dev/v1/".toHttpUrl()): FootballProvider {
    private suspend fun get(segments: List<String>): JsonElement {
        if(key.isBlank() || key.length>512 || key.any { it.isISOControl() }) throw FootballFailure("Configure your PitchAPI key privately in Settings → Football Lineups.")
        val url=base.newBuilder().apply {
            segments.forEach(::addPathSegment)
            if(segments.firstOrNull()=="date") addQueryParameter("status","all")
        }.build()
        val request=Request.Builder().url(url).header("X-API-KEY",key).header("Accept","application/json").header("User-Agent","NuvioTV/1.0").build()
        return suspendCancellableCoroutine { c ->
            val call=client.newCall(request);c.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call,e: IOException) { if(c.isActive) c.resumeWithException(FootballFailure()) }
                override fun onResponse(call: Call,response: Response) {
                    try {
                        val data=response.use {
                            if(!it.isSuccessful) throw FootballFailure()
                            val bytes=java.io.ByteArrayOutputStream()
                            it.body.byteStream().use { input ->
                                val buffer=ByteArray(8192)
                                while(bytes.size()<=2_000_000) {
                                    val n=input.read(buffer,0,minOf(buffer.size,2_000_001-bytes.size()))
                                    if(n<0) break
                                    bytes.write(buffer,0,n)
                                }
                            }
                            if(bytes.size()>2_000_000) throw FootballFailure()
                            Json.parseToJsonElement(bytes.toString("UTF-8"))
                        }
                        if(c.isActive) c.resume(data)
                    } catch(_: Exception) { if(c.isActive) c.resumeWithException(FootballFailure()) }
                }
            })
        }
    }
    override suspend fun matches(date: LocalDate)=FootballDecoder.matches(get(listOf("date",date.toString())))
    override suspend fun lineups(id: String): List<FootballTeam> {
        if(id.isBlank() || id.length>150 || id.any { it.isISOControl() }) throw FootballFailure()
        return FootballDecoder.lineups(get(listOf("matches",id,"lineups")))
    }
}
internal suspend fun resolveLineups(provider: FootballProvider,title: String,explicitDate: LocalDate?=FootballMatching.explicitDate(title),today: LocalDate=LocalDate.now(ZoneOffset.UTC),competitors: Set<String> = emptySet()): FootballLineups {
    val found=linkedMapOf<String,FootballMatch>()
    for(date in FootballMatching.dates(today,explicitDate)) {
        currentCoroutineContext().ensureActive()
        provider.matches(date).filter { FootballMatching.matches(title,it,competitors) }.forEach { found[it.id]=it }
    }
    val match=found.values.singleOrNull() ?: throw FootballFailure("Original confirmed lineups are unavailable for this programme. Retry.")
    val teams=provider.lineups(match.id)
    if(teams.isEmpty()) throw FootballFailure("Original confirmed lineups are not available yet. Retry.")
    return FootballLineups(match,teams)
}
