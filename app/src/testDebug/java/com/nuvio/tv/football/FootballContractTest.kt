package com.nuvio.tv.football

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FootballContractTest {
    private val match=FootballMatch("m_opaqueAlpha7","Manchester United","Roma","UEFA","2026-11-03T20:00:00Z")
    private val player=FootballPlayer("Original Player","7",.5f,.5f)
    private val teams=listOf(FootballTeam("home","Manchester United","4-3-3","Original Coach",listOf(player),listOf(player)))
    private fun fixture(confirmed: JsonElement=JsonPrimitive(true))=buildJsonObject {
        put("data",buildJsonObject {
            put("home_team",buildJsonObject { put("name","Manchester United") })
            put("home",buildJsonObject {
                put("confirmed",confirmed);put("formation","4-3-3");put("coach",buildJsonObject { put("name","Original Coach") })
                for(key in listOf("starters","subs")) put(key,buildJsonArray { add(buildJsonObject {
                    put("name","Original Player");put("shirt_number",7);put("pitch_x",.5);put("pitch_y",.4)
                    put("rating","SPOILER_RATING");put("goals","SPOILER_GOAL");put("substitution_minutes","SPOILER_MINUTE")
                    put("participation","SPOILER_PARTICIPATION");put("image","https://provider.example/match/SPOILER_IMAGE")
                }) })
                put("events","SPOILER_EVENT");put("status","SPOILER_STATUS")
            })
            put("away",buildJsonObject { put("confirmed",false) })
            put("score","SPOILER_SCORE")
        })
    }
    @Test fun domesticReplayUsesExactCompetitorTagsAndLeague() {
        val bayern = FootballMatch("m_first", "Bayern Munich", "Borussia Dortmund", "Bundesliga", "")
        val tags = setOf("borussia-dortmund", "bayern-munich")
        assertTrue(FootballMatching.matches("Bundesliga. Bayern - Borussia", bayern, tags))
        assertTrue(FootballMatching.matches("Bundesliga. Bayern - Borussia",
            bayern.copy(home = "FC Bayern München", away = "Dortmund"), tags))
        assertFalse(FootballMatching.matches("Bundesliga. Bayern - Borussia", bayern.copy(away = "Borussia Mönchengladbach"), tags))
        assertFalse(FootballMatching.matches("Bundesliga. Bayern - Borussia", bayern.copy(competition = "DFB Pokal"), tags))
        assertFalse(FootballMatching.matches("Bundesliga. Bayern - Borussia", bayern))
        assertTrue(FootballMatching.matches("LaLiga. Athletic - Real Sociedad",
            FootballMatch("m_spain", "Athletic Club", "Real Sociedad", "LaLiga", ""),
            setOf("athletic-bilbao", "real-sociedad")))
    }

    @Test fun domesticShortNamesAllowClubNumbersWithoutMatchingOtherSquads() {
        val fixture = FootballMatch("m_germany", "Mainz 05", "Bayern Munich", "Bundesliga", "")
        assertTrue(FootballMatching.matches("Bundesliga. Mainz - Bayern", fixture))
        assertFalse(FootballMatching.matches("Bundesliga. Mainz - Bayern", fixture.copy(home = "Mainz 05 II")))
        assertFalse(FootballMatching.matches("Bundesliga. Mainz - Bayern", fixture.copy(competition = "2. Bundesliga")))
    }

    @Test fun pitchSurnamesPreserveParticlesAndBenchKeepsInitials() {
        for ((full, surname, bench) in listOf(
            Triple("Luke Young", "Young", "L. Young"),
            Triple("Virgil van Dijk", "van Dijk", "V. van Dijk"),
            Triple("Alexis Mac Allister", "Mac Allister", "A. Mac Allister"),
            Triple("Mac Allister", "Mac Allister", "Mac Allister"),
        )) {
            val player = FootballPlayer(full, "10", null, null)
            assertEquals(surname, player.surname)
            assertEquals(bench, player.compactName)
        }
    }

    @Test fun fixtureMatchingHandlesClubAffixesAndAbbreviationsWithoutConfusingOtherSides() {
        for ((title, home, away) in listOf(
            Triple("Villarreal - PSG", "Villarreal CF", "Paris Saint-Germain"),
            Triple("Villarreal - PSG", "Villarreal", "Paris SG"),
            Triple("Porto - Napoli", "FC Porto", "SSC Napoli"),
            Triple("PSV - Club Brugge", "PSV Eindhoven", "Club Brugge KV"),
            Triple("Viking - Sabah", "Viking FK", "Sabah FC"),
        )) {
            assertTrue(title, FootballMatching.matches("UEFA Champions League. $title",
                FootballMatch("m_fixture", home, away, "Champions League", "")))
        }
        val fixture = FootballMatch("m_fixture", "PSV Eindhoven Women", "Club Brugge", "", "")
        assertFalse(FootballMatching.matches("PSV - Club Brugge", fixture))
        assertFalse(FootballMatching.matches("PSV Eindhoven U19 - Club Brugge", fixture))
    }

    @Test fun completedPayloadOnlyExposesOriginalConfirmedAllowlist() {
        val decoded=FootballDecoder.lineups(fixture())
        assertEquals(1,decoded.size);assertEquals(teams[0].coach,decoded[0].coach)
        assertEquals(1,decoded[0].starters.size);assertEquals(1,decoded[0].bench.size)
        assertFalse(decoded.toString().contains("SPOILER"))
    }
    @Test fun predictionsAndStringConfirmationAreRejected() {
        assertTrue(FootballDecoder.lineups(fixture(JsonPrimitive(false))).isEmpty())
        assertTrue(FootballDecoder.lineups(fixture(JsonPrimitive("true"))).isEmpty())
        assertTrue(FootballDecoder.lineups(fixture(JsonNull)).isEmpty())
    }
    @Test fun coordinatesMustBeNumericFiniteAndNormalized() {
        for(v in listOf(JsonPrimitive(-.1),JsonPrimitive(1.1),JsonPrimitive("0.5"),JsonPrimitive("NaN"),JsonNull)) {
            val root=fixture().toMutableMap();val d=root["data"]!!.jsonObject.toMutableMap();val h=d["home"]!!.jsonObject.toMutableMap()
            h["starters"]=buildJsonArray { add(buildJsonObject { put("name","Safe Player");put("pitch_x",v);put("pitch_y",1) }) }
            d["home"]=JsonObject(h);root["data"]=JsonObject(d)
            val p=FootballDecoder.lineups(JsonObject(root)).single().starters.single()
            assertNull(p.pitchX);assertEquals(1f,p.pitchY!!,0f);assertEquals("",p.number)
        }
    }
    @Test fun fixtureDecoderDropsAllSpoilersAndKeepsOpaqueIds() {
        val root=Json.parseToJsonElement("""{"data":{"matches":[{"id":"m_opaqueAlpha7","home_team":{"name":"Manchester United","score":999},"away_team":{"name":"Roma"},"league":{"name":"UEFA"},"time_utc":"2026-11-03T20:00:00Z","score":"SPOILER","status":"SPOILER","events":["SPOILER"]}]}}""")
        val result=FootballDecoder.matches(root).single()
        assertEquals(match,result);assertFalse(result.toString().contains("SPOILER"));assertFalse(result.toString().contains("999"))
    }
    @Test fun championsLeagueReplayMatchesSlaviaPragueAndArsenalClubNames() {
        val fixture = match.copy(home = "SK Slavia Prague", away = "Arsenal FC")
        assertTrue(FootballMatching.matches("UEFA Champions League. Slavia Praha - Arsenal", fixture))
        assertFalse(FootballMatching.matches("UEFA Champions League. Sparta Praha - Arsenal", fixture))
        assertFalse(FootballMatching.matches("Slavia Praha - Arsenal Women", fixture))
        assertTrue(FootballMatching.matches("English Premier League. Arsenal - Liverpool",
            fixture.copy(home = "Arsenal", away = "Liverpool")))
    }

    @Test fun conferenceLeagueShortNamesMatchOnlyTheIntendedClubs() {
        val fixture = match.copy(home = "FC Thun", away = "Heart of Midlothian FC")
        assertTrue(FootballMatching.matches("UEFA Conference League.  Thun - Hearts", fixture))
        assertFalse(FootballMatching.matches("Thun - Hearts of Oak", fixture))
        assertFalse(FootballMatching.matches("Thun II - Hearts", fixture))
    }

    @Test fun aliasesAccentsPunctuationAndWholeTeamBoundaries() {
        assertTrue(FootballMatching.matches("UCL: MAN. UTD — Róma",match))
        assertTrue(FootballMatching.matches("Man United - Roma",match))
        assertFalse(FootballMatching.matches("Manchester United - Romagna",match))
        assertFalse(FootballMatching.matches("United vs Roma",match))
    }
    @Test fun explicitReplayDateAndUtcDateBoundaries()=runTest {
        val dates=mutableListOf<LocalDate>()
        val provider=object: FootballProvider {
            override suspend fun matches(date: LocalDate): List<FootballMatch> { dates+=date;return listOf(match) }
            override suspend fun lineups(id: String)=teams
        }
        resolveLineups(provider,"Man Utd vs Roma 2026-11-03",today=LocalDate.parse("2026-11-04"))
        assertEquals(listOf(LocalDate.parse("2026-11-03")),dates)
        dates.clear();resolveLineups(provider,"Man Utd vs Roma",today=LocalDate.parse("2026-01-01"))
        assertEquals(15,dates.size);assertEquals(LocalDate.parse("2025-12-18"),dates.last())
    }
    @Test fun ambiguousRepeatedFixtureDoesNotChooseLatest()=runTest {
        var lineups=0
        val provider=object: FootballProvider {
            override suspend fun matches(date: LocalDate)=listOf(match,match.copy(id="m_other",kickoff="2026-11-01T20:00:00Z"))
            override suspend fun lineups(id: String): List<FootballTeam> { lineups++;return teams }
        }
        try { resolveLineups(provider,"Man Utd vs Roma");fail() } catch(e: FootballFailure) { assertFalse(e.safeMessage.contains("m_other")) }
        assertEquals(0,lineups)
    }
    @Test fun dismissAndSelectionInvalidateLateResponsesAndSuccessfulReopenUsesCache()=runTest {
        var calls=0;var gate: CompletableDeferred<Unit>?=null
        val provider=object: FootballProvider {
            override suspend fun matches(date: LocalDate)=listOf(match)
            override suspend fun lineups(id: String): List<FootballTeam> {
                calls++;gate?.let { withContext(NonCancellable) { it.await() } };return teams
            }
        }
        val session=FootballLineupSession(backgroundScope) { provider }
        session.select("first");session.open("Man Utd vs Roma 2026-11-03");runCurrent()
        assertNotNull(session.state.value.lineups);session.close();session.open("Man Utd vs Roma 2026-11-03");runCurrent();assertEquals(1,calls)
        gate=CompletableDeferred();session.open("Man Utd vs Roma 2026-11-03",true);runCurrent();session.close()
        session.select("second");gate!!.complete(Unit);runCurrent()
        assertNull(session.state.value.lineups)
    }
    @Test fun providerFailureIsNeutralAndNeverExposesRawError() {
        try { FootballDecoder.lineups(Json.parseToJsonElement("""{"error":"SPOILER with secret key"}"""));fail() }
        catch(e: FootballFailure) { assertFalse(e.message!!.contains("SPOILER"));assertFalse(e.message!!.contains("secret")) }
    }
}
