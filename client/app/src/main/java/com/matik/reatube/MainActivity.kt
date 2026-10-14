package com.matik.reatube

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.matik.reatube.api.RutubeApi
import com.matik.reatube.ui.App
import com.matik.reatube.ui.LocalServices
import com.matik.reatube.ui.Services

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ReaApp
        val services = Services(RutubeApi(), app.library, app.playbackLog)
        setContent {
            CompositionLocalProvider(LocalServices provides services) { App() }
        }
    }

    // While PlayerView holds focus Compose never sees the remote's "menu" key, so catch it here.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) RemoteKeys.lastKeyDownAt = SystemClock.uptimeMillis()
        if (RemoteKeys.onKey?.invoke(event) == true) return true
        val onMenu = RemoteKeys.onMenu
        if (event.keyCode == KeyEvent.KEYCODE_MENU && onMenu != null) {
            if (event.action == KeyEvent.ACTION_UP) onMenu()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

object RemoteKeys {
    var onMenu: (() -> Unit)? = null

    /** Lets the player take keys before PlayerView does; returns true if the key was handled. */
    var onKey: ((KeyEvent) -> Boolean)? = null

    /** Screens move focus by themselves only until the user presses something. */
    var lastKeyDownAt = 0L
}
