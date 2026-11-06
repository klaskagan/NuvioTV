package com.nuvio.tv.go3

import android.view.KeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.football.*
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FootballTvLayoutTest {
    @get:Rule val compose=createComposeRule()
    private fun team(side: String): FootballTeam {
        val columns=if(side=="home") listOf(.1f to listOf(.5f),.36f to listOf(.88f,.63f,.38f,.13f),
            .61f to listOf(.79f,.5f,.21f),.87f to listOf(.79f,.5f,.21f))
        else listOf(.1f to listOf(.5f),.29f to listOf(.88f,.63f,.38f,.13f),.49f to listOf(.7f,.3f),
            .68f to listOf(.84f,.5f,.16f),.87f to listOf(.5f))
        val coordinates=columns.flatMap { (x,ys) -> ys.map { x to it } }
        return FootballTeam(side,if(side=="home") "Fenerbahçe" else "Liverpool",if(side=="home") "4-3-3" else "4-2-3-1","Original Coach",
            coordinates.mapIndexed { i,point ->
                val name=when { side=="away" && i==1 -> "Original Away Gómez";side=="away" && i==3 -> "Original Away Muñoz";side=="away" && i==8 -> "Original Away Gravenberch"
                    else -> "Original ${side.replaceFirstChar(Char::uppercase)} Player${i+1}" }
                FootballPlayer(name,"${i+1}",point.first,point.second)
            },
            (12..22).map { FootballPlayer("Original $side Bench$it","$it",null,null) })
    }
    @Test fun fullTwentyTwoPlayerPitchHasSafeBoundsAndRemoteAccessibleDetailsAndRestore() {
        val data=FootballLineups(FootballMatch("m_fixture","Fenerbahçe","Liverpool","UEFA","2026-11-03T20:00:00Z"),listOf(team("home"),team("away")))
        var restored=false
        var controls by mutableStateOf(false)
        compose.setContent { NuvioTheme {
            FootballLineupsPanel(FootballPresentation(lineups=data),{}, { restored=true }, { controls=true },
                playerControlsVisible = controls)
        } }
        compose.onAllNodesWithTag("pitch-player").assertCountEquals(22)
        for(label in listOf("Lineups","Coach · Original Coach")) {
            val pixels=compose.onAllNodesWithText(label).onFirst().captureToImage().toPixelMap()
            var readable=false
            for(y in 0 until pixels.height step 2) for(x in 0 until pixels.width step 2) {
                val pixel=pixels[x,y]
                if(pixel.red>.7f && pixel.green>.7f && pixel.blue>.7f) readable=true
            }
            assertTrue("Readable foreground for $label",readable)
        }

        val pitch=compose.onNodeWithTag("lineups-pitch").fetchSemanticsNode().boundsInRoot
        assertEquals(105f/68f,pitch.width/pitch.height,.015f)
        val markers=compose.onAllNodesWithTag("pitch-player").fetchSemanticsNodes().map { it.boundsInRoot }
        markers.forEach { assertTrue(it.left>=pitch.left-1 && it.right<=pitch.right+1 && it.top>=pitch.top-1 && it.bottom<=pitch.bottom+1) }
        for(i in markers.indices) for(j in i+1 until markers.size) assertFalse("Overlapping player markers",markers[i].overlaps(markers[j]))
        val rail=compose.onNodeWithTag("lineups-video-bench-rail").fetchSemanticsNode().boundsInRoot
        assertTrue(rail.left>pitch.right)
        compose.onNodeWithText("Restore video").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithText("Player controls").assertIsFocused().remoteClick()
        compose.runOnIdle { assertTrue(controls); controls = false }
        compose.onNodeWithText("Player controls").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("Restore video").assertIsFocused()
        compose.onNodeWithContentDescription("1 Original Home Player1").remoteClick()
        compose.onNodeWithText("Close player details").assertIsDisplayed().remoteClick()
        compose.onNodeWithText("Restore video").remoteClick()
        compose.runOnIdle { assertTrue(restored) }
        val output=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("go3-fixtures")!!,"lineups-tv-fixture.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val allText=compose.onAllNodes(SemanticsMatcher("all fixture nodes") { true },useUnmergedTree=true)
            .fetchSemanticsNodes().flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }.joinToString(" ")
        for(forbidden in listOf("SPOILER","rating","substitution","goals","score")) assertFalse("Forbidden presentation field: $forbidden",allText.contains(forbidden,true))
    }
    @Test fun fullTeamsRenderAtThreeTvLogicalViewports() {
        var logicalWidth by mutableFloatStateOf(960f)
        val data=FootballLineups(FootballMatch("m_fixture","Fenerbahçe","Liverpool","UEFA","2026-11-03T20:00:00Z"),listOf(team("home"),team("away")))
        compose.setContent {
            val pixels=LocalConfiguration.current.screenWidthDp*LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(pixels/logicalWidth,1f)) {
                NuvioTheme { FootballLineupsPanel(FootballPresentation(lineups=data),{}, {}, {}) }
            }
        }
        for(width in listOf(960f,1280f,1920f)) {
            compose.runOnIdle { logicalWidth=width }
            compose.waitForIdle()
            compose.onAllNodesWithTag("pitch-player").assertCountEquals(22)
            val bounds=compose.onNodeWithTag("lineups-pitch").fetchSemanticsNode().boundsInRoot
            assertEquals(105f/68f,bounds.width/bounds.height,.015f)
            val markers=compose.onAllNodesWithTag("pitch-player").fetchSemanticsNodes().map { it.boundsInRoot }
            for(i in markers.indices) for(j in i+1 until markers.size) assertFalse(markers[i].overlaps(markers[j]))
            val image=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("go3-fixtures")!!,"lineups-tv-fixture-${width.toInt()}.png")
            image.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }

    @Test fun shortSurnamesAndBothBenchesStayOnOneLineWithAlignedRows() {
        val data=FootballLineups(FootballMatch("m_fixture","Fenerbahçe","Liverpool","UEFA","2026-11-04T17:45:00Z"),listOf(team("home"),team("away")))
        compose.setContent { NuvioTheme { FootballLineupsPanel(FootballPresentation(lineups=data),{}, {}, {}) } }
        for(label in listOf("Gómez","Muñoz","12 O. Bench12")) {
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onAllNodesWithText(label,useUnmergedTree=true).onFirst().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Single line for $label",1,layouts.single().lineCount)
            assertFalse("No clipped letters for $label",layouts.single().didOverflowWidth)
        }
        val longer=mutableListOf<TextLayoutResult>()
        compose.onNode(SemanticsMatcher("Gravenberch across one or two lines") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                it.text.filterNot(Char::isWhitespace) == "Gravenberch"
            }
        }, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(longer) }
        assertTrue("Longer names remain visible",longer.single().lineCount<=2)
        assertFalse("Longer names are not clipped",longer.single().hasVisualOverflow)
        compose.onAllNodesWithText("Original bench · 11").assertCountEquals(2)
        val rows=compose.onAllNodesWithTag("lineups-player-row").fetchSemanticsNodes()
        assertEquals(22,rows.size)
        assertEquals("Both original benches use equal row heights",1,rows.map { it.size.height }.distinct().size)
        val image=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("go3-fixtures")!!,"lineups-tv-name-regression.png")
        image.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }

}
