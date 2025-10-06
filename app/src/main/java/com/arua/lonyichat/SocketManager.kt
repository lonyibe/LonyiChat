// File Path: lonyibe/lonyichat/LonyiChat-554c58ac20d26ce973661057119b615306e7f3c8/app/src/main/java/com/arua/lonyichat/SocketManager.kt

package com.arua.lonyichat

import android.util.Log
import com.arua.lonyichat.data.ApiService
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URISyntaxException

object SocketManager {

    private var mSocket: Socket? = null
    private val _chatBadgeUpdate = MutableSharedFlow<Unit>(replay = 0)
    val chatBadgeUpdate = _chatBadgeUpdate.asSharedFlow()

    fun getSocket(): Socket? {
        if (mSocket == null) {
            try {
                mSocket = IO.socket(ApiService.BASE_URL)
            } catch (e: URISyntaxException) {
                Log.e("SocketManager", "URISyntaxException: ${e.message}")
            }
        }
        return mSocket
    }

    fun establishConnection() {
        mSocket?.connect()
        mSocket?.on(Socket.EVENT_CONNECT) {
            Log.d("SocketManager", "Socket connected!")
            ApiService.getCurrentUserId()?.let { userId ->
                mSocket?.emit("register", userId)
            }
        }

        mSocket?.on("chat_badge_update") {
            Log.d("SocketManager", "Received chat_badge_update event")
            _chatBadgeUpdate.tryEmit(Unit)
        }
    }

    fun closeConnection() {
        mSocket?.disconnect()
        mSocket?.off(Socket.EVENT_CONNECT)
        mSocket?.off("chat_badge_update")
    }
}