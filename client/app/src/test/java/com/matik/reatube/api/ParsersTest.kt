package com.matik.reatube.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Responses captured from the live API on 2026-10-13 (see docs/api.md). */
class ParsersTest {

    private fun sample(name: String): JsonElement =
        Json.parseToJsonElement(javaClass.classLoader!!.getResource("samples/$name")!!.readText())

    @Test
    fun searchResultsBecomeCards() {
        val cards = Parsers.videoCards(sample("search_video.json"))
        assertTrue(cards.size > 10)
        assertTrue(cards.all { it.id.length == 32 && it.title.isNotEmpty() })
        assertNotNull(cards.first().author)
    }

    @Test
    fun thumbnailsAreRequestedSmall() {
        val card = Parsers.videoCards(sample("search_video.json")).first()
        assertTrue(card.thumbnailUrl!!.endsWith("?size=m"))
    }

    @Test
    fun vodPlayOptionsGiveHlsAndCountTvAds() {
        val source = Parsers.playSource(sample("play_options_tv_ads.json"))
        assertTrue(source.hlsUrl!!.contains(".m3u8"))
        assertFalse(source.isLive)
        assertFalse(source.hasDrm)
        assertEquals(3, source.adBlocks)
    }

    @Test
    fun livePlayOptionsPreferLiveStream() {
        val source = Parsers.playSource(sample("play_options_live.json"))
        assertTrue(source.isLive)
        assertTrue(source.hlsUrl!!.contains("/livestream/"))
    }

    @Test
    fun liveIsFoundInsideFeedRow() {
        val live = Parsers.findLive(sample("live_row.json"))
        assertNotNull(live)
        assertTrue(live!!.isLive)
    }

    @Test
    fun feedPointsToFirstApiRow() {
        val url = Parsers.firstRowUrl(sample("feed_live_smarttv.json"))
        assertTrue(url!!.startsWith(RutubeApi.API))
    }

    @Test
    fun navigationGivesMenuWithIcons() {
        val links = Parsers.navigation(sample("navigation.json")).flatMap { it.links }
        assertEquals("rutube://rutube.ru/search", links.first().url)
        assertTrue(links.size >= 8)
        val icons = links.mapNotNull { it.iconUrl }
        assertTrue(icons.size >= 7)
        // SVG (promo) and empty ("Моё") icons are dropped so the menu falls back to built-in ones.
        assertTrue(icons.none { it.isBlank() || it.endsWith(".svg") })
    }

    @Test
    fun feedHasTabsOfPangolinRows() {
        val feed = Parsers.feed(sample("feed_main.json"))
        assertTrue(feed.tabs.size >= 3)
        val main = feed.tabs.first { it.name == "Главная" }
        assertTrue(main.rows.size >= 5)
        assertTrue(main.rows.all { it.url.startsWith("https://rutube.ru/pangolin/api/tv/resource/") })
    }

    @Test
    fun videoRowGivesVideos() {
        val page = Parsers.page(sample("row_videos.json"))
        assertTrue(page.items.size >= 10)
        assertTrue(page.items.all { it is VideoItem })
    }

    @Test
    fun cardGroupGivesChannelsAndShows() {
        val items = Parsers.page(sample("row_cardgroup.json")).items
        assertTrue(items.any { it is ChannelItem })
        assertTrue(items.any { it is ShowItem })
    }

    @Test
    fun promoRowGivesLinksToFeeds() {
        val items = Parsers.page(sample("row_promo.json")).items
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it is LinkItem && it.link.startsWith("/feeds/") })
    }

    @Test
    fun endScreenGivesNextVideo() {
        assertNotNull(Parsers.nextVideo(sample("end_screen.json")))
    }

    @Test
    fun seriesEpisodeHasIntroAndCreditsMarks() {
        val marks = Parsers.marks(sample("annotation.json")) // «Бывшие», 1 сезон, 1 серия
        assertEquals(IntroMark(startMs = 0, endMs = 10_000, targetMs = 10_000), marks.intro)
        assertEquals(CreditsMark(3_035_000, 3_074_000, "e6df92f859f6dc099c400aa45a7dd1bd", autoSwitchMs = 6_000), marks.credits)
    }

    @Test
    fun subscriptionEpisodeIsPaid() {
        // «Бывшие», 2 серия in the end screen: is_paid is false, the subscription list is not
        val json = """{"id":"e6df92f859f6dc099c400aa45a7dd1bd","title":"Бывшие, 1 сезон, 2 серия","is_paid":false,
            "common_subscription_product_codes":["RUTUBE_START","PREMIER_RUTUBE_START","vse-vklyucheno"]}"""
        assertTrue(Parsers.videoCard(Json.parseToJsonElement(json).asObject()!!)!!.isPaid)
    }

    @Test
    fun subscriptionVideoHasNoStreamButAPaywallText() {
        val json = """{"title":null,"video_balancer":null,"detail":{"id":39,"name":"subscription: RUTUBE_START",
            "type":"common_subscription","languages":[{"lang":"rus","title":"Смотрите по подписке RUTUBE",
            "description":"Оформить можно на сайте","url_title":null}],"product_codes":["RUTUBE_START"]}}"""
        val source = Parsers.playSource(Json.parseToJsonElement(json))
        assertEquals(null, source.hlsUrl)
        assertEquals("Смотрите по подписке RUTUBE. Оформить можно на сайте", source.paywall)
    }

    @Test
    fun disclaimersAreNotMarks() {
        val json = """{"activities":[{"annotation_type":"DISCLAIMER","activity_type":"DISCLAIMER",
            "params":{"text":"Facebook и Instagram запрещены в РФ","show":{"strategy":"PRIORITY","duration_ms":3000}}}]}"""
        assertEquals(PlayerMarks(intro = null, credits = null), Parsers.marks(Json.parseToJsonElement(json)))
    }

    @Test
    fun suggestionsAreStrings() {
        val suggestions = Parsers.suggestions(sample("autocomplete.json"))
        assertTrue(suggestions.isNotEmpty() && suggestions.all { it.isNotBlank() })
    }

    @Test
    fun channelProfile() {
        val channel = Parsers.channelInfo(sample("channel_profile.json"))!!
        assertEquals("45087022", channel.id)
        assertTrue(channel.subscribers!! > 0)
    }

    @Test
    fun unknownShapesDoNotCrash() {
        assertTrue(Parsers.page(Json.parseToJsonElement("""{"results": [{"id": 5}, "x", null, {"object": {}}]}""")).items.isEmpty())
        assertEquals(null, Parsers.findLive(Json.parseToJsonElement("[]")))
        assertTrue(Parsers.feed(Json.parseToJsonElement("{}")).tabs.isEmpty())
    }
}
