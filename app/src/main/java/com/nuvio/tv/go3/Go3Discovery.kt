package com.nuvio.tv.go3

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable public-metadata boundary; tests never need real account/authentication storage. */
interface Go3Discovery {
    val sources: List<Go3CatalogueSource>
    fun cached(source: Go3CatalogueSource): Go3CatalogueSnapshot?
    suspend fun load(source: Go3CatalogueSource,refresh: Boolean=false): Go3CatalogueSnapshot
    fun find(id: String): Go3Programme?
    suspend fun programme(id: String,refresh: Boolean=false): Go3Programme
    object Disabled : Go3Discovery {
        override val sources=emptyList<Go3CatalogueSource>()
        override fun cached(source: Go3CatalogueSource): Go3CatalogueSnapshot?=null
        override suspend fun load(source: Go3CatalogueSource,refresh: Boolean): Go3CatalogueSnapshot=error("Go3 fixture not installed")
        override fun find(id: String): Go3Programme?=null
        override suspend fun programme(id: String,refresh: Boolean): Go3Programme=error("Go3 fixture not installed")
    }
}

@Singleton
class Go3CatalogueRepository @Inject constructor() : Go3Discovery {
    private val cache=Go3CatalogueCache()
    private val loader=Go3PublicCatalogueLoader()
    private val details=java.util.concurrent.ConcurrentHashMap<String,Go3Programme>()
    override val sources=Go3CatalogueSource.entries
    override fun cached(source: Go3CatalogueSource)=cache.cached(source)
    override suspend fun load(source: Go3CatalogueSource,refresh: Boolean)=cache.load(source,refresh) { loader.load(source) }
    override fun find(id: String)=details[id] ?: cache.find(id)
    override suspend fun programme(id: String,refresh: Boolean): Go3Programme {
        if(!refresh) find(id)?.let { return it }
        val digits=id.removePrefix("go3:programme:")
        if(!id.startsWith("go3:programme:") || !digits.matches(Regex("[0-9]+"))) throw Go3Failure("Go3 programme is unavailable.")
        return loader.programme(digits).also { details[it.id]=it }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class Go3DiscoveryModule {
    @Binds @Singleton abstract fun bindGo3Discovery(repository: Go3CatalogueRepository): Go3Discovery
}
