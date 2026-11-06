package com.nuvio.tv.football

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.player.ControlButton

internal fun supportsFootballLineups(contentType: String?): Boolean =
    contentType?.lowercase(java.util.Locale.ROOT) in setOf("sport","sports")

@Composable
internal fun FootballLineupsToggle(
    contentType: String?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    upFocusRequester: FocusRequester? = null,
    onDownKey: (() -> Unit)? = null,
) {
    if (supportsFootballLineups(contentType)) {
        ControlButton(
            icon = ImageVector.vectorResource(R.drawable.ic_player_formation),
            contentDescription = "Lineups",
            onClick = onClick,
            onFocused = onFocused,
            upFocusRequester = upFocusRequester,
            onDownKey = onDownKey,
        )
    }
}
