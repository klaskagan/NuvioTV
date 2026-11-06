@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.go3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.ui.screens.settings.SettingsActionRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import java.time.Instant

@HiltViewModel
class Go3SettingsViewModel @Inject constructor(val account: Go3Account): ViewModel()

@Composable
fun Go3SettingsContent(onBack: () -> Unit,viewModel: Go3SettingsViewModel=hiltViewModel()) {
    val account=viewModel.account
    val scope=rememberCoroutineScope()
    var operation by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf(if(account.session()!=null) "Go3 account linked locally." else "Sign in with your existing Go3 subscription.") }
    var code by remember { mutableStateOf<Go3Login?>(null) }
    var profiles by remember { mutableStateOf(emptyList<Go3Profile>()) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun run(block: suspend () -> Unit) {
        operation?.cancel()
        operation=scope.launch {
            busy=true
            try { block() } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { code=null;status=(error as? Go3Failure)?.reason ?: "Go3 action failed. Retry." }
            finally { busy=false }
        }
    }
    BackHandler {
        when { confirm -> confirm=false
            code!=null -> { operation?.cancel();code=null;status="Sign-in cancelled." }
            else -> onBack() }
    }
    // Linking codes are short-lived authentication material. Keep this presentation protected.
    Go3SecureWindow()
    CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Go3")
        Text("Experimental native integration · existing subscription only")
        Text(status)
        code?.let { Text("Open https://go3.lt/subscriber/connect-tv privately and approve this code:");Text(it.code) }
        if(confirm) {
            Go3ResetConfirmation(onCancel={ confirm=false },onConfirm={
                operation?.cancel();account.reset();code=null;profiles=emptyList();confirm=false;status="Local Go3 session reset."
            })
        } else {
            SettingsActionRow(title="Sign in / Open Go3 account",subtitle="Link this TV privately at go3.lt/subscriber/connect-tv",onClick={ run {
                profiles=emptyList();val login=account.api.loginCode();code=login
                while(Instant.now()<login.expires) {
                    delay(7000);val session=account.api.poll(login.code) ?: continue
                    currentCoroutineContext().ensureActive();account.save(session);code=null;profiles=account.api.profiles(session)
                    status="Choose your Go3 viewing profile.";return@run
                }
                code=null;throw Go3Failure("Go3 linking code expired. Retry.")
            } },enabled=!busy)
            SettingsActionRow(title="Choose Go3 viewing profile",subtitle="Use the viewing profile for this local session",onClick={ run {
                profiles=account.api.profiles(account.session() ?: throw Go3Failure("Sign in to Go3 first."))
            } },enabled=!busy)
            profiles.forEach { profile -> Button(onClick={ account.selectProfile(profile.id);profiles=emptyList();status="Go3 profile selected." }) { Text(profile.name) } }
            if(busy || code!=null) Button(onClick={ operation?.cancel();code=null;status="Go3 action cancelled." }) { Text("Cancel") }
            SettingsActionRow(title="Reset local Go3 session",subtitle="Confirmation required; affects only Go3 in NuvioTV",onClick={ confirm=true })
            Button(onClick=onBack) { Text("Back") }
        }
    }
    }
}


/** Shared by the actual settings screen and isolated remote tests; no authentication in this view. */
@Composable
internal fun Go3ResetConfirmation(onCancel: () -> Unit,onConfirm: () -> Unit) {
    BackHandler(onBack=onCancel)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Reset only this app’s local Go3 session? Your Nuvio account stays signed in.")
        Button(onClick=onCancel) { Text("Cancel") }
        Button(onClick=onConfirm) { Text("Confirm local Go3 reset") }
    }
}
