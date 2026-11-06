package com.nuvio.tv.football

import com.nuvio.tv.go3.footballProgrammeTitle
import kotlinx.serialization.json.*
import java.text.Normalizer
import java.time.LocalDate

// Only these allowlisted values can cross into presentation or accessibility.
data class FootballMatch(val id: String,val home: String,val away: String,val competition: String,val kickoff: String)
data class FootballPlayer(val name: String,val number: String,val pitchX: Float?,val pitchY: Float?) {
    val surname: String
        get() = nameParts().second

    val compactName: String
        get() {
            val (givenName, surname) = nameParts()
            return if (givenName == null) surname else "${givenName.take(1).uppercase(java.util.Locale.ROOT)}. $surname"
        }

    private fun nameParts(): Pair<String?, String> {
        val parts = name.trim().split(whitespace)
        var start = parts.lastIndex
        while (start > 0 && parts[start - 1].lowercase(java.util.Locale.ROOT) in surnameParticles) {
            start--
        }
        return parts.first().takeIf { start > 0 } to parts.drop(start).joinToString(" ")
    }

    private companion object {
        val whitespace = Regex("""\s+""")
        val surnameParticles = setOf("de", "del", "la", "van", "von", "der", "den", "da", "di", "dos", "do", "mac")
    }
}
data class FootballTeam(val side: String,val name: String,val formation: String,val coach: String,val starters: List<FootballPlayer>,val bench: List<FootballPlayer>)
data class FootballLineups(val match: FootballMatch,val teams: List<FootballTeam>)

internal object FootballDecoder {
    private fun text(o: JsonObject,key: String)= (o[key] as? JsonPrimitive)?.contentOrNull.orEmpty().filter { !it.isISOControl() }.take(250)
    private fun obj(o: JsonObject,key: String)=o[key] as? JsonObject ?: JsonObject(emptyMap())
    private fun data(root: JsonElement): JsonObject {
        val o=root as? JsonObject ?: throw FootballFailure()
        if(o["error"]?.let { it !is JsonNull && it!=JsonPrimitive(false) }==true) throw FootballFailure()
        return o["data"] as? JsonObject ?: throw FootballFailure()
    }
    fun matches(root: JsonElement): List<FootballMatch> = (data(root)["matches"] as? JsonArray).orEmpty().mapNotNull {
        val o=it as? JsonObject ?: return@mapNotNull null
        val id=text(o,"id");val home=text(obj(o,"home_team"),"name");val away=text(obj(o,"away_team"),"name")
        if(id.isBlank() || id.length>150 || home.isBlank() || away.isBlank()) null
        else FootballMatch(id,home,away,text(obj(o,"league"),"name"),text(o,"time_utc"))
    }
    fun lineups(root: JsonElement): List<FootballTeam> {
        val d=data(root)
        fun coordinate(o: JsonObject,key: String): Float? {
            val raw=o[key] as? JsonPrimitive ?: return null
            if(raw.isString) return null
            return raw.floatOrNull?.takeIf { it.isFinite() && it in 0f..1f }
        }
        fun players(o: JsonObject,key: String)=(o[key] as? JsonArray).orEmpty().mapNotNull { raw ->
            val p=raw as? JsonObject ?: return@mapNotNull null
            val name=text(p,"name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            FootballPlayer(name,text(p,"shirt_number").takeUnless { it=="null" }.orEmpty(),coordinate(p,"pitch_x"),coordinate(p,"pitch_y"))
        }
        return listOf("home","away").mapNotNull { side ->
            val o=obj(d,side)
            val confirmed=o["confirmed"] as? JsonPrimitive
            if(confirmed==null || confirmed.isString || confirmed.booleanOrNull!=true) null else
                FootballTeam(side,text(obj(d,"${side}_team"),"name"),text(o,"formation"),text(obj(o,"coach"),"name"),players(o,"starters"),players(o,"subs"))
        }
    }
}

internal object FootballMatching {
    fun normalize(s: String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT)
        .replace(Regex("man[ .-]+(?:utd|united)\\b"),"manchester united").replace(Regex("[^a-z0-9]+")," ").trim()
    private val clubAffixes = setOf("fc", "fk", "cf", "afc", "ac", "as", "sc", "sk", "rc", "sv", "vfb", "vfl", "ssc", "kv", "tsg", "club")
    private val clubAliases = listOf(
        setOf("hearts", "heart of midlothian"),
        setOf("slavia praha", "slavia prague"),
    )
    private val fixtureSeparator = Regex("""\s+(?:[-–—]|vs\.?|v\.?)\s+""", RegexOption.IGNORE_CASE)
    private val trailingDate = Regex("""\s+\(?\d{4}-\d{2}-\d{2}\)?\s*$""")

    private val citySpellings = mapOf("munchen" to "munich", "koeln" to "cologne", "koln" to "cologne")

    private fun clubWords(name: String): List<String> {
        val words = normalize(name).split(' ').map { citySpellings[it] ?: it }
        fun isAffix(word: String) = word in clubAffixes || word == "1" ||
            (word.length == 4 && word.startsWith("18") && word.all(Char::isDigit)) ||
            (word.length == 4 && word.startsWith("19") && word.all(Char::isDigit)) ||
            (word.length == 2 && word.startsWith("0") && word.all(Char::isDigit))
        return words.dropWhile(::isAffix).dropLastWhile(::isAffix)
            .ifEmpty { words }
    }

    private fun teamMatches(label: String, name: String): Boolean {
        val first = clubWords(label)
        val second = clubWords(name)
        val firstName = first.joinToString(" ")
        val secondName = second.joinToString(" ")
        if (firstName.isBlank() || secondName.isBlank()) return false
        return first == second || clubAliases.any { firstName in it && secondName in it } ||
            matchesAbbreviation(label, first, second) || matchesAbbreviation(name, second, first)
    }

    private fun matchesAbbreviation(raw: String, short: List<String>, full: List<String>): Boolean {
        val acronym = short.singleOrNull() ?: return false
        if (acronym.length !in 3..5 || !acronym.all { it in 'a'..'z' } ||
            raw.trim() != acronym.uppercase(java.util.Locale.ROOT) || full.size < 2) return false
        // A shared initialism alone does not identify a reserve, youth or women's side.
        if (full.any { it in squadQualifiers || it.any(Char::isDigit) }) return false
        return acronym == full.joinToString("") { it.take(1) } ||
            acronym == full.first() ||
            (full.size == 2 && acronym == full.first().take(1) + full.last())
    }

    private val domesticLeagues = mapOf(
        "laliga" to setOf("laliga", "la liga", "primera division"),
        "bundesliga" to setOf("bundesliga", "1 bundesliga"),
        "ligue 1" to setOf("ligue 1", "ligue 1 mcdonald s"),
        "serie a" to setOf("serie a"),
        "fa cup" to setOf("fa cup", "emirates fa cup"),
    )
    private val genericShortNames = setOf("real", "borussia", "united", "city", "sporting", "racing", "club")
    private val squadQualifiers = setOf("b", "ii", "iii", "women", "femenino", "reserves", "youth")

    private fun shortNameMatches(label: String, name: String): Boolean {
        val word = clubWords(label).singleOrNull() ?: return false
        if (word.length < 4 || word in genericShortNames || !word.all(Char::isLetter)) return false
        val full = clubWords(name)
        if (full.any { it in squadQualifiers || it.any(Char::isDigit) }) return false
        return full.size > 1 && (word == full.first() || word == full.last())
    }

    fun matches(title: String, fixture: FootballMatch, competitors: Set<String> = emptySet()): Boolean {
        val leaguePrefix = normalize(title.substringBefore('.').substringBefore(':')).replace("la liga", "laliga")
        val league = domesticLeagues[leaguePrefix]
        if (league != null && normalize(fixture.competition) !in league) return false
        val teams = footballProgrammeTitle(title).replace(trailingDate, "").split(fixtureSeparator, limit = 2)
        val tags = competitors.toList()
        fun matchesPair(first: String, second: String, shortNames: Boolean, tagged: Boolean = false): Boolean {
            fun side(label: String, name: String) =
                teamMatches(label, name) || (shortNames && shortNameMatches(label, name)) ||
                    (tagged && shortNameMatches(name, label))
            val forward = side(first, fixture.home) && side(second, fixture.away)
            val reverse = side(first, fixture.away) && side(second, fixture.home)
            return forward != reverse
        }
        if (league != null && tags.size == 2 && matchesPair(tags[0], tags[1], shortNames = false, tagged = true)) return true
        if (teams.size != 2) return false
        return matchesPair(teams[0], teams[1], league != null && tags.size != 2)
    }
    fun dates(today: LocalDate,explicit: LocalDate?): List<LocalDate> = explicit?.let(::listOf) ?: (0L..14L).map(today::minusDays)
    fun explicitDate(title: String)=Regex("\\b[0-9]{4}-[0-9]{2}-[0-9]{2}\\b").find(title)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
internal class FootballFailure(val safeMessage: String="Football lineups unavailable. Check your PitchAPI key or retry."): Exception(safeMessage)
