package com.nuvio.tv.football

import android.content.Context
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FootballKeyStore @Inject constructor(@ApplicationContext context: Context) {
    private val secrets=ProtectedProfilePreferences(context,"pitchapi_local_key","com.nuvio.tv.pitchapi.v1")
    internal fun read()=secrets.read(0).orEmpty()
    internal fun save(key: String)=secrets.write(0,key.trim().takeIf(String::isNotEmpty))
    internal fun remove()=secrets.write(0,null)
}
