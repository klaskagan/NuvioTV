package com.nuvio.tv.core.deeplink

import com.nuvio.tv.domain.deeplink.AppDeepLink
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoffDeepLinkTest {
    @Test fun parsesDedicatedReceiverSchemeWithoutConflictingWithStockApp() {
        val parameters = "{\"requestId\":\"test\",\"token\":\"test-only\"}"
        val encoded = URLEncoder.encode(parameters, "UTF-8")
        assertEquals(AppDeepLink.Handoff(parameters), DeepLinkParser.parse("nuvio-handoff://handoff?launch=$encoded"))
    }

    @Test fun rejectsOversizedPayloadAndNonHandoffHost() {
        assertNull(DeepLinkParser.parse("nuvio-handoff://handoff?launch=" + "x".repeat(4097)))
        assertNull(DeepLinkParser.parse("nuvio-handoff://movie/tt0133093"))
        assertNull(DeepLinkParser.parse("nuvio-handoff://handoff"))
    }
}
