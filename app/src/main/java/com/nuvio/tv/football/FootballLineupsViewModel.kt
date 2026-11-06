package com.nuvio.tv.football

import com.nuvio.tv.go3.Go3Discovery
import com.nuvio.tv.go3.Go3Programme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class FootballPresentation(val loading: Boolean=false,val lineups: FootballLineups?=null,val message: String?=null)

@HiltViewModel
class FootballLineupsViewModel @Inject constructor(private val keys: FootballKeyStore, private val go3: Go3Discovery): ViewModel() {
    private val session=FootballLineupSession(viewModelScope) { PitchApi(keys.read()) }
    val state=session.state
    fun select(identity: String)=session.select(identity)
    fun open(title: String, programmeId: String?, retry: Boolean = false) =
        session.open(title, retry, programmeId?.let(go3::find))
    fun close()=session.close()
}

internal class FootballLineupSession(private val scope: CoroutineScope,private val provider: () -> FootballProvider) {
    private val mutable=MutableStateFlow(FootballPresentation())
    val state=mutable.asStateFlow()
    private val cache=mutableMapOf<String,FootballLineups>()
    private var job: Job?=null
    private var generation=0L
    private var selected=""
    fun select(identity: String) {
        if(selected==identity) return
        close();selected=identity;cache.clear();mutable.value=FootballPresentation()
    }
    fun open(title: String,retry: Boolean=false, programme: Go3Programme? = null) {
        job?.cancel();val request=++generation
        cache[selected]?.takeUnless { retry }?.let { mutable.value=FootballPresentation(lineups=it);return }
        mutable.value=FootballPresentation(loading=true)
        val identity=selected
        job=scope.launch {
            try {
                val value = resolveLineups(
                    provider(), title,
                    explicitDate = programme?.originalMatchDate ?: FootballMatching.explicitDate(title),
                    competitors = programme?.competitorSlugs.orEmpty(),
                )
                ensureActive()
                if(request==generation && identity==selected) { cache[identity]=value;mutable.value=FootballPresentation(lineups=value) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { if(request==generation && identity==selected) mutable.value=FootballPresentation(message=(error as? FootballFailure)?.safeMessage ?: "Football lineups unavailable. Retry.") }
        }
    }
    fun close() { ++generation;job?.cancel();job=null }
}
