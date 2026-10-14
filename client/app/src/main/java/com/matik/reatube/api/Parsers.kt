package com.matik.reatube.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Pure JSON → model functions, unit-tested against saved API responses. */
object Parsers {
    private val videoId = Regex("^[0-9a-f]{32}$")

    /** Rutube images come full size (a 1920 px thumbnail is ~330 KB); `size=m` gives 480 px, ~33 KB. */
    fun sizedImage(url: String?, size: String = "m"): String? =
        url?.takeIf { it.isNotBlank() }?.let { if ('?' in it) it else "$it?size=$size" }

    fun videoCard(o: JsonObject): VideoCard? {
        val id = o.str("id")?.takeIf(videoId::matches) ?: return null
        val author = o.obj("author")
        return VideoCard(
            id = id,
            title = o.str("title").orEmpty(),
            thumbnailUrl = sizedImage(o.str("thumbnail_url")),
            durationSec = o.int("duration"),
            author = author?.str("name"),
            authorId = author?.str("id"),
            isLive = o.bool("is_livestream") == true,
            // A subscription episode has is_paid=false but lists the subscriptions that open it.
            isPaid = o.bool("is_paid") == true || o.arr("common_subscription_product_codes")?.isNotEmpty() == true,
        )
    }

    fun videoCards(root: JsonElement): List<VideoCard> =
        page(root).items.mapNotNull { (it as? VideoItem)?.video }

    fun playSource(root: JsonElement): PlaySource {
        val o = root.asObject() ?: throw IllegalArgumentException("play/options: ожидался JSON-объект")
        val live = o.obj("live_streams")?.arr("hls")?.firstNotNullOfOrNull { it.asObject()?.str("url") }
        val vod = o.obj("video_balancer")?.str("m3u8")
        // The TV request (ad_ver=v2) gets {video: {ad_breaks: [...]}}, the web one gets a list.
        val adBlocks = when (val advert = o["advert"]) {
            is JsonObject -> advert.obj("video")?.arr("ad_breaks")?.size ?: 0
            is JsonArray -> advert.size
            else -> 0
        }
        // Without a subscription: no stream, and `detail` says which subscription opens the video.
        val paywall = o.obj("detail")
            ?.takeIf { it.str("type") == "common_subscription" || it.arr("product_codes")?.isNotEmpty() == true }
            ?.let { detail ->
                val text = detail.arr("languages")?.firstOrNull()?.asObject()
                listOfNotNull(text?.str("title"), text?.str("description")).joinToString(". ").ifEmpty { "Видео доступно по подписке" }
            }
        return PlaySource(
            title = o.str("title").orEmpty(),
            hlsUrl = live ?: vod,
            isLive = live != null,
            hasDrm = o.str("drm_token") != null,
            adBlocks = adBlocks,
            paywall = paywall,
        )
    }

    fun navigation(root: JsonElement): List<NavGroup> =
        root.asObject()?.arr("result")?.mapNotNull { group ->
            val g = group.asObject() ?: return@mapNotNull null
            val links = g.arr("links")?.mapNotNull { link ->
                val l = link.asObject() ?: return@mapNotNull null
                // "Моё" items come with an empty icon and promo items with an SVG we can't draw: use our own then.
                val icon = l.str("icon")?.takeIf { it.isNotBlank() && !it.endsWith(".svg") }
                NavLink(l.str("title") ?: return@mapNotNull null, l.str("url") ?: return@mapNotNull null, icon)
            }.orEmpty()
            NavGroup(g.str("slug").orEmpty(), g.str("name").orEmpty(), links)
        }.orEmpty()

    fun feed(root: JsonElement): Feed {
        val o = root.asObject() ?: throw IllegalArgumentException("лента: ожидался JSON-объект")
        val tabs = o.arr("tabs")?.mapNotNull { tab ->
            val t = tab.asObject() ?: return@mapNotNull null
            val rows = t.arr("resources")?.mapNotNull { resource ->
                val r = resource.asObject() ?: return@mapNotNull null
                val type = r.obj("content_type")
                FeedRow(
                    title = r.str("name").orEmpty(),
                    url = r.str("url") ?: return@mapNotNull null,
                    kind = "${type?.str("app_label")}.${type?.str("model")}",
                    objectId = r.str("object_id"),
                )
            }.orEmpty()
            FeedTab(t.str("name").orEmpty(), rows)
        }.orEmpty()
        return Feed(o.str("name").orEmpty(), tabs.filter { it.rows.isNotEmpty() })
    }

    /** A page of any row: videos, wrapped channels/shows (`{content_type, object}`) or promo tiles. */
    fun page(root: JsonElement): Page {
        val o = root.asObject()
        val results = o?.arr("results") ?: root as? JsonArray ?: JsonArray(emptyList())
        return Page(results.mapNotNull { it.asObject()?.let(::item) }, o?.str("next"))
    }

    fun item(o: JsonObject): Item? {
        videoCard(o)?.let { return VideoItem(it) }
        o.obj("video")?.let(::videoCard)?.let { return VideoItem(it) }
        val inner = o.obj("object")
        if (inner != null) {
            val id = inner.str("id") ?: return null
            val name = inner.str("name") ?: inner.str("title") ?: return null
            val image = sizedImage(inner.str("picture")) ?: sizedImage(inner.str("poster_url"))
            return when (o.obj("content_type")?.str("model")) {
                "userchannel" -> ChannelItem(id, name, image)
                "tv" -> ShowItem(id, name, image)
                else -> null
            }
        }
        val target = o.str("target") ?: return null
        val title = o.str("title") ?: return null
        return LinkItem(title, sizedImage(o.str("picture")), target)
    }

    fun channelInfo(root: JsonElement): ChannelInfo? {
        val o = root.asObject() ?: return null
        return ChannelInfo(
            id = o.str("id") ?: return null,
            name = o.str("name").orEmpty(),
            avatarUrl = sizedImage(o.str("avatar_url")),
            description = o.str("description").orEmpty(),
            subscribers = o.int("subscribers_count"),
            videoCount = o.int("video_count"),
        )
    }

    fun suggestions(root: JsonElement): List<String> =
        root.asObject()?.arr("results")?.mapNotNull { it.asObject()?.str("value") }.orEmpty()

    fun nextVideo(root: JsonElement): VideoCard? = root.asObject()?.obj("next_video")?.let(::videoCard)

    /** Buttons of the annotation; DISCLAIMER texts are skipped. */
    fun marks(root: JsonElement): PlayerMarks {
        val buttons = root.asObject()?.arr("activities").orEmpty().mapNotNull { activity ->
            val a = activity.asObject()?.takeIf { it.str("activity_type") == "BUTTON" } ?: return@mapNotNull null
            val params = a.obj("params") ?: return@mapNotNull null
            val show = params.obj("show") ?: return@mapNotNull null
            MarkButton(
                type = a.str("annotation_type"),
                startMs = show.long("start_position_ms") ?: return@mapNotNull null,
                endMs = show.long("end_position_ms") ?: return@mapNotNull null,
                click = params.obj("on_click"),
                progress = params.obj("progress"),
            )
        }
        val intro = buttons.firstOrNull { it.type == "SKIP_INTRO" && it.click?.str("strategy") == "SEEK_EXACTLY" }
            ?.let { IntroMark(it.startMs, it.endMs, it.click?.long("target_position_ms") ?: it.endMs) }
        val credits = buttons.firstNotNullOfOrNull { button ->
            val next = button.click?.takeIf { button.type == "SKIP_TITLES" && it.str("strategy") == "SWITCH_VIDEO" }
                ?.str("video_id")?.takeIf(videoId::matches) ?: return@firstNotNullOfOrNull null
            val autoSwitch = button.progress?.takeIf { it.str("strategy") == "AUTO_TRIGGER" }?.long("duration_ms")
            CreditsMark(button.startMs, button.endMs, next, autoSwitch)
        }
        return PlayerMarks(intro, credits)
    }

    private class MarkButton(val type: String?, val startMs: Long, val endMs: Long, val click: JsonObject?, val progress: JsonObject?)

    /** Feeds nest videos at different depths, so search the whole tree for the first live one. */
    fun findLive(root: JsonElement): VideoCard? = when (root) {
        is JsonObject -> videoCard(root)?.takeIf { it.isLive } ?: root.values.firstNotNullOfOrNull(::findLive)
        is JsonArray -> root.firstNotNullOfOrNull(::findLive)
        else -> null
    }

    /** URL of the first row in a feed: every row (`resource`) points to an API list of videos. */
    fun firstRowUrl(root: JsonElement): String? = when (root) {
        is JsonObject -> root.str("url")?.takeIf { it.startsWith(RutubeApi.API) }
            ?: root.values.firstNotNullOfOrNull(::firstRowUrl)
        is JsonArray -> root.firstNotNullOfOrNull(::firstRowUrl)
        else -> null
    }
}
