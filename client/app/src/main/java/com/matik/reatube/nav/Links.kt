package com.matik.reatube.nav

import com.matik.reatube.api.ChannelItem
import com.matik.reatube.api.Item
import com.matik.reatube.api.LinkItem
import com.matik.reatube.api.ShowItem
import com.matik.reatube.api.VideoCard
import com.matik.reatube.api.VideoItem

/** Maps Rutube links to screens, the same way the official app's TabContainerFragment does. */
object Links {
    private val videoPath = Regex("^video/([0-9a-f]{32})$")

    // Menu items whose screen is a Pangolin feed with a different slug (docs/api.md, §1).
    private val rootFeeds = mapOf(
        "main" to "tab_new_main",
        "catalog" to "smart_category",
        "tv-channels" to "live_smarttv",
        "movies-serials" to "movies-serials",
        "kids" to "kids",
        "sport" to "sport",
        "premier" to "premier",
    )

    /** Accepts `rutube://rutube.ru/...`, `https://rutube.ru/...` and bare `/feeds/...`; null if unsupported. */
    fun route(link: String, title: String): Route? {
        val path = link.substringAfter("rutube.ru", link).substringBefore('?').trim('/')
        rootFeeds[path]?.let { return Route.Feed(it, title) }
        videoPath.find(path)?.let {
            return Route.Player(VideoCard(it.groupValues[1], title, null, null, null, null, isLive = false, isPaid = false))
        }
        return when {
            path == "search" -> Route.Search
            path.startsWith("feeds/") -> Route.Feed(path.removePrefix("feeds/").substringBefore('/'), title)
            path == "my/subscriptions" -> Route.Saved(SavedKind.Subscriptions)
            path == "my/watch-later" || path == "my/liked" -> Route.Saved(SavedKind.WatchLater)
            path == "my/watch-history" -> Route.Saved(SavedKind.History)
            path.startsWith("video/person/") -> Route.Channel(path.removePrefix("video/person/").substringBefore('/'), title)
            path.startsWith("channel/") -> Route.Channel(path.removePrefix("channel/").substringBefore('/'), title)
            path.startsWith("metainfo/tv/") -> Route.Show(path.removePrefix("metainfo/tv/").substringBefore('/'), title)
            else -> null
        }
    }

    fun route(item: Item): Route? = when (item) {
        is VideoItem -> Route.Player(item.video)
        is ChannelItem -> Route.Channel(item.id, item.title)
        is ShowItem -> Route.Show(item.id, item.title)
        is LinkItem -> route(item.link, item.title)
    }
}
