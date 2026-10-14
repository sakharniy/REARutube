package com.matik.reatube.api

import com.matik.reatube.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** Anonymous Rutube API, as mapped in docs/api.md. */
class RutubeApi(private val http: OkHttpClient = Http.client) {

    // Parsed models, not JSON trees: a feed's JSON is ~150 KB and the TV is low on memory.
    private val cache = object : LinkedHashMap<String, Pair<Long, Any?>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Any?>>) = size > 150
    }

    /** Downloads and parses off the main thread: a row's JSON is up to 160 KB, parsing it on the UI thread made scrolling stutter. */
    suspend fun get(url: String): JsonElement = withContext(Dispatchers.IO) { Json.parseToJsonElement(fetch(url)) }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
            return response.body!!.string()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(key: String, load: suspend () -> T): T {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[key]?.let { (time, value) -> if (now - time < CACHE_MS) return value as T }
        }
        return withContext(Dispatchers.Default) { load() }.also { value -> synchronized(cache) { cache[key] = now to value } }
    }

    suspend fun navigation(): List<NavGroup> =
        cached("nav") { Parsers.navigation(get("${PANGOLIN_TV}navigation/Android_TV")) }

    suspend fun feed(slug: String): Feed = cached("feed:$slug") { Parsers.feed(get("${PANGOLIN_TV}feeds/$slug/")) }

    suspend fun page(url: String): Page = cached("page:$url") { Parsers.page(get(url)) }

    /** A page already in memory, so a row that scrolls back into view starts with its cards instead of a placeholder. */
    fun cachedPage(url: String): Page? = synchronized(cache) {
        cache["page:$url"]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.second as? Page
    }

    /** Channel rows answer 404 through Pangolin, so read the channel's videos directly. */
    fun rowUrl(row: FeedRow): String =
        if (row.kind == "custom_profile.userchannel" && row.objectId != null) channelVideosUrl(row.objectId) else row.url

    fun channelVideosUrl(id: String) = "${API}video/person/$id/?page=1"

    fun showVideosUrl(id: String) = "${API}metainfo/tv/$id/video/?page=1"

    fun searchUrl(query: String) = "${API}search/video/?query=${encode(query)}"

    suspend fun searchVideos(query: String): List<VideoCard> = Parsers.videoCards(get(searchUrl(query)))

    suspend fun suggestions(query: String): List<String> =
        Parsers.suggestions(get("${API}search/autocomplete/video/?query=${encode(query)}"))

    suspend fun channel(id: String): ChannelInfo? = cached("channel:$id") { Parsers.channelInfo(get("${API}profile/user/$id")) }

    suspend fun nextVideo(videoId: String): VideoCard? =
        Parsers.nextVideo(get("${API}v2/video/end-screen/$videoId/?limit=0"))

    suspend fun video(id: String): VideoCard? = get("${API}video/$id/").asObject()?.let(Parsers::videoCard)

    /**
     * For a series uploaded as plain videos: the next episode on the same channel. Rutube's own suggestion
     * when it is that one (it usually is), otherwise a search: on some channels the suggestion skips an episode.
     */
    suspend fun nextEpisode(current: VideoCard, suggested: VideoCard?): VideoCard? {
        if (suggested != null && Episodes.isNextEpisode(current, suggested)) return suggested
        val tag = Episodes.tag(current.title)?.takeIf { it.show.isNotEmpty() } ?: return null
        for (query in Episodes.searchQueries(tag)) {
            searchVideos(query).firstOrNull { !it.isPaid && Episodes.isNextEpisode(current, it) }?.let { return it }
        }
        return null
    }

    /** Intro and end-credits marks of a series episode. Without `client=tv` the server answers 400. */
    suspend fun marks(videoId: String, authorId: String): PlayerMarks =
        Parsers.marks(get("${PANGOLIN_TV}video/$videoId/annotation?featureUseCount=0&author_id=$authorId&client=tv"))

    /**
     * Stream info, never cached: the links are signed and expire. The official app adds
     * ac_client/ad_ver/ac_request_id; without them the server returns no ad schedule at all.
     */
    suspend fun playOptions(videoId: String): PlaySource =
        Parsers.playSource(get("${API}play/options/$videoId/?no_404=true&pver=v2&referer=${encode(TV_REFERER)}"))

    /** Some cards (e.g. an off-air TV channel) come without a stream, so take the first one that has it. */
    suspend fun firstPlayable(cards: List<VideoCard>, attempts: Int = 6): Pair<VideoCard, PlaySource>? {
        for (card in cards.take(attempts)) {
            val source = playOptions(card.id)
            if (source.hlsUrl != null && !source.hasDrm) return card to source
        }
        return null
    }

    suspend fun findLive(): VideoCard? {
        val feed = get("${API}feeds/live_smarttv/")
        Parsers.findLive(feed)?.let { return it }
        return Parsers.firstRowUrl(feed)?.let { Parsers.findLive(get(it)) }
    }

    companion object {
        const val API = "https://rutube.ru/api/"
        const val PANGOLIN_TV = "https://rutube.ru/pangolin/api/tv/"
        const val TV_REFERER = "https://tv-androidtv.rutube.ru"
        private const val CACHE_MS = 5 * 60_000L

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
