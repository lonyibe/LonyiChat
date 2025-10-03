package com.arua.lonyichat

import android.app.Application
import android.content.Context

// ✨ FIX: The custom ViewModelFactory and static instance have been removed.
// This was the root cause of the lifecycle issue.
class LonyiChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set // Ensure it's not modified from outside
    }
}