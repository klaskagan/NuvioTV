package com.nuvio.tv.go3

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class Go3TransportTest {
    @Test fun `credential free media follows ordinary same scheme CDN redirects`() {
        MockWebServer().use { source -> MockWebServer().use { cdn ->
            cdn.enqueue(MockResponse().setBody("fixture manifest"))
            source.enqueue(MockResponse().setResponseCode(302).setHeader("Location",cdn.url("/manifest.mpd")))
            val client=Go3Transport.mediaClient()
            try {
                client.newCall(Request.Builder().url(source.url("/entry.mpd")).build()).execute().use {
                    assertEquals(200,it.code); assertEquals("fixture manifest",it.body.string())
                }
                val redirected=cdn.takeRequest()
                assertNull(redirected.getHeader("API-Authentication"))
                assertNull(redirected.getHeader("API-ProfileUid"))
                assertNull(redirected.getHeader("Cookie"))
                assertFalse(client.followSslRedirects)
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
        } }
    }
    @Test fun `account and license transport does not follow redirects`() {
        MockWebServer().use { source -> MockWebServer().use { other ->
            source.enqueue(MockResponse().setResponseCode(302).setHeader("Location",other.url("/unexpected")))
            val client=Go3Transport.restrictedClient()
            try {
                client.newCall(Request.Builder().url(source.url("/account")).header("API-Authentication","fixture-only").build())
                    .execute().use { assertEquals(302,it.code) }
                assertEquals(0,other.requestCount)
                assertFalse(client.followSslRedirects)
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
        } }
    }
    @Test fun `diagnostics contain fixed text and numeric status without provider payload`() {
        val error=Go3Protocol.failure(403,"private signed-url token score 5-1")
        assertEquals(403,error.httpStatus)
        assertEquals("Go3 request failed. Retry. (HTTP 403)",error.diagnosticMessage())
        assertFalse(error.diagnosticMessage().contains("signed-url"))
        assertFalse(error.toString().contains("5-1"))
        assertTrue(Go3Protocol.failure(-1,"").reason.contains("size limit"))
    }
}
