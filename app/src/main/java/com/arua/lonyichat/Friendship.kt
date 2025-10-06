package com.arua.lonyichat

data class FriendshipStatus(
    val userId: String,
    val status: String // Can be "none", "friends", "request_sent", "request_received"
)