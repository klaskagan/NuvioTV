package com.nuvio.tv.go3

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Go3CatalogueTest {
    private val now=Instant.parse("2026-11-04T12:00:00Z")
    private fun row(id: String="42",start: String="2026-11-03T12:00:00Z",end: String="2026-11-03T14:00:00Z",art: Boolean=true)=buildJsonObject {
        put("type","PROGRAMME");put("id",id);put("slug","united-roma");put("title","Manchester United vs Roma")
        put("since",start);put("till",end);put("catchupTill","2026-11-10T12:00:00Z");put("programRecordingId","900")
        put("live",buildJsonObject { put("id","17");put("slug","extra") })
        put("images",buildJsonObject { if(art) put("16x9",buildJsonArray { add(buildJsonObject {
            put("mainUrl","//static3.go3.tv/scale/go3/test.jpg?dstw=640&dsth=360")
        }) }) })
        put("allowPlayback",false);put("liveProgramme",true)
        put("scoreCounter",999);put("scoreDenominator",888)
    }
    @Test fun exactReplayIdentityAndArtArePreservedWithoutSpoilers() {
        val p=requireNotNull(Go3CatalogueDecoder.programme(row(),now))
        assertEquals("https://go3.lt/live_tv/extra,live-17/united-roma,programme-42",p.url)
        assertEquals("42",p.programmeId);assertEquals("900",p.recordingId)
        assertEquals(Go3Availability.REPLAY,p.availability);assertTrue(p.playable)
        assertTrue(p.artwork!!.startsWith("https://static3.go3.tv/"))
        assertFalse(p.toString().contains("999"));assertFalse(p.preview().toString().contains("888"))
    }
    @Test fun futureScheduleOverridesLiveProgrammeAndAnonymousEntitlement() {
        val p=requireNotNull(Go3CatalogueDecoder.programme(row(start="2026-11-04T19:00:00Z",end="2026-11-04T21:00:00Z"),now))
        assertEquals(Go3Availability.UPCOMING,p.availability);assertFalse(p.playable)
        assertEquals(listOf("Upcoming"),Go3CatalogueSnapshot(listOf(p),false).filters)
    }
    @Test fun catchupWindowWithoutARecordingDoesNotOfferReplay() {
        val programme = requireNotNull(Go3CatalogueDecoder.programme(JsonObject(row() - "programRecordingId"), now))
        assertEquals(Go3Availability.LISTED, programme.availability)
        assertFalse(programme.playable)
        assertTrue(Go3CatalogueSnapshot(listOf(programme), false).filters.isEmpty())
    }

    @Test fun liveAndExpiredAvailability() {
        assertEquals(Go3Availability.LIVE,Go3CatalogueDecoder.programme(row(start="2026-11-04T11:00:00Z",end="2026-11-04T13:00:00Z"),now)!!.availability)
        assertEquals(Go3Availability.LISTED,Go3CatalogueDecoder.programme(row(),Instant.parse("2026-11-11T12:00:00Z"))!!.availability)
    }
    @Test fun footerChannelsAndMismatchedDestinationsAreRejected() {
        assertNull(Go3CatalogueDecoder.programme(JsonObject(row()+ ("type" to JsonPrimitive("LIVE"))),now))
        assertNull(Go3CatalogueDecoder.programme(JsonObject(row()+ ("url" to JsonPrimitive("https://go3.lt/live_tv/extra,live-17"))),now))
        assertNull(Go3CatalogueDecoder.programme(JsonObject(row()+ ("url" to JsonPrimitive("https://go3.lt/live_tv/extra,live-17/x,programme-999"))),now))
        assertNull(Go3CatalogueDecoder.artwork("https://other.example/art.jpg"))
        assertNull(Go3CatalogueDecoder.artwork("https://static3.go3.tv/scale/go3/x?token=secret"))
    }
    @Test fun repeatedLazyEntriesRetainEarlierArtwork() {
        val rows=JsonArray(listOf(row(),row(art=false),row(id="43",art=false)))
        val p=Go3CatalogueDecoder.decode(rows,now)
        assertEquals(2,p.size);assertNotNull(p.first().artwork);assertNull(p.last().artwork)
    }
    @Test fun explicitOriginalDateOnlyNeverInferReplayDate() {
        assertNull(Go3CatalogueDecoder.programme(row(),now)!!.originalMatchDate)
        val item=JsonObject(row()+ ("title" to JsonPrimitive("United vs Roma 2026-11-03")))
        assertEquals("2026-11-03",Go3CatalogueDecoder.programme(item,now)!!.originalMatchDate.toString())
    }
    @Test fun cacheExpiresAfterTwoMinutesAndRefreshFailureIsMarkedStale()=runTest {
        var time=0L;var calls=0;val cache=Go3CatalogueCache { time }
        val rows=listOf(requireNotNull(Go3CatalogueDecoder.programme(row(),now)))
        suspend fun load(force: Boolean=false)=cache.load(Go3CatalogueSource.UCL,force) { calls++;rows }
        load();time=119999;load();assertEquals(1,calls)
        time=120000;assertTrue(cache.cached(Go3CatalogueSource.UCL)!!.stale);load();assertEquals(2,calls)
        load(true);assertEquals(3,calls)
        val stale=cache.load(Go3CatalogueSource.UCL,true) { error("private raw provider error") }
        assertTrue(stale.stale);assertEquals(rows,stale.programmes)
        assertEquals(rows,load().programmes);assertEquals(4,calls)
    }
    @Test fun cancellationIsNotConvertedIntoStaleSuccess()=runTest {
        val cache=Go3CatalogueCache { 0 };cache.load(Go3CatalogueSource.LIVE) { emptyList() }
        try { cache.load(Go3CatalogueSource.LIVE,true) { throw CancellationException() };fail() } catch(_: CancellationException) { }
        assertFalse(cache.cached(Go3CatalogueSource.LIVE)!!.stale)
    }
    @Test fun cardLabelsKeepTeamNamesVisibleWithoutReplacingFullProgrammeTitle() {
        val event=Go3CatalogueDecoder.programme(JsonObject(row()+ ("title" to JsonPrimitive("UEFA Champions League. Manchester United - Roma"))),now)!!
        assertEquals("Manchester United - Roma",event.preview().name)
        assertEquals("UEFA Champions League. Manchester United - Roma",event.title)
    }
    @Test fun allSourcesHaveOfficialIdentityAndSportsType() {
        assertEquals(listOf("live","epl","ucl","uel","uecl","laliga","bundesliga","ligue1","seriea","facup"),Go3CatalogueSource.entries.map { it.key })
        assertTrue(Go3CatalogueSource.entries.all { it.discover().type=="sport" && it.officialUrl.startsWith("https://go3.lt/") })
    }
    @Test fun competitionPrefixesDoNotCrowdOutTeamNamesOnCards() {
        for (league in listOf("UEFA Europa League", "UEFA Conference League", "UEFA Europa Conference League", "LaLiga", "Bundesliga", "Ligue 1", "Serie A", "FA Cup")) {
            val title = "$league.  Home - Away"
            val event = requireNotNull(Go3CatalogueDecoder.programme(JsonObject(row()+ ("title" to JsonPrimitive(title))),now))
            assertEquals("Home - Away",event.cardTitle)
            assertEquals(title,event.title)
        }
    }

}
