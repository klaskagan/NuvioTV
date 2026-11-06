package com.nuvio.tv.go3

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Entirely in-memory transport, no real Go3 account, preferences, sockets or DRM service. */
class Go3ExperimentalApiTest {
    private class Harness(vararg replies: Pair<Int,String>) : AutoCloseable {
        val requests=Collections.synchronizedList(mutableListOf<Request>())
        private val responses=ArrayDeque(replies.toList())
        val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            requests.add(chain.request())
            val response=synchronized(responses) { responses.removeFirst() }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(response.first)
                .message("fixture").body(response.second.toResponseBody()).build()
        }.build()
        val api=Go3ExperimentalApi("test-device","test-device-info", "https://go3.lt/api/".toHttpUrl(),http)
        override fun close() { http.connectionPool.evictAll(); http.dispatcher.executorService.shutdownNow() }
    }
    private val session=Go3Session("fixture-token-not-real","TIER_2")
    private suspend fun failure(block: suspend () -> Unit): Go3Failure {
        try { block(); fail("Expected neutral failure") } catch(e: Go3Failure) { return e }
        error("unreachable")
    }
    @Test fun `device flow uses Lithuanian endpoint and never includes an existing account token`() = runBlocking {
        Harness(200 to """{"code":"TEST42","till":"2030-01-01T00:00:00Z"}""",
            403 to """{"code":"OTC_NOT_VERIFIED"}""",
            200 to """{"token":"fixture-token-not-real","tier":"TIER_2"}""").use { h ->
            val code=h.api.loginCode()
            assertEquals("TEST42",code.code)
            assertNull(h.api.poll(code.code))
            assertEquals(session.token,h.api.poll(code.code)?.token)
            assertEquals("/api/subscribers/devices/otc",h.requests[0].url.encodedPath)
            assertEquals("ANDROID_TV",h.requests[0].url.queryParameter("platform"))
            assertEquals("LT",h.requests[0].url.queryParameter("lang"))
            assertNull(h.requests[0].url.queryParameter("tenant"))
            h.requests.forEach { assertNull(it.header("API-Authentication")); assertTrue(it.header("User-Agent")!!.startsWith("NuvioTV/")) }
            assertFalse(code.toString().contains(code.code))
            assertFalse(session.toString().contains(session.token))
        }
    }
    @Test fun `expired linking code is not treated as pending`() = runBlocking {
        Harness(403 to """{"code":"OTC_EXPIRED","details":"secret account text"}""").use { h ->
            val e=failure { h.api.poll("TEST42") }
            assertTrue(e.reason.contains("expired")); assertFalse(e.toString().contains("secret"))
        }
    }
    @Test fun `profiles and channels decode allowlisted metadata only`() = runBlocking {
        Harness(200 to """{"profiles":[{"uid":"p1","name":"Viewer","password":"do-not-retain"}],"email":"private"}""",
            200 to """[{"elements":[{"item":{"id":123,"title":"TV3 Sport","score":"do-not-retain"}},{"item":{"id":"bad/path","title":"invalid"}}]}]""").use { h ->
            assertEquals(listOf(Go3Profile("p1","Viewer")),h.api.profiles(session))
            assertEquals(listOf(Go3Channel("123","TV3 Sport")),h.api.channels(session,"p1"))
            assertEquals("OM_LT",h.requests.last().url.queryParameter("tenant"))
            assertEquals("p1",h.requests.last().header("API-ProfileUid"))
            assertEquals(session.token,h.requests.last().header("API-Authentication"))
        }
    }
    @Test fun `replay retains distinct schedule recording and channel identity without live fallback`() {
        val root=Go3Protocol.json("""{"items":[
          {"id":"schedule-1","programRecordingId":"456","live":{"id":"123"},"title":"Home - Away","since":"2026-11-03T18:00:00Z","catchupTill":"2026-11-10T00:00:00Z","score":"2-1"},
          {"id":"schedule-2","live":{"id":"123"},"title":"No recording","since":"2026-11-03T18:00:00Z","catchupTill":"2026-11-10T00:00:00Z"},
          {"id":"schedule-3","programRecordingId":"789","live":{"id":"123"},"title":"Upcoming","since":"2026-11-05T18:00:00Z","catchupTill":"2026-11-10T00:00:00Z"}
        ]}""")
        val rows=Go3Protocol.replays(root,"123",Instant.parse("2026-11-02T00:00:00Z"),Instant.parse("2026-11-04T00:00:00Z"))
        assertEquals(1,rows.size); assertEquals("456",rows.single().playbackId)
        assertEquals("schedule-1",rows.single().scheduleId); assertEquals("123",rows.single().channelId)
        assertFalse(rows.toString().contains("2-1"))
    }
    @Test fun `replay fallback requests small bounded pages and retains recording destination`() = runBlocking {
        val now=Instant.now()
        val start=now.minusSeconds(86400).toString()
        val future=now.plusSeconds(86400).toString()
        Harness(404 to "{}",
            200 to """{"meta":{"totalCount":1000},"items":[{"since":"$future"}]}""",
            200 to """{"items":[{"id":"schedule-1","programRecordingId":"456","live":{"id":"123"},"title":"Home - Away","since":"$start","catchupTill":"$future"},{"since":"$future"}]}""").use { h ->
            val rows=h.api.replays(session,"p1","123")
            assertEquals("456",rows.single().playbackId)
            assertEquals(3,h.requests.size)
            assertEquals(listOf(1,200),h.requests.mapNotNull { it.url.queryParameter("maxResults")?.toInt() })
        }
    }
    @Test fun `oversized metadata gives a bounded diagnostic and retains no response body`() = runBlocking {
        Harness(200 to "x".repeat(2_000_001)).use { h ->
            val e=failure { h.api.profiles(session) }
            assertTrue(e.reason.contains("size limit")); assertNull(e.httpStatus); assertNull(e.cause)
            assertFalse(e.toString().contains("xxxx"))
        }
    }
    @Test fun `catchup ticket uses exact recording and ordinary Widevine configuration data`() = runBlocking {
        Harness(200 to """{"playlistUrl":"https://go3.lt/api/playlist","videoSession":{"videoSessionId":"s_test","prolongInterval":30}}""",
            200 to """{"sources":{"DASH":[{"src":"https://cdn.example.test/stream.mpd?private=fixture"}]},"drm":{"WIDEVINE":{"src":"https://go3.lt/drm/widevine"}}}""").use { h ->
            val ticket=h.api.ticket(session,"p1","456",false)
            assertEquals("/api/products/456/videos/player/configuration",h.requests.first().url.encodedPath)
            assertEquals("CATCHUP",h.requests.first().url.queryParameter("videoType"))
            assertEquals("s_test",ticket.sessionId); assertEquals(30L,ticket.renewSeconds)
            assertEquals("https://go3.lt/drm/widevine",ticket.license)
            assertEquals(session.token,ticket.headers["API-Authentication"])
            assertFalse(ticket.toString().contains(session.token)); assertFalse(ticket.toString().contains("private=fixture"))
        }
    }
    @Test fun `playlist cannot forward credentials off origin and failed setup closes server session`() = runBlocking {
        Harness(200 to """{"playlistUrl":"https://unrelated.example/playlist","videoSession":{"videoSessionId":"s_test"}}""",
            204 to "").use { h ->
            failure { h.api.ticket(session,"p1","456",false) }
            assertEquals(2,h.requests.size)
            assertTrue(h.requests.all { it.url.host=="go3.lt" })
            assertEquals("DELETE",h.requests.last().method)
            assertEquals("/api/products/videosessions/s_test",h.requests.last().url.encodedPath)
        }
    }
    @Test fun `untrusted or cleartext license destinations are rejected before DRM`() {
        for(url in listOf("http://go3.lt/drm", "https://go3.lt.evil.test/drm", "https://u:p@go3.lt/drm", "https://evil.test/drm"))
            assertFalse(url,Go3ExperimentalApi.officialLicense(url))
        assertTrue(Go3ExperimentalApi.officialLicense("https://go3.lt/drm/widevine"))
        assertTrue(Go3ExperimentalApi.officialLicense("https://license.go3.tv/widevine"))
    }
    @Test fun `renew and close use session operations without mutating collections`() = runBlocking {
        Harness(204 to "",204 to "").use { h ->
            h.api.renew(session,"s_test"); h.api.close(session,"s_test")
            assertEquals(listOf("PUT","DELETE"),h.requests.map { it.method })
            assertTrue(h.requests.all { it.url.encodedPath=="/api/products/videosessions/s_test" })
        }
    }
    @Test fun `provider failures never expose payloads tokens entitlement details or spoilers`() = runBlocking {
        for(status in listOf(0,401,402,429,500)) {
            Harness((if(status==0) 503 else status) to """{"token":"fixture-token-not-real","score":"Home 3 Away 2","message":"private"}""").use { h ->
                val error=failure { h.api.profiles(session) }
                assertFalse(error.toString().contains("fixture-token")); assertFalse(error.toString().contains("Home 3"))
                assertFalse(error.toString().contains("private")); assertNull(error.cause)
            }
        }
        assertTrue(Go3Protocol.failure(401,"").reason.contains("expired"))
        assertTrue(Go3Protocol.failure(402,"").reason.contains("subscription"))
        assertTrue(Go3Protocol.failure(429,"").reason.contains("limit"))
    }
    @Test fun `invalid JSON never exposes original response in errors`() {
        try { Go3Protocol.json("secret fixture score malformed"); fail() } catch(e: Go3Failure) {
            assertFalse(e.toString().contains("secret")); assertNull(e.cause)
        }
    }
    @Test fun `cancelling an in flight call prevents late successful selection`() = runBlocking {
        val started=CountDownLatch(1); val release=CountDownLatch(1)
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            started.countDown(); release.await(5,TimeUnit.SECONDS)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"profiles\":[]}".toResponseBody()).build()
        }.build()
        var applied=false
        try {
            val api=Go3ExperimentalApi("test-device","test",client=http)
            val work=launch(Dispatchers.Default) { api.profiles(session); applied=true }
            assertTrue(started.await(3,TimeUnit.SECONDS)); work.cancelAndJoin(); release.countDown()
            assertFalse(applied)
        } finally { release.countDown(); http.dispatcher.executorService.shutdownNow(); http.connectionPool.evictAll() }
    }
    @Test fun `start over capability is read with the linked profile and never inferred from anonymous metadata`() = runBlocking {
        Harness(200 to """{"id":42,"type":"PROGRAMME","title":"Fenerbahçe - Liverpool","slug":"game","live":{"id":17,"slug":"extra"},"since":"2026-11-04T17:35:00Z","till":"2026-11-04T20:35:00Z","startoverAvailable":false,"scoreCounter":"SPOILER"}""",
            200 to """{"startoverAvailable":true,"scoreCounter":"SPOILER","token":"private"}""",
            200 to """{"startOver":false}""").use { h ->
            val value=h.api.programme(session,"fixture-profile","42")
            assertTrue(value.startOverAvailable)
            assertFalse(value.toString().contains("SPOILER"))
            assertEquals(listOf("/api/products/lives/programmes/42","/api/subscribers/products/lives/programmes/42","/api/products/lives/17"),h.requests.map { it.url.encodedPath })
            h.requests.forEach { request ->
                assertEquals(session.token,request.header("API-Authentication"))
                assertEquals("fixture-profile",request.header("API-ProfileUid"))
            }
        }
    }

    @Test fun `official live channel start over capability supports an event with no standalone programme start over`() = runBlocking {
        val metadata="""{"id":42,"type":"PROGRAMME","title":"Fenerbahçe - Liverpool","slug":"game","live":{"id":17,"slug":"extra"},"since":"2026-11-04T17:35:00Z","till":"2026-11-04T20:35:00Z","startoverAvailable":false}"""
        for(flag in listOf("true","false","\"true\"","null")) {
            Harness(200 to metadata,200 to """{"startoverAvailable":false}""",200 to """{"startOver":$flag}""").use { h ->
                assertEquals(flag=="true",h.api.programme(session,"fixture-profile","42").startOverAvailable)
            }
        }
    }

}
