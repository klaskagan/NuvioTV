package com.nuvio.tv.football

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PitchApiTransportTest {
    @Test fun exactHeadersOpaquePathAndNeutralInvalidKeyFailure()=runBlocking {
        val requests=mutableListOf<Request>()
        var fail=false
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            requests+=chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if(fail) 403 else 200).message("fixture")
                .body((if(fail) "SPOILER and private provider text" else """{"data":{"matches":[]}}""").toResponseBody()).build()
        }.build()
        try {
            val api=PitchApi("fixture-only-key",http)
            assertTrue(api.matches(LocalDate.parse("2026-11-03")).isEmpty())
            assertTrue(api.lineups("m_opaque_Alpha").isEmpty())
            assertEquals("/v1/date/2026-11-03",requests[0].url.encodedPath)
            assertEquals("all",requests[0].url.queryParameter("status"))
            assertEquals("/v1/matches/m_opaque_Alpha/lineups",requests[1].url.encodedPath)
            requests.forEach { assertEquals("NuvioTV/1.0",it.header("User-Agent"));assertEquals("application/json",it.header("Accept"));assertEquals("fixture-only-key",it.header("X-API-KEY")) }
            fail=true
            try { api.matches(LocalDate.now());fail() } catch(e: FootballFailure) { assertFalse(e.toString().contains("SPOILER"));assertFalse(e.toString().contains("fixture-only-key")) }
        } finally { http.dispatcher.executorService.shutdownNow();http.connectionPool.evictAll() }
    }
}
