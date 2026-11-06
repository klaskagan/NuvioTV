package com.nuvio.tv.go3

import androidx.media3.common.C
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class Go3DrmCallbackTest {
    private class Endpoint : MediaDrmCallback {
        var provisions=0; var licenses=0
        var received: Any?=null
        override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray {
            provisions++; received=request; return byteArrayOf(1)
        }
        override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
            licenses++; received=request; return byteArrayOf(2)
        }
    }
    @Test fun `framework provisioning never goes through the Go3 account license transport`() {
        val license=Endpoint(); val provisioning=Endpoint(); val stages=mutableListOf<Go3DrmStage>()
        val callback=Go3DrmCallback(license,provisioning) { stages.add(it) }
        val request=mockk<ExoMediaDrm.ProvisionRequest>()
        assertArrayEquals(byteArrayOf(1),callback.executeProvisionRequest(C.WIDEVINE_UUID,request))
        assertEquals(0,license.provisions); assertEquals(0,license.licenses)
        assertEquals(1,provisioning.provisions); assertSame(request,provisioning.received)
        assertEquals(listOf(Go3DrmStage.PROVISIONING),stages)
    }
    @Test fun `Go3 license exchange never goes through credential free provisioning transport`() {
        val license=Endpoint(); val provisioning=Endpoint(); val stages=mutableListOf<Go3DrmStage>()
        val callback=Go3DrmCallback(license,provisioning) { stages.add(it) }
        val request=mockk<ExoMediaDrm.KeyRequest>()
        assertArrayEquals(byteArrayOf(2),callback.executeKeyRequest(C.WIDEVINE_UUID,request))
        assertEquals(1,license.licenses); assertSame(request,license.received)
        assertEquals(0,provisioning.provisions); assertEquals(0,provisioning.licenses)
        assertEquals(listOf(Go3DrmStage.LICENSE),stages)
    }
}
