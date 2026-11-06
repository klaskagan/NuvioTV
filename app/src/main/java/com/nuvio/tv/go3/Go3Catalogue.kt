package com.nuvio.tv.go3

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.screens.search.DiscoverCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class Go3Availability(val label: String) { LIVE("Live"), UPCOMING("Upcoming"), REPLAY("Replay"), LISTED("Listed") }
enum class Go3CatalogueSource(val key: String,val title: String,val officialUrl: String,val category: String?) {
    LIVE("live","Go3 Live Events","https://go3.lt/sports/sportas-tiesiogiai,section-7998051",null),
    EPL("epl","Go3 Premier League","https://go3.lt/categories/premierleague,category-6886315","6886315"),
    UCL("ucl","Go3 Champions League","https://go3.lt/categories/uefachampionsleague,category-6886205","6886205"),
    UEL("uel","Go3 Europa League","https://go3.lt/categories/uefaeuropaleague,category-6886318","6886318"),
    UECL("uecl","Go3 Conference League","https://go3.lt/categories/uefaeuropaconferenceleague,category-6886320","6886320"),
    LALIGA("laliga","Go3 La Liga","https://go3.lt/categories/football-laliga,category-8272112","8272112"),
    BUNDESLIGA("bundesliga","Go3 Bundesliga","https://go3.lt/categories/football-bundesliga,category-7934060","7934060"),
    LIGUE1("ligue1","Go3 Ligue 1","https://go3.lt/categories/football-ligue-1,category-7498236","7498236"),
    SERIEA("seriea","Go3 Serie A","https://go3.lt/categories/football-serie-a,category-7495922","7495922"),
    FACUP("facup","Go3 FA Cup","https://go3.lt/categories/emiratesfacup,category-6886370","6886370");
    fun discover(filters: List<String> = emptyList()) = DiscoverCatalog("go3:$key","builtin.go3","Go3","",key,title,"sport",filters,false,100)
    companion object { fun fromKey(key: String) = entries.firstOrNull { "go3:${it.key}"==key } }
}
private val footballCompetitionPrefix = Regex(
    """^(?:UEFA (?:Champions|Europa(?: Conference)?|Conference) League|UCL|(?:English )?Premier League|La\s*Liga|Bundesliga|Bundeslyga|Ligue 1|Serie A|Italijos lyga: Serija A|(?:Emirates )?FA (?:Cup|Taurė))\s*(?:[.:]\s*|[-–—]\s+)""",
    RegexOption.IGNORE_CASE,
)
internal fun footballProgrammeTitle(title: String): String =
    title.replace(footballCompetitionPrefix, "").ifBlank { title }

data class Go3Programme(
    val programmeId: String,
    val title: String,
    val url: String,
    val channelId: String,
    val recordingId: String?,
    val artwork: String?,
    val availability: Go3Availability,
    val since: Instant?,
    val until: Instant?,
    val originalMatchDate: LocalDate? = null,
    val startOverAvailable: Boolean = false,
    val competitorSlugs: Set<String> = emptySet(),
) {
    val id get()="go3:programme:$programmeId"
    val cardTitle get()=footballProgrammeTitle(title)
    val playable get()=availability==Go3Availability.LIVE || availability==Go3Availability.REPLAY
    fun schedule(zone: ZoneId=ZoneId.systemDefault(),locale: Locale=Locale.getDefault()): String = since?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).withLocale(locale).withZone(zone).format(it)
    }.orEmpty()
    fun preview(stale: Boolean=false) = MetaPreview(
        id=id,type=ContentType.UNKNOWN,rawType="sport",name=cardTitle,poster=artwork,posterShape=PosterShape.LANDSCAPE,
        background=artwork,logo=null,description=schedule(),releaseInfo=schedule(),imdbRating=null,genres=emptyList(),
        status=availability.name.lowercase(),landscapePoster=artwork,sourceAddonBaseUrl="")
}

/** Programme cards only. Neither signed playback URLs nor channel/footer identities cross this boundary. */
internal object Go3CatalogueDecoder {
    private val programmePath=Regex("/(?:lt/)?(?:watch|live_tv)/[^/]+,(?:live|programme|vod)-[0-9]+(?:/[^/]+,programme-[0-9]+)?")
    private val competitorSlug = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val artKeys=setOf("dsth","dstw","srcmode","quality","type","srcx","srcy","srcw","srch")
    fun officialProgrammeUrl(raw: String): String? {
        val u=raw.toHttpUrlOrNull() ?: return null
        if(!u.isHttps || u.host!="go3.lt" || u.port!=443 || u.username.isNotEmpty() || u.password.isNotEmpty() ||
            !programmePath.matches(u.encodedPath) || !Regex(",programme-[0-9]+").containsMatchIn(u.encodedPath)) return null
        return u.newBuilder().query(null).fragment(null).build().toString()
    }
    fun artwork(raw: String): String? {
        val u=(if(raw.startsWith("//")) "https:$raw" else raw).toHttpUrlOrNull() ?: return null
        if(!u.isHttps || u.host!="static3.go3.tv" || u.port!=443 || u.username.isNotEmpty() || u.password.isNotEmpty() ||
            !u.encodedPath.startsWith("/scale/go3/") || u.queryParameterNames.any { it !in artKeys } || raw.contains("no-image")) return null
        return u.newBuilder().fragment(null).build().toString()
    }
    fun programme(item: JsonObject, now: Instant): Go3Programme? {
        if(Go3Protocol.text(item,"type")!="PROGRAMME") return null
        val id=Go3Protocol.text(item,"id"); val slug=Go3Protocol.text(item,"slug")
        val live=Go3Protocol.objectValue(item,"live")
        val channel=Go3Protocol.text(live,"id"); val channelSlug=Go3Protocol.text(live,"slug")
        if(!id.matches(Regex("[0-9]+")) || !channel.matches(Regex("[0-9]+")) ||
            slug.isBlank() || channelSlug.isBlank() || '/' in slug || '/' in channelSlug) return null
        val provided=Go3Protocol.text(item,"canonicalUrl").ifBlank { Go3Protocol.text(item,"url") }
        val url=officialProgrammeUrl(provided.ifBlank { "https://go3.lt/live_tv/$channelSlug,live-$channel/$slug,programme-$id" }) ?: return null
        // A supplied programme destination must still identify this programme.
        if(Regex(",programme-([0-9]+)").find(url)?.groupValues?.get(1)!=id) return null
        val title=Go3Protocol.safeTitle(Go3Protocol.text(item,"title")).trim()
        if(title.isBlank()) return null
        fun instant(key: String)=runCatching { Instant.parse(Go3Protocol.text(item,key)) }.getOrNull()
        val since=instant("since"); val till=instant("till"); val catchup=instant("catchupTill")
        val recordingId=Go3Protocol.text(item,"programRecordingId").takeIf { it.matches(Regex("[0-9]+")) }
        val state=when {
            since!=null && since>now -> Go3Availability.UPCOMING
            since!=null && till!=null && since<=now && now<till -> Go3Availability.LIVE
            till!=null && till<=now && recordingId!=null && catchup!=null && now<catchup -> Go3Availability.REPLAY
            else -> Go3Availability.LISTED
        }
        val images=Go3Protocol.objectValue(item,"images")
        val art=Go3Protocol.array(images,"16x9").asSequence().mapNotNull { it as? JsonObject }
            .flatMap { sequenceOf(Go3Protocol.text(it,"mainUrl"),Go3Protocol.text(it,"miniUrl")) }.mapNotNull(::artwork).firstOrNull()
        val date=Regex("\\b[0-9]{4}-[0-9]{2}-[0-9]{2}\\b").find(title)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val competitors = Go3Protocol.array(item, "categories").asSequence().filterIsInstance<JsonObject>()
            .filter { Go3Protocol.text(it, "level") == "COMPETITOR" }
            .map { Go3Protocol.text(it, "slug") }
            .filter { competitorSlug.matches(it) }
            .toSet()
        return Go3Programme(id,title,url,channel,recordingId,art,state,since,till,date,
            (item["startoverAvailable"] as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull==true }==true, competitors)
    }
    fun decode(root: JsonElement,now: Instant): List<Go3Programme> {
        val rows=when(root) {
            is JsonArray -> root
            is JsonObject -> (root["items"] as? JsonArray) ?: (root["elements"] as? JsonArray) ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val result=linkedMapOf<String,Go3Programme>()
        for(row in rows) {
            val o=row as? JsonObject ?: continue
            val item=(o["item"] as? JsonObject) ?: o
            val event=programme(item,now) ?: continue
            val previous=result[event.id]
            result[event.id]=if(event.artwork==null && previous?.artwork!=null) event.copy(artwork=previous.artwork) else event
        }
        return result.values.toList()
    }
}

data class Go3CatalogueSnapshot(val programmes: List<Go3Programme>,val stale: Boolean) {
    val filters get()=listOf(Go3Availability.LIVE,Go3Availability.UPCOMING,Go3Availability.REPLAY)
        .filter { state -> programmes.any { it.availability==state } }.map { it.label }
    fun filtered(filter: String?) = programmes.filter { filter==null || it.availability.label.equals(filter,true) }
        .sortedBy { it.availability.ordinal }
}
internal class Go3CatalogueCache(private val clock: () -> Long = { System.nanoTime()/1_000_000 }) {
    private val lock=Mutex()
    private val values=java.util.concurrent.ConcurrentHashMap<Go3CatalogueSource,Pair<Long,Go3CatalogueSnapshot>>()
    fun cached(source: Go3CatalogueSource): Go3CatalogueSnapshot? = values[source]?.let { (at,snapshot) ->
        snapshot.copy(stale=snapshot.stale || clock()-at>=120_000) }
    fun find(id: String): Go3Programme? = values.values.sortedByDescending { it.first }.asSequence()
        .flatMap { it.second.programmes.asSequence() }.firstOrNull { it.id==id }
    suspend fun load(source: Go3CatalogueSource,force: Boolean=false,loader: suspend () -> List<Go3Programme>): Go3CatalogueSnapshot = lock.withLock {
        val old=values[source]
        if(!force && old!=null && clock()-old.first<120_000 && !old.second.stale) return@withLock old.second
        try {
            val snapshot=Go3CatalogueSnapshot(loader(),false)
            values[source]=clock() to snapshot
            snapshot
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) {
            if(old==null) throw Go3Failure("Go3 listings could not refresh. Retry.")
            val stale=old.second.copy(stale=true); values[source]=old.first to stale; stale
        }
    }
}
