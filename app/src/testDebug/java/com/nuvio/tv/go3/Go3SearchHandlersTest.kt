package com.nuvio.tv.go3

import android.content.Context
import com.nuvio.tv.data.local.*
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.repository.*
import com.nuvio.tv.ui.screens.search.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class Go3SearchHandlersTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val replay=Go3Programme("42","Manchester United vs Roma","https://go3.lt/live_tv/extra,live-17/x,programme-42","17","900",null,Go3Availability.REPLAY,Instant.EPOCH,Instant.EPOCH)
    private class Fixtures(private val event: Go3Programme): Go3Discovery {
        override val sources=Go3CatalogueSource.entries
        val loads=mutableListOf<Pair<Go3CatalogueSource,Boolean>>()
        var stale=false
        override fun cached(source: Go3CatalogueSource)=Go3CatalogueSnapshot(listOf(event,event.copy(programmeId="43",availability=Go3Availability.UPCOMING)),stale)
        override suspend fun load(source: Go3CatalogueSource,refresh: Boolean): Go3CatalogueSnapshot { loads+=source to refresh;return cached(source) }
        override fun find(id: String)=event.takeIf { it.id==id }
        override suspend fun programme(id: String,refresh: Boolean)=requireNotNull(find(id))
    }
    private fun model(source: Go3Discovery): SearchViewModel {
        val layout=mockk<LayoutPreferenceDataStore> {
            every { discoverLocation } returns flowOf(DiscoverLocation.IN_SEARCH)
            every { posterCardWidthDp } returns flowOf(126)
            every { posterCardHeightDp } returns flowOf(189)
            every { posterCardCornerRadiusDp } returns flowOf(12)
            every { posterLabelsEnabled } returns flowOf(true)
            every { catalogAddonNameEnabled } returns flowOf(true)
            every { catalogTypeSuffixEnabled } returns flowOf(true)
            every { hideUnreleasedContent } returns flowOf(false)
        }
        return SearchViewModel(
            addonRepository=mockk { every { getInstalledAddons() } returns flowOf(emptyList()) },
            catalogRepository=mockk(relaxed=true),metaRepository=mockk(relaxed=true),
            discoverSelectionDataStore=mockk(relaxed=true),layoutPreferenceDataStore=layout,
            searchHistoryDataStore=mockk(relaxed=true) { every { recentSearches } returns flowOf(emptyList()) },
            watchProgressRepository=mockk { every { observeWatchedMovieIds() } returns flowOf(emptySet()) },
            watchedSeriesStateHolder=mockk { every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet()) },
            posterOptions=mockk(relaxed=true),serverCatalog=mockk { every { searchAddons } returns flowOf(emptyList()) },
            context=mockk<Context>(relaxed=true),go3Discovery=source)
    }
    @Test fun sportsSourceAndAvailabilityHandlersRetainExactReplayIdentityAndRefresh()=runTest {
        val fixture=Fixtures(replay);val vm=model(fixture);advanceUntilIdle()
        vm.ensureDiscoverLoaded();advanceUntilIdle()
        assertEquals(listOf("go3:live","go3:epl","go3:ucl","go3:uel","go3:uecl","go3:laliga","go3:bundesliga","go3:ligue1","go3:seriea","go3:facup"),vm.uiState.value.discoverCatalogs.map { it.key })
        vm.onEvent(SearchEvent.SelectDiscoverType("sport"));vm.onEvent(SearchEvent.SelectDiscoverCatalog("go3:ucl"));advanceUntilIdle()
        assertEquals("go3:ucl",vm.uiState.value.selectedDiscoverCatalogKey)
        assertEquals(listOf("Upcoming","Replay"),vm.uiState.value.discoverCatalogs.single { it.key=="go3:ucl" }.genres)
        vm.onEvent(SearchEvent.SelectDiscoverGenre("Replay"));advanceUntilIdle()
        assertEquals(listOf(replay.id),vm.uiState.value.discoverResults.map { it.id })
        assertEquals("sport",vm.uiState.value.discoverResults.single().apiType)
        fixture.stale=true;vm.onEvent(SearchEvent.RefreshDiscover);advanceUntilIdle()
        assertTrue(fixture.loads.last().second);assertTrue(vm.uiState.value.go3CatalogueStale)
        assertEquals(replay.id,vm.uiState.value.discoverResults.single().id)
    }
    @Test fun newLeagueSelectionsLoadTheirOwnCatalogue()=runTest {
        val fixture=Fixtures(replay);val vm=model(fixture);advanceUntilIdle()
        vm.ensureDiscoverLoaded();advanceUntilIdle()
        vm.onEvent(SearchEvent.SelectDiscoverType("sport"))
        for(source in Go3CatalogueSource.entries.drop(3)) {
            vm.onEvent(SearchEvent.SelectDiscoverCatalog("go3:${source.key}"));advanceUntilIdle()
            assertEquals(source,fixture.loads.last().first)
            assertTrue(vm.uiState.value.discoverResults.any { it.id==replay.id })
        }
    }

}
