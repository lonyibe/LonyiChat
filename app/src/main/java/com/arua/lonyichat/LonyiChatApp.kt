package com.arua.lonyichat

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arua.lonyichat.ui.viewmodel.ProfileViewModel

class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            if (LonyiChatApp.profileViewModelInstance == null) {
                LonyiChatApp.profileViewModelInstance = ProfileViewModel() as T
            }
            return LonyiChatApp.profileViewModelInstance as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class LonyiChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
        var profileViewModelInstance: ViewModel? = null

        fun getViewModelFactory(application: Application): ViewModelFactory {
            return ViewModelFactory(application)
        }
    }
}