package com.matik.reatube.nav

import com.matik.reatube.api.VideoCard

sealed interface Route {
    data class Feed(val slug: String, val title: String) : Route
    data class Channel(val id: String, val name: String) : Route
    data class Show(val id: String, val name: String) : Route
    data class Player(val video: VideoCard) : Route
    data object Search : Route
    data class Saved(val kind: SavedKind) : Route
    data object Diagnostics : Route
}

/** "Моё" without a Rutube account: kept on the TV. */
enum class SavedKind(val title: String) {
    Subscriptions("Подписки"),
    WatchLater("Смотреть позже"),
    History("История просмотров"),
}
