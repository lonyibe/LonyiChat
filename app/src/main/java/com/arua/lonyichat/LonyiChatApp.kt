// File Path: lonyibe/lonyichat/LonyiChat-554c58ac20d26ce973661057119b615306e7f3c8/app/src/main/java/com/arua/lonyichat/LonyiChatApp.kt

package com.arua.lonyichat

import android.app.Application
import android.content.Context
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.util.UnstableApi
import okhttp3.OkHttpClient
import java.io.File

class LonyiChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        initializeCache() // Initialize caching components

        // ✨ NEW: Establish the socket connection when the app starts
        SocketManager.establishConnection()
    }

    companion object {
        lateinit var appContext: Context
            private set

        private var cacheDataSourceFactory: CacheDataSource.Factory? = null

        private const val MAX_CACHE_SIZE = 200 * 1024 * 1024L

        @UnstableApi
        fun getCacheDataSourceFactory(): CacheDataSource.Factory {
            if (cacheDataSourceFactory == null) {
                cacheDataSourceFactory = createCacheDataSourceFactory()
            }
            return cacheDataSourceFactory!!
        }

        @UnstableApi
        private fun createCacheDataSourceFactory(): CacheDataSource.Factory {
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val cacheDir = File(appContext.cacheDir, "media_cache")
            val simpleCache = SimpleCache(cacheDir, cacheEvictor)
            val okHttpClient = OkHttpClient()
            val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)

            return CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheReadDataSourceFactory(FileDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }

    private fun initializeCache() {
        getCacheDataSourceFactory()
    }
}