package com.arua.lonyichat

import android.app.Application
import android.content.Context

class LonyiChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
    }
}