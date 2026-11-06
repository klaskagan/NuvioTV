package com.nuvio.tv.go3

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Keep account/license transport separate from credential-free CDN transport. */
internal object Go3Transport {
    fun restrictedClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(30,TimeUnit.SECONDS).build()

    // Follow normal CDN redirects within HTTPS, without API headers or account cookies.
    fun mediaClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(false)
        .callTimeout(30,TimeUnit.SECONDS).build()
}
