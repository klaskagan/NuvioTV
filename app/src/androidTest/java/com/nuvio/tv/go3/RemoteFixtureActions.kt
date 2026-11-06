package com.nuvio.tv.go3

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry

// TV touch injection first focuses a card. Exercise the actual D-pad confirm path instead.
internal fun SemanticsNodeInteraction.remoteClick(): SemanticsNodeInteraction {
    performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
    return this
}
