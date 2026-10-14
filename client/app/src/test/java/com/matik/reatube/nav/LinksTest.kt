package com.matik.reatube.nav

import com.matik.reatube.api.Parsers
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {

    private fun route(link: String) = Links.route(link, "t")

    @Test
    fun menuLinksOpenPangolinFeeds() {
        assertEquals(Route.Search, route("rutube://rutube.ru/search"))
        assertEquals(Route.Feed("tab_new_main", "t"), route("rutube://rutube.ru/main"))
        assertEquals(Route.Feed("smart_category", "t"), route("rutube://rutube.ru/catalog"))
        assertEquals(Route.Feed("live_smarttv", "t"), route("rutube://rutube.ru/tv-channels"))
        assertEquals(Route.Feed("movies-serials", "t"), route("rutube://rutube.ru/movies-serials/"))
        assertEquals(Route.Feed("birthday", "t"), route("rutube://rutube.ru/feeds/birthday/"))
        assertEquals(Route.Feed("kids", "t"), route("/feeds/kids/"))
    }

    @Test
    fun accountLinksOpenLocalLists() {
        assertEquals(Route.Saved(SavedKind.Subscriptions), route("rutube://rutube.ru/my/subscriptions"))
        assertEquals(Route.Saved(SavedKind.History), route("rutube://rutube.ru/my/watch-history"))
        assertEquals(Route.Saved(SavedKind.WatchLater), route("rutube://rutube.ru/my/liked/"))
        assertNull(route("rutube://rutube.ru/my/playlists"))
    }

    @Test
    fun contentLinks() {
        val video = route("https://rutube.ru/video/8584286624cd4e9ffa71c4a6233e69fe/")
        assertTrue(video is Route.Player && video.video.id == "8584286624cd4e9ffa71c4a6233e69fe")
        assertEquals(Route.Channel("23872149", "t"), route("https://rutube.ru/video/person/23872149/"))
        assertEquals(Route.Channel("42", "t"), route("https://rutube.ru/channel/42/"))
        assertEquals(Route.Show("81004", "t"), route("https://rutube.ru/metainfo/tv/81004/"))
    }

    @Test
    fun everyServerMenuItemHasAScreenExceptPlaylists() {
        val text = javaClass.classLoader!!.getResource("samples/navigation.json")!!.readText()
        val links = Parsers.navigation(Json.parseToJsonElement(text)).flatMap { it.links }
        val unsupported = links.filter { Links.route(it.url, it.title) == null }.map { it.url }
        assertEquals(listOf("rutube://rutube.ru/my/playlists"), unsupported)
    }
}
