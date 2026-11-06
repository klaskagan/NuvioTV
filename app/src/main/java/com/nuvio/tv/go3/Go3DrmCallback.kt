package com.nuvio.tv.go3

import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import java.util.UUID

internal enum class Go3DrmStage { IDLE, PROVISIONING, LICENSE }

/** Framework provisioning and Go3 license exchange have independent HTTP clients/headers. */
internal class Go3DrmCallback(
    private val license: MediaDrmCallback,
    private val provisioning: MediaDrmCallback,
    private val onStage: (Go3DrmStage) -> Unit,
) : MediaDrmCallback {
    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray {
        onStage(Go3DrmStage.PROVISIONING)
        return provisioning.executeProvisionRequest(uuid,request)
    }
    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
        onStage(Go3DrmStage.LICENSE)
        return license.executeKeyRequest(uuid,request)
    }
}
