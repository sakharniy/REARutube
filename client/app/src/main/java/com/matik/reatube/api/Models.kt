package com.matik.reatube.api

data class VideoCard(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSec: Int?,
    val author: String?,
    val authorId: String?,
    val isLive: Boolean,
    /** Bought or by a RUTUBE subscription (START, Premier…): we never play those. */
    val isPaid: Boolean,
)

/** What the player needs from `play/options`. */
data class PlaySource(
    val title: String,
    val hlsUrl: String?,
    val isLive: Boolean,
    val hasDrm: Boolean,
    /** Ad blocks present in the response; we never request or play them, this is only reported. */
    val adBlocks: Int,
    /** Server's text for a subscription-only video, which comes without a stream. */
    val paywall: String? = null,
)

/**
 * Series marks from `pangolin/api/tv/video/<id>/annotation`: the official app shows "Пропустить" over the intro
 * and "Следующая серия" over the end credits. Other videos have neither.
 */
data class PlayerMarks(val intro: IntroMark?, val credits: CreditsMark?)

/** Between [startMs] and [endMs] "Пропустить" jumps to [targetMs]. */
data class IntroMark(val startMs: Long, val endMs: Long, val targetMs: Long)

/** End credits: "Следующая серия" opens [nextVideoId], by itself after [autoSwitchMs] when the server sets it. */
data class CreditsMark(val startMs: Long, val endMs: Long, val nextVideoId: String, val autoSwitchMs: Long?)

/** Side menu from `pangolin/api/tv/navigation/Android_TV`; [url] is a `rutube://rutube.ru/...` link. */
data class NavGroup(val slug: String, val title: String, val links: List<NavLink>)

data class NavLink(val title: String, val url: String, val iconUrl: String?)

/** A screen described by the server: tabs of rows, every row is its own paged API list. */
data class Feed(val title: String, val tabs: List<FeedTab>)

data class FeedTab(val name: String, val rows: List<FeedRow>)

/** [kind] is the resource's `app_label.model`, e.g. `tags.tag` or `custom_profile.userchannel`. */
data class FeedRow(val title: String, val url: String, val kind: String, val objectId: String?)

sealed interface Item {
    val key: String
    val title: String
}

data class VideoItem(val video: VideoCard) : Item {
    override val key get() = "video:${video.id}"
    override val title get() = video.title
}

data class ChannelItem(val id: String, override val title: String, val imageUrl: String?) : Item {
    override val key get() = "channel:$id"
}

/** A TV show, series or film (`metainfo.tv`): opens a list of its videos. */
data class ShowItem(val id: String, override val title: String, val imageUrl: String?) : Item {
    override val key get() = "show:$id"
}

/** A promo tile pointing to another screen, e.g. `/feeds/kids/`. */
data class LinkItem(override val title: String, val imageUrl: String?, val link: String) : Item {
    override val key get() = "link:$link"
}

data class Page(val items: List<Item>, val next: String?)

data class ChannelInfo(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val description: String,
    val subscribers: Int?,
    val videoCount: Int?,
)
