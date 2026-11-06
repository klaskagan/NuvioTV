@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.go3

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.screens.search.*
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class Go3TvScreensTest {
    @get:Rule val compose=createComposeRule()
    private val replay=Go3Programme("42","Fenerbahçe vs Liverpool","https://go3.lt/lt/live_tv/extra,live-17/fenerbahce-liverpool,programme-42","17","900",null,Go3Availability.REPLAY,Instant.parse("2026-11-03T20:00:00Z"),Instant.parse("2026-11-03T22:00:00Z"))
    private val fixture=object: Go3Discovery {
        override val sources=Go3CatalogueSource.entries
        override fun cached(source: Go3CatalogueSource)=Go3CatalogueSnapshot(listOf(replay),false)
        override suspend fun load(source: Go3CatalogueSource,refresh: Boolean)=cached(source)
        override fun find(id: String)=replay
        override suspend fun programme(id: String,refresh: Boolean)=replay
    }
    @Test fun actualDiscoveryCardOpensExactProgrammeAndMenuRefreshHasNoVisibleButton() {
        var selected=""
        var refreshed=false
        val state=SearchUiState(discoverInitialized=true,selectedDiscoverType="sport",selectedDiscoverCatalogKey="go3:ucl",
            discoverCatalogs=Go3CatalogueSource.entries.map { it.discover(listOf("Replay")) },discoverResults=listOf(replay.preview()),go3CatalogueStale=true)
        compose.setContent { NuvioTheme {
            DiscoverSection(uiState=state,posterCardStyle=PosterCardDefaults.Style,focusResults=false,
                firstItemFocusRequester=remember { FocusRequester() },focusedItemIndex=0,shouldRestoreFocusedItem=false,
                onRestoreFocusedItemHandled={},onNavigateToDetail={ id,type,_ -> selected="$type/$id" },onDiscoverItemFocused={},
                onSelectType={},onSelectCatalog={},onSelectGenre={},onLoadMore={},onRefreshDiscover={ refreshed=true })
        } }
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        compose.onNodeWithContentDescription(replay.title).assertIsDisplayed().remoteClick()
        compose.runOnIdle { assertEquals("sport/go3:programme:42",selected) }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MENU)
        compose.runOnIdle { assertTrue(refreshed) }
        compose.onNodeWithText("Go3 Champions League").assertIsDisplayed()
        compose.onAllNodesWithText("Replay",substring=true).assertAny(hasText("Replay",substring=true))
    }
    @Test fun actualDetailWatchHandlerUsesExactReplayWithoutChannelFallback() {
        var watched: Go3Programme? = null
        val ready = CompletableDeferred<Unit>()
        compose.setContent { NuvioTheme {
            Go3DetailContent(replay.id, fixture, {}, { item, _ -> watched = item },
                capabilities = { ready.await(); it })
        } }
        val watch = compose.onNodeWithText("Watch")
        watch.assertIsNotEnabled()
        val pendingCenter = watch.fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithText("Loading…").assertDoesNotExist()
        ready.complete(Unit)
        compose.waitForIdle()
        val readyCenter = watch.assertIsEnabled().fetchSemanticsNode().boundsInRoot.center
        assertEquals(pendingCenter.y, readyCenter.y, 1f)
        compose.onNodeWithText(replay.url).assertDoesNotExist()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        compose.onNodeWithText("Watch").assertIsEnabled().remoteClick()
        compose.runOnIdle { assertEquals("900",watched!!.recordingId);assertEquals(replay.url,watched!!.url) }
    }
    @Test fun upcomingDetailCannotLaunchPlayback() {
        val upcoming=replay.copy(availability=Go3Availability.UPCOMING)
        val source=object: Go3Discovery by fixture {
            override fun find(id: String)=upcoming
            override suspend fun programme(id: String,refresh: Boolean)=upcoming
        }
        var watched=false
        compose.setContent { NuvioTheme { Go3DetailContent(upcoming.id,source,{}, { _,_ -> watched=true }) } }
        compose.onNodeWithText("Upcoming").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(watched) }
    }
    @Test fun localResetRequiresConfirmationAndBackCancelsBeforeAnyReset() {
        var resets=0
        compose.setContent { NuvioTheme {
            var confirming by remember { mutableStateOf(true) }
            if(confirming) Go3ResetConfirmation(onCancel={ confirming=false },onConfirm={ resets++;confirming=false })
            else androidx.tv.material3.Button(onClick={ confirming=true }) { androidx.tv.material3.Text("Reset local Go3 session") }
        } }
        compose.runOnIdle { assertEquals(0,resets) }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Confirm local Go3 reset").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0,resets) }
        compose.onNodeWithText("Reset local Go3 session").remoteClick()
        compose.onNodeWithText("Confirm local Go3 reset").remoteClick()
        compose.runOnIdle { assertEquals(1,resets) }
    }

    @Test fun liveWatchOffersLiveAndBeginningAndRoutesTheChosenMode() {
        val live=replay.copy(availability=Go3Availability.LIVE,startOverAvailable=true)
        val source=object: Go3Discovery by fixture {
            override fun find(id: String)=live
            override suspend fun programme(id: String,refresh: Boolean)=live
        }
        var selected: Go3PlaybackChoice?=null
        compose.setContent { NuvioTheme { Go3DetailContent(live.id,source,{}, { _,mode -> selected=mode }) } }
        compose.onNodeWithText("Watch").assertIsEnabled().remoteClick()
        compose.onNodeWithText("Watch live").assertIsEnabled()
        compose.onNodeWithText("Start from beginning").assertIsEnabled().remoteClick()
        compose.runOnIdle { assertEquals(Go3PlaybackChoice.BEGINNING,selected) }
    }
    @Test fun unavailableStartOverIsDisabledAndBackDismissesChoiceFirst() {
        val live=replay.copy(availability=Go3Availability.LIVE,startOverAvailable=false)
        val source=object: Go3Discovery by fixture {
            override fun find(id: String)=live
            override suspend fun programme(id: String,refresh: Boolean)=live
        }
        var exited=false;var watched=false
        compose.setContent { NuvioTheme { Go3DetailContent(live.id,source,{ exited=true }, { _,_ -> watched=true }) } }
        compose.onNodeWithText("Watch").remoteClick()
        compose.onNodeWithText("Start from beginning").assertIsNotEnabled()
        compose.onNodeWithText("Watch live").assertIsEnabled()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("How would you like to watch?").assertDoesNotExist()
        compose.runOnIdle { assertFalse(exited);assertFalse(watched) }
    }
    @Test fun deployedLineupsToggleIsAbsentForMoviesAndWorksForSports() {
        var type by mutableStateOf("movie")
        var toggled=false
        compose.setContent { NuvioTheme { com.nuvio.tv.football.FootballLineupsToggle(type,{ toggled=true },{}) } }
        compose.onNodeWithContentDescription("Lineups").assertDoesNotExist()
        compose.runOnIdle { type="sport" }
        compose.onNodeWithContentDescription("Lineups").assertIsDisplayed().remoteClick()
        compose.runOnIdle { assertTrue(toggled);type="series" }
        compose.onNodeWithContentDescription("Lineups").assertDoesNotExist()
    }

}
