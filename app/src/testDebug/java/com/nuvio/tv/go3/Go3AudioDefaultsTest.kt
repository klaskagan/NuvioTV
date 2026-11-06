package com.nuvio.tv.go3

import com.nuvio.tv.ui.screens.player.resolvePreferredAudioLanguages
import org.junit.Assert.*
import org.junit.Test

class Go3AudioDefaultsTest {
    @Test fun go3AlwaysStartsWithLithuanianIndependentOfDeviceAndGlobalDefaults() {
        for(preference in listOf("default","device","original","en","et","ru")) {
            assertEquals(listOf("lt","lit"),resolvePreferredAudioLanguages(preference,"et",listOf("et","en"),"et",true))
        }
    }
    @Test fun ordinaryPlayerLanguagePreferenceKeepsExistingBehavior() {
        assertEquals(listOf("et","en"),resolvePreferredAudioLanguages("et","en",listOf("lt"),null,false))
    }
}
