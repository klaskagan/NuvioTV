package com.nuvio.tv.go3

import com.nuvio.tv.football.supportsFootballLineups
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Go3PlaybackChoiceTest {
    private val programme=Go3Programme("42","Fenerbahçe - Liverpool","https://go3.lt/live_tv/extra,live-17/game,programme-42",
        "17","900",null,Go3Availability.LIVE,Instant.parse("2026-11-04T17:35:00Z"),Instant.parse("2026-11-04T20:35:00Z"),startOverAvailable=true)
    @Test fun liveAndBeginningUseExplicitPublicLocatorsAndReplayKeepsItsExactIdentity() {
        assertEquals(programme.id,programme.playbackLocator(Go3PlaybackChoice.LIVE))
        assertEquals("${programme.id}?start=beginning",programme.playbackLocator(Go3PlaybackChoice.BEGINNING))
        assertEquals(programme.id,programme.copy(availability=Go3Availability.REPLAY).playbackLocator(Go3PlaybackChoice.BEGINNING))
        assertEquals("900",programme.recordingId)
    }
    @Test fun startOverUsesExactProgrammeTimeInsideTheDvrWindow() {
        assertEquals(1800000L,go3StartOverPosition(3600000,1800000,7200000,true))
        assertEquals(0L,go3StartOverPosition(1800000,1800000,7200000,true))
        assertEquals(7200000L,go3StartOverPosition(9000000,1800000,7200000,true))
    }
    @Test fun unavailableOrExpiredDvrStartNeverClampsToUnrelatedContent() {
        assertNull(go3StartOverPosition(1000,2000,7200000,true))
        assertNull(go3StartOverPosition(9000001,1800000,7200000,true))
        assertNull(go3StartOverPosition(3600000,null,7200000,true))
        assertNull(go3StartOverPosition(3600000,1800000,7200000,false))
        assertNull(go3StartOverPosition(Long.MAX_VALUE,Long.MIN_VALUE,7200000,true))
    }
    @Test fun lineupsOnlyBelongToExplicitSportsContent() {
        for(type in listOf(null,"movie","series","tv","anime","episode","unknown")) assertFalse(supportsFootballLineups(type))
        for(type in listOf("sport","sports","SPORT","Sports")) assertTrue(supportsFootballLineups(type))
    }
    @Test fun liveSeekingUsesOnlyTheRetainedPartOfTheProgrammeAndTheLiveTarget() {
        val range = requireNotNull(go3DvrRange(20_000, 10_000, 100_000, 80_000, true))
        assertEquals(10_000L..80_000L, range)
        assertTrue(range.isAtGo3LiveEdge(79_000))
        assertFalse(range.isAtGo3LiveEdge(68_000))
        assertEquals(0L..80_000L, go3DvrRange(5_000, 10_000, 100_000, 80_000, true))
        assertNull(go3DvrRange(20_000, 10_000, 100_000, 80_000, false))
        assertNull(go3DvrRange(20_000, null, 100_000, 80_000, true))
        assertNull(go3DvrRange(95_000, 10_000, 100_000, 80_000, true))
    }

}
