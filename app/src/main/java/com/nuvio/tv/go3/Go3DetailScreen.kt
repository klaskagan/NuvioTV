@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.go3

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.screens.detail.PlayButton
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

@HiltViewModel
class Go3DetailViewModel @Inject constructor(val discovery: Go3Discovery,val account: Go3Account): ViewModel()

@Composable
fun Go3DetailScreen(id: String,onBack: () -> Unit,onWatch: (Go3Programme,Go3PlaybackChoice) -> Unit,viewModel: Go3DetailViewModel=hiltViewModel()) {
    Go3DetailContent(id,viewModel.discovery,onBack,onWatch,capabilities={ viewModel.account.playbackProgramme(it) })
}

/** Injectable content also exercises the actual TV handlers without account/preferences in tests. */
@Composable
internal fun Go3DetailContent(id: String,discovery: Go3Discovery,onBack: () -> Unit,
    onWatch: (Go3Programme,Go3PlaybackChoice) -> Unit,capabilities: suspend (Go3Programme) -> Go3Programme = { it }) {
    var programme by remember(id) { mutableStateOf(discovery.find(id)) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var retry by remember(id) { mutableIntStateOf(0) }
    var choosing by remember(id) { mutableStateOf<Go3Programme?>(null) }
    val retryFocus = remember { FocusRequester() }
    BackHandler(enabled=choosing==null,onBack=onBack)
    LaunchedEffect(error, loading) {
        if (error != null && !loading) {
            withFrameNanos { }
            withFrameNanos { }
            retryFocus.requestFocus()
        }
    }
    LaunchedEffect(id,retry) {
        loading=true;error=null
        try { programme=capabilities(discovery.programme(id,true)) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) { error=(failure as? Go3Failure)?.reason ?: "Go3 programme could not load. Retry." }
        finally { loading=false }
    }
    CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color(0xffeeeeee)) {
        choosing?.let { item -> Go3LivePlaybackChoices(item,onDismiss={ choosing=null },onChoice={ mode ->
            choosing=null;onWatch(item,mode)
        }) }
        Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
            if(event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_MENU) {
                if(event.nativeKeyEvent.action==KeyEvent.ACTION_UP && !loading) retry++
                true
            } else false
        }) {
            AsyncImage(model=programme?.artwork,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black,Color.Black.copy(alpha=.3f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(NuvioTheme.spacing.xxxl).fillMaxWidth(.7f),
                verticalArrangement=Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                Text("Go3",style=MaterialTheme.typography.titleMedium)
                Text(programme?.title ?: "Go3 programme",style=MaterialTheme.typography.headlineLarge)
                programme?.let { item ->
                    Text("${item.availability.label} · ${item.schedule()}", style = MaterialTheme.typography.bodyLarge)
                }
                val failure = error
                if (failure != null) {
                    Text(failure)
                    Button(onClick = { retry++ }, modifier = Modifier.focusRequester(retryFocus)) { Text("Retry") }
                } else {
                    val item = programme
                    PlayButton(
                        text = if (item?.availability == Go3Availability.UPCOMING) "Upcoming" else "Watch",
                        enabled = item?.playable == true && !loading,
                        onClick = {
                            if (item != null) {
                                if (item.availability == Go3Availability.LIVE) choosing = item
                                else onWatch(item, Go3PlaybackChoice.BEGINNING)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun Go3LivePlaybackChoices(programme: Go3Programme,onDismiss: () -> Unit,onChoice: (Go3PlaybackChoice) -> Unit) {
    val liveFocus=remember { FocusRequester() }
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.background(Color(0xff202020)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("How would you like to watch?",style=MaterialTheme.typography.headlineSmall)
            Button(onClick={ onChoice(Go3PlaybackChoice.LIVE) },modifier=Modifier.focusRequester(liveFocus)) { Text("Watch live") }
            Button(onClick={ onChoice(Go3PlaybackChoice.BEGINNING) },enabled=programme.startOverAvailable && programme.since!=null) {
                Text("Start from beginning")
            }
            if(!programme.startOverAvailable) Text("Start from beginning is unavailable for this event with the current Go3 session.")
            Button(onClick=onDismiss) { Text("Cancel") }
        }
        LaunchedEffect(Unit) { withFrameNanos { };withFrameNanos { };runCatching { liveFocus.requestFocus() } }
    }
}
