package com.nuvio.tv.go3

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Go3LiveLoadPolicyTest {
    private val policy = Go3LiveLoadPolicy()
    private val tracks = LoadErrorHandlingPolicy.FallbackOptions(1, 0, 3, 0)

    private fun failure(
        count: Int,
        trackType: Int = C.TRACK_TYPE_VIDEO,
        dataType: Int = C.DATA_TYPE_MEDIA,
        status: Int = 404,
    ): LoadErrorHandlingPolicy.LoadErrorInfo {
        val spec = mockk<DataSpec>()
        val event = LoadEventInfo(1, spec, mockk<Uri>(), emptyMap(), 0, 0, 0)
        val media = MediaLoadData(dataType, trackType, null, C.SELECTION_REASON_ADAPTIVE, null, 0, 4_000)
        val error = HttpDataSource.InvalidResponseCodeException(status, null, null, emptyMap(), spec, byteArrayOf())
        return LoadErrorHandlingPolicy.LoadErrorInfo(event, media, error, count)
    }

    @Test fun liveVideoWaitsUpToFiveSecondsBeforeExcludingTheTrack() {
        val delays = (1..3).map { count ->
            val error = failure(count)
            assertNull(policy.getFallbackSelectionFor(tracks, error))
            policy.getRetryDelayMsFor(error)
        }
        assertEquals(listOf(1_000L, 2_000L, 2_000L), delays)
        assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
            policy.getFallbackSelectionFor(tracks, failure(count = 4))?.type)
    }

    @Test fun alternateCdnAndOtherErrorsUseTheirDefaultFallback() {
        val multipleLocations = LoadErrorHandlingPolicy.FallbackOptions(2, 0, 3, 0)
        assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION,
            policy.getFallbackSelectionFor(multipleLocations, failure(count = 1))?.type)
        for (error in listOf(
            failure(count = 1, trackType = C.TRACK_TYPE_AUDIO),
            failure(count = 1, dataType = C.DATA_TYPE_MEDIA_INITIALIZATION),
            failure(count = 1, status = 503),
        )) {
            assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
                policy.getFallbackSelectionFor(tracks, error)?.type)
        }
    }
}
