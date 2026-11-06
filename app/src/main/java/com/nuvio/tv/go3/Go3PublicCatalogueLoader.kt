package com.nuvio.tv.go3

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Read-only official metadata. No session token, account cookie, or subscription mutation. */
internal class Go3PublicCatalogueLoader(
    private val client: OkHttpClient=Go3Transport.restrictedClient(),
    private val base: HttpUrl="https://go3.lt/api/".toHttpUrl(),
    private val now: () -> Instant=Instant::now,
) {
    private suspend fun read(path: String,params: List<Pair<String,String>> = emptyList()): JsonElement {
        val url=requireNotNull(base.resolve(path)).newBuilder()
            .addQueryParameter("platform","ANDROID_TV").addQueryParameter("lang","LT").addQueryParameter("tenant","OM_LT")
            .apply { params.forEach { (name,value) -> addQueryParameter(name,value) } }.build()
        val request=Request.Builder().url(url).header("User-Agent","NuvioTV/1.0").header("Accept","application/json").get().build()
        return suspendCancellableCoroutine { continuation ->
            val call=client.newCall(request); continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call,error: IOException) {
                    if(continuation.isActive) continuation.resumeWithException(Go3Failure("Go3 listings could not refresh. Retry."))
                }
                override fun onResponse(call: Call,response: Response) {
                    try {
                        val decoded=response.use {
                            if(!it.isSuccessful) throw Go3Failure("Go3 listings could not refresh. Retry.",it.code)
                            val out=java.io.ByteArrayOutputStream()
                            it.body.byteStream().use { input ->
                                val buffer=ByteArray(8192)
                                while(out.size()<=2_000_000) {
                                    val n=input.read(buffer,0,minOf(buffer.size,2_000_001-out.size()))
                                    if(n<0) break
                                    out.write(buffer,0,n)
                                }
                            }
                            if(out.size()>2_000_000) throw Go3Failure("Go3 listings could not refresh. Retry.")
                            Go3Protocol.json(out.toString("UTF-8"))
                        }
                        if(continuation.isActive) continuation.resume(decoded)
                    } catch(_: Exception) {
                        if(continuation.isActive) continuation.resumeWithException(Go3Failure("Go3 listings could not refresh. Retry."))
                    }
                }
            })
        }
    }
    suspend fun load(source: Go3CatalogueSource): List<Go3Programme> {
        val at=now()
        if(source==Go3CatalogueSource.LIVE) return Go3CatalogueDecoder.decode(read("products/sections/7998051"),at)
        val result=linkedMapOf<String,Go3Programme>()
        var offset=0
        repeat(10) {
            val root=read("products/lives/programmes/catalog",listOf("categoryId[]" to requireNotNull(source.category),
                "firstResult" to "$offset","maxResults" to "100","sort" to "since","order" to "DESC")) as? JsonObject
                ?: throw Go3Failure("Go3 listings could not refresh. Retry.")
            val rows=Go3Protocol.array(root,"items")
            Go3CatalogueDecoder.decode(root,at).forEach { event ->
                val previous=result[event.id]
                result[event.id]=if(event.artwork==null && previous?.artwork!=null) event.copy(artwork=previous.artwork) else event
            }
            offset+=rows.size
            val total=Go3Protocol.text(Go3Protocol.objectValue(root,"meta"),"totalCount").toIntOrNull()
            if(rows.isEmpty() || rows.size<100 || total!=null && offset>=total) return result.values.toList()
        }
        throw Go3Failure("Go3 listings could not refresh. Retry.")
    }
    suspend fun programme(id: String): Go3Programme {
        require(id.matches(Regex("[0-9]+")))
        val root=read("products/lives/programmes/$id") as? JsonObject ?: throw Go3Failure("Go3 programme is unavailable.")
        return Go3CatalogueDecoder.programme(root,now()) ?: throw Go3Failure("Go3 programme is unavailable.")
    }
}
