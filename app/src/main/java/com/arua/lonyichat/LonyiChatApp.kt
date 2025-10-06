package com.arua.lonyichat

import android.app.Application
import android.content.Context
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.util.UnstableApi // FIX: Import UnstableApi
import okhttp3.OkHttpClient
import java.io.File

// ✨ FIX: The custom ViewModelFactory and static instance have been removed.
// This was the root cause of the lifecycle issue.
class LonyiChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        initializeCache() // Initialize caching components
    }

    // ✨ ADDED: Caching components for ExoPlayer
    companion object {
        lateinit var appContext: Context
            private set // Ensure it's not modified from outside

        private var cacheDataSourceFactory: CacheDataSource.Factory? = null

        // Cache size of 200MB
        private const val MAX_CACHE_SIZE = 200 * 1024 * 1024L

        // FIX: Apply Opt-In annotation
        @UnstableApi
        fun getCacheDataSourceFactory(): CacheDataSource.Factory {
            if (cacheDataSourceFactory == null) {
                // Must be initialized on the main thread, which onCreate guarantees.
                cacheDataSourceFactory = createCacheDataSourceFactory()
            }
            return cacheDataSourceFactory!!
        }

        // FIX: Apply Opt-In annotation
        @UnstableApi
        private fun createCacheDataSourceFactory(): CacheDataSource.Factory {
            // 1. Evictor: Least Recently Used, Max Size 200MB
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)

            // 2. Cache instance, stores cache index in a local directory
            val cacheDir = File(appContext.cacheDir, "media_cache")
            val simpleCache = SimpleCache(cacheDir, cacheEvictor)

            // 3. Upstream factory (Network DataSource) - using OkHttp for better network management
            val okHttpClient = OkHttpClient() // Reuse ApiService.client if possible, or create new one
            val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)

            // 4. Cache Factory: combines all components
            return CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheReadDataSourceFactory(FileDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

    }

    // Legacy cache initialization method kept private or can be removed if not needed elsewhere
    private fun initializeCache() {
        // Calling getCacheDataSourceFactory ensures initialization happens
        getCacheDataSourceFactory()
    }
}