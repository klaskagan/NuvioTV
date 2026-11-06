@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.football

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.go3.Go3SecureWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class FootballSettingsViewModel @Inject constructor(val keys: FootballKeyStore): ViewModel()

@Composable
fun FootballSettingsContent(onBack: () -> Unit,viewModel: FootballSettingsViewModel=hiltViewModel()) {
    val keys=viewModel.keys
    var configured by remember { mutableStateOf(keys.read().isNotEmpty()) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if(configured) "PitchAPI key configured · ••••••••" else "Configure your free PitchAPI key privately on this TV.") }
    var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    Go3SecureWindow()
    CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Football Lineups · PitchAPI")
        Text(status)
        Text("Enter key (masked)")
        com.nuvio.tv.ui.screens.account.InputField(
            value=draft,onValueChange={ if(it.length<=512 && it.none(Char::isISOControl)) draft=it },
            placeholder="PitchAPI key",isPassword=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={ scope.launch {
            busy=true;status="Validating…"
            try { PitchApi(draft.trim()).matches(LocalDate.now(ZoneOffset.UTC));ensureActive();keys.save(draft);draft="";configured=true;status="PitchAPI key validated and stored locally · ••••••••" }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { status="Could not validate the key. Check it privately or retry." }
            finally { busy=false }
        } },enabled=!busy && draft.isNotBlank()) { Text("Validate and save") }
        Button(onClick={ keys.remove();draft="";configured=false;status="Local PitchAPI key removed." },enabled=!busy && configured) { Text("Remove key") }
        Button(onClick=onBack) { Text("Back") }
    }
    }
}
