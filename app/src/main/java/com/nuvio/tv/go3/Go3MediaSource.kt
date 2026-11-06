package com.nuvio.tv.go3

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Private ticket material stays inside Media3, never in routes or persisted stream links. */
internal object Go3MediaSource {
    fun factory(onStage: (Go3DrmStage) -> Unit = {}): DefaultMediaSourceFactory {
        val media=OkHttpDataSource.Factory(Go3Transport.mediaClient()).setUserAgent("NuvioTV/1.0")
        val licenses=object : HttpDataSource.Factory {
            private val delegate=OkHttpDataSource.Factory(Go3Transport.restrictedClient()).setUserAgent("NuvioTV/1.0")
            override fun setDefaultRequestProperties(properties: Map<String,String>): HttpDataSource.Factory {
                delegate.setDefaultRequestProperties(properties); return this
            }
            override fun createDataSource(): HttpDataSource {
                val source=delegate.createDataSource()
                return object : HttpDataSource by source {
                    override fun open(dataSpec: DataSpec): Long {
                        if(!Go3ExperimentalApi.officialLicense(dataSpec.uri.toString())) throw java.io.IOException("Unrecognized Go3 license host")
                        return source.open(dataSpec)
                    }
                }
            }
        }
        val provision=OkHttpDataSource.Factory(Go3Transport.mediaClient()).setUserAgent("NuvioTV/1.0")
        val provisioning=DataSource.Factory {
            val source=provision.createDataSource()
            object : DataSource by source {
                override fun open(dataSpec: DataSpec): Long {
                    if(!Go3ExperimentalApi.safeHttps(dataSpec.uri.toString())) throw java.io.IOException("Insecure Widevine provisioning destination")
                    return source.open(dataSpec)
                }
            }
        }
        return DefaultMediaSourceFactory(media).setDrmSessionManagerProvider { item ->
            val drm=item.localConfiguration?.drmConfiguration
            if(drm==null) DrmSessionManager.DRM_UNSUPPORTED else {
                val license=HttpMediaDrmCallback(drm.licenseUri?.toString(),true,licenses)
                drm.licenseRequestHeaders.forEach { (key,value) -> license.setKeyRequestProperty(key,value) }
                DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID,FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(Go3DrmCallback(license,HttpMediaDrmCallback(null,provisioning),onStage))
            }
        }
    }
    fun item(ticket: Go3Ticket): MediaItem = MediaItem.Builder().setUri(ticket.manifest).setMimeType("application/dash+xml")
        .setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(ticket.license)
            .setLicenseRequestHeaders(ticket.headers).build()).build()
    fun create(ticket: Go3Ticket): MediaSource {
        val factory = factory()
        if (ticket.live) factory.setLoadErrorHandlingPolicy(Go3LiveLoadPolicy())
        return factory.createMediaSource(item(ticket))
    }
}


/** A live video segment can appear in the manifest shortly before it reaches the CDN. */
internal class Go3LiveLoadPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        val fallback = super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
        return if (fallback?.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK &&
            shouldWaitForSegment(loadErrorInfo)) null else fallback
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (shouldWaitForSegment(loadErrorInfo)) {
            if (loadErrorInfo.errorCount == 1) 1_000 else 2_000
        } else {
            super.getRetryDelayMsFor(loadErrorInfo)
        }

    private fun shouldWaitForSegment(info: LoadErrorHandlingPolicy.LoadErrorInfo): Boolean =
        info.errorCount in 1..3 &&
            info.mediaLoadData.dataType == C.DATA_TYPE_MEDIA &&
            info.mediaLoadData.trackType == C.TRACK_TYPE_VIDEO &&
            (info.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 404
}
