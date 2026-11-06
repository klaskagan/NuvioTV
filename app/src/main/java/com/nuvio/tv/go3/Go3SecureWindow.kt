package com.nuvio.tv.go3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun Go3SecureWindow() {
    val context=LocalContext.current
    DisposableEffect(context) {
        var current: Context?=context
        while(current is ContextWrapper && current !is Activity) current=current.baseContext
        val window=(current as? Activity)?.window
        if(window!=null) SecureWindows.acquire(window)
        onDispose { if(window!=null) SecureWindows.release(window) }
    }
}


/** Navigation transitions can keep two protected presentations mounted on the same window. */
private object SecureWindows {
    private data class Ownership(var count: Int,val originallySecure: Boolean)
    private val owners=java.util.WeakHashMap<android.view.Window,Ownership>()
    @Synchronized fun acquire(window: android.view.Window) {
        val existing=owners[window]
        if(existing==null) owners[window]=Ownership(1,window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
        else existing.count++
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    @Synchronized fun release(window: android.view.Window) {
        val ownership=owners[window] ?: return
        if(--ownership.count==0) {
            owners.remove(window)
            if(!ownership.originallySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
