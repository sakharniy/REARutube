package com.matik.reatube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.matik.reatube.data.Library
import com.matik.reatube.data.PlaybackLog
import com.matik.reatube.net.Http
import java.io.File

class ReaApp : Application(), SingletonImageLoader.Factory {
    lateinit var library: Library
        private set
    lateinit var playbackLog: PlaybackLog
        private set

    override fun onCreate() {
        super.onCreate()
        library = Library(File(filesDir, "library.json")).also { it.load() }
        playbackLog = PlaybackLog(File(filesDir, "playback-log.json")).also { it.load() }
    }

    // A low-RAM TV: keep the image cache small; thumbnails are requested pre-shrunk (Parsers.sizedImage).
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .build()
}
