@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.football

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextGeometricTransform
import com.nuvio.tv.ui.components.AutoResizeText

/** Shares the existing player's video surface. Only bounds change when this panel opens. */
@Composable
fun FootballLineupsPanel(state: FootballPresentation,onRetry: () -> Unit,onRestore: () -> Unit,onPlayerControls: () -> Unit,modifier: Modifier=Modifier, playerControlsVisible: Boolean = false) {
    var selected by remember { mutableStateOf<FootballPlayer?>(null) }
    val restoreFocus=remember { FocusRequester() }
    val playerControlsFocus = remember { FocusRequester() }
    var returnToPlayerControls by remember { mutableStateOf(false) }
    LaunchedEffect(playerControlsVisible) {
        if (!playerControlsVisible) {
            withFrameNanos { }
            withFrameNanos { }
            val target = if (returnToPlayerControls) playerControlsFocus else restoreFocus
            target.requestFocus()
            returnToPlayerControls = false
        }
    }
    CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color(0xffeeeeee)) {
    BackHandler(enabled=selected!=null) { selected=null }
    selected?.let { player -> Dialog(onDismissRequest={ selected=null }) {
        Column(Modifier.background(Color(0xff242424)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("${player.number} ${player.name}",fontSize=22.sp)
            Button(onClick={ selected=null }) { Text("Close player details") }
        }
    } }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xff121212)).padding(24.dp)) {
        val videoWidth=maxWidth*.38f
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Lineups", fontSize = 22.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            returnToPlayerControls = true
                            onPlayerControls()
                        },
                        modifier = Modifier.focusRequester(playerControlsFocus),
                    ) { Text("Player controls") }
                    Button(onClick=onRestore,modifier=Modifier.focusRequester(restoreFocus)) { Text("Restore video") }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.fillMaxWidth(.58f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    when {
                        state.loading -> Text("Finding the original confirmed lineups…")
                        state.lineups==null -> Text(state.message ?: "Original confirmed lineups unavailable.")
                        else -> HorizontalPitch(state.lineups.teams, onPlayer = { selected = it })
                    }
                    if (!state.loading && (state.lineups?.teams?.size ?: 0) < 2) {
                        Button(onClick=onRetry) { Text("Retry") }
                    }
                }
                Column(Modifier.width(videoWidth).fillMaxHeight().testTag("lineups-video-bench-rail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    // Same width and top edge as the existing compact video surface.
                    Spacer(Modifier.height(videoWidth*9f/16f+12.dp))
                    Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        state.lineups?.let { lineups ->
                            for ((side, name) in listOf("home" to lineups.match.home, "away" to lineups.match.away)) {
                                val team = lineups.teams.firstOrNull { it.side == side }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(team?.name?.ifBlank { name } ?: name, fontSize = 16.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (team == null) {
                                        Text("Confirmed bench unavailable", fontSize = 14.sp)
                                    } else {
                                        Text("Coach · ${team.coach.ifBlank { "Unavailable" }}", fontSize = 14.sp,
                                            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("Original bench · ${team.bench.size}", fontSize = 16.sp, maxLines = 1)
                                        team.bench.forEach { PlayerRow(it, onPlayer = { selected = it }) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PlayerRow(player: FootballPlayer,onPlayer: () -> Unit) {
    Button(onClick=onPlayer,contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp),
        modifier=Modifier.fillMaxWidth().height(44.dp).testTag("lineups-player-row")
            .semantics { contentDescription="${player.number} ${player.name}" }) {
        AutoResizeText("${player.number} ${player.compactName}",color=LocalContentColor.current,
            modifier=Modifier.fillMaxWidth(),style=LocalTextStyle.current.copy(fontSize=14.sp,lineHeight=18.sp),minFontSize=11.sp)
    }
}

@Composable
internal fun HorizontalPitch(teams: List<FootballTeam>,onPlayer: (FootballPlayer) -> Unit) {
    val fallback=remember(teams) { mutableStateMapOf<String,List<FootballPlayer>>() }
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("home","away").forEach { side ->
                val team=teams.firstOrNull { it.side==side }
                Text(team?.let { "${it.name} · ${it.formation}" } ?: "Confirmed lineup unavailable",fontSize=13.sp,modifier=Modifier.weight(1f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(105f/68f).testTag("lineups-pitch").background(Color(0xff214c32))) {
            Canvas(Modifier.fillMaxSize()) {
                val line=Color(0xff89958b);val stroke=Stroke(width=2.dp.toPx())
                drawRect(line,style=stroke)
                drawLine(line,Offset(size.width/2,0f),Offset(size.width/2,size.height),strokeWidth=2.dp.toPx())
                // Radius is based on metres across the horizontal pitch; width and height are equal.
                drawCircle(line,radius=size.width*9.15f/105f,center=center,style=stroke)
                val boxWidth=size.width*16.5f/105f;val boxHeight=size.height*40.32f/68f
                drawRect(line,Offset(0f,(size.height-boxHeight)/2),Size(boxWidth,boxHeight),style=stroke)
                drawRect(line,Offset(size.width-boxWidth,(size.height-boxHeight)/2),Size(boxWidth,boxHeight),style=stroke)
            }
            val half=maxWidth/2
            teams.forEach { team ->
                val away=team.side=="away"
                val placement=pitchPlacement(team.starters,away,half.value,maxHeight.value)
                LaunchedEffect(placement.unpositioned) { fallback[team.side]=placement.unpositioned }
                placement.markers.forEach { marker ->
                    val player=marker.player
                    Button(onClick={ onPlayer(player) },contentPadding=PaddingValues(1.dp),
                        shape=androidx.tv.material3.ButtonDefaults.shape(shape=androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                        modifier=Modifier.offset((if(away) half else 0.dp)+marker.x.dp,marker.y.dp).width(placement.markerWidth.dp).height(placement.markerHeight.dp)
                            .testTag("pitch-player").semantics { contentDescription="${player.number} ${player.name}" }) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(player.number,fontSize=13.sp,lineHeight=15.sp)
                            PitchName(player.surname,placement.markerWidth.dp)
                        }
                    }
                }
            }
        }
        teams.forEach { team ->
            val missing=fallback[team.side].orEmpty()
            if(missing.isNotEmpty()) {
                Text("${team.name} · Original starters without usable pitch coordinates",fontSize=14.sp)
                missing.forEach { player -> PlayerRow(player,onPlayer={ onPlayer(player) }) }
            }
        }
    }
}


@Composable
private fun PitchName(name: String,width: Dp) {
    val measurer=rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(
        fontSize = 12.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center,
        color = LocalContentColor.current,
    )
    val oneLineMinimum = 9.sp
    val available = with(LocalDensity.current) { width.roundToPx() - 2 * 1.dp.roundToPx() }
    fun fitsOnOneLine(text: String, candidate: TextStyle): Boolean = measurer.measure(
        text, style = candidate, softWrap = false, maxLines = 1,
    ).size.width <= available - 1
    fun fitsAtMinimum(text: String, candidate: TextStyle) =
        fitsOnOneLine(text, candidate.copy(fontSize = oneLineMinimum))
    val narrowStyle = style.copy(textGeometricTransform = TextGeometricTransform(scaleX = 0.84f))
    val oneLineStyle = when {
        fitsAtMinimum(name, style) -> style
        fitsAtMinimum(name, narrowStyle) -> narrowStyle
        else -> null
    }
    val split = if (oneLineStyle != null) null else {
        val boundary = java.text.BreakIterator.getCharacterInstance()
        boundary.setText(name)
        val splits = generateSequence(boundary.first()) { boundary.next().takeIf { it != java.text.BreakIterator.DONE } }
            .filter { it in 1 until name.length }
            .sortedWith(compareBy<Int> { !name[it - 1].isWhitespace() && !name[it].isWhitespace() }
                .thenBy { kotlin.math.abs(name.length / 2 - it) })
        splits.firstOrNull { index ->
            fitsAtMinimum(name.substring(0, index).trimEnd(), narrowStyle) &&
                fitsAtMinimum(name.substring(index).trimStart(), narrowStyle)
        }
    }
    val label = split?.let { name.substring(0, it).trimEnd() + "\n" + name.substring(it).trimStart() } ?: name

    val labelStyle = oneLineStyle ?: narrowStyle.copy(lineHeight = 11.sp)
    val lines = label.lines()
    val fontSize = (12 downTo 9).firstOrNull { size ->
        lines.all { fitsOnOneLine(it, labelStyle.copy(fontSize = size.sp)) }
    }?.sp ?: oneLineMinimum

    BasicText(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        style = labelStyle.copy(fontSize = fontSize),
        maxLines = if (oneLineStyle != null) 1 else 2,
        softWrap = oneLineStyle == null,
        overflow = TextOverflow.Ellipsis,
    )
}
