package com.offcampus.app.ui.navigation

object Routes {
    const val AUTH = "auth"
    const val PROFILE = "profile"
    const val AVATAR_PICKER = "avatarPicker"
    const val LOBBIES = "lobbies"
    const val FRIENDS = "friends"
    const val POST_TRIP = "postTrip"
    const val LOBBY_DETAIL_PATTERN = "lobbyDetail/{lobbyId}"
    const val LOBBY_CHAT_PATTERN = "lobbyChat/{lobbyId}"
    const val FRIEND_CHAT_PATTERN = "friendChat/{friendId}"
    const val POST_FARE_PATTERN = "postFare/{lobbyId}"
    const val PAYMENT_SPLIT_PATTERN = "paymentSplit/{lobbyId}"
    const val TRIP_HISTORY = "tripHistory"
    const val REPORT_PATTERN = "report/{lobbyId}/{reportedUserId}"
    const val RATE_PATTERN = "rate/{lobbyId}/{ratedUserId}"

    fun lobbyDetail(lobbyId: String) = "lobbyDetail/$lobbyId"
    fun lobbyChat(lobbyId: String) = "lobbyChat/$lobbyId"
    fun friendChat(friendId: String) = "friendChat/$friendId"
    fun postFare(lobbyId: String) = "postFare/$lobbyId"
    fun paymentSplit(lobbyId: String) = "paymentSplit/$lobbyId"
    fun report(lobbyId: String, reportedUserId: String) = "report/$lobbyId/$reportedUserId"
    fun rate(lobbyId: String, ratedUserId: String) = "rate/$lobbyId/$ratedUserId"
}
