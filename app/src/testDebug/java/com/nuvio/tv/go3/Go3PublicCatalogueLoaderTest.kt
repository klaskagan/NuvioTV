package com.nuvio.tv.go3

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Go3PublicCatalogueLoaderTest {
    private fun programme(id: Int,art: Boolean)=buildJsonObject {
        put("id",id);put("type","PROGRAMME");put("title","Original programme $id");put("slug","programme-$id")
        put("live",buildJsonObject { put("id",7);put("slug","extra") })
        put("since","2026-11-03T20:00:00Z");put("till","2026-11-03T22:00:00Z");put("catchupTill","2026-11-10T20:00:00Z");put("programRecordingId",id+1000)
        if(art) put("images",buildJsonObject { put("16x9",buildJsonArray { add(buildJsonObject { put("mainUrl","//static3.go3.tv/scale/go3/$id.jpg?dstw=640") }) }) })
    }
    @Test fun categoryPagingCollectsLazyEntriesRetainsArtAndNeverSendsAccountHeaders()=runBlocking {
        val requests=mutableListOf<Request>()
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            val request=chain.request();requests+=request
            val root=buildJsonObject {
                put("meta",buildJsonObject { put("totalCount",101) })
                put("items",buildJsonArray {
                    if(request.url.queryParameter("firstResult")=="0") repeat(100) { add(programme(it+1,true)) }
                    else { add(programme(1,false));add(programme(101,false));add(buildJsonObject { put("type","LIVE");put("id",999) }) }
                })
            }
            Response.Builder().request(request).code(200).message("fixture").protocol(Protocol.HTTP_1_1).body(root.toString().toResponseBody()).build()
        }.build()
        try {
            val rows=Go3PublicCatalogueLoader(http,now={ Instant.parse("2026-11-04T12:00:00Z") }).load(Go3CatalogueSource.UCL)
            assertEquals(101,rows.size);assertNotNull(rows.first().artwork);assertNull(rows.last().artwork)
            assertEquals(listOf("0","100"),requests.map { it.url.queryParameter("firstResult") })
            requests.forEach { assertEquals("6886205",it.url.queryParameter("categoryId[]"));assertNull(it.header("API-Authentication"));assertNull(it.header("Cookie"));assertEquals("NuvioTV/1.0",it.header("User-Agent")) }
        } finally { http.dispatcher.executorService.shutdownNow();http.connectionPool.evictAll() }
    }
    @Test fun liveSectionUsesProgrammeItemsAndRejectsFooterLinks()=runBlocking {
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("/api/products/sections/7998051",chain.request().url.encodedPath)
            val root=buildJsonObject { put("elements",buildJsonArray {
                add(buildJsonObject { put("item",programme(42,true)) })
                add(buildJsonObject { put("item",buildJsonObject { put("type","LIVE");put("id",99) }) })
            }) }
            Response.Builder().request(chain.request()).code(200).message("fixture").protocol(Protocol.HTTP_1_1).body(root.toString().toResponseBody()).build()
        }.build()
        try { assertEquals(listOf("42"),Go3PublicCatalogueLoader(http).load(Go3CatalogueSource.LIVE).map { it.programmeId }) }
        finally { http.dispatcher.executorService.shutdownNow();http.connectionPool.evictAll() }
    }
}
