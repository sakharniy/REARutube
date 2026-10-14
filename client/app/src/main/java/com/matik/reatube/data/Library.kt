package com.matik.reatube.data

import androidx.compose.runtime.mutableStateListOf
import com.matik.reatube.api.VideoCard
import com.matik.reatube.api.arr
import com.matik.reatube.api.asObject
import com.matik.reatube.api.bool
import com.matik.reatube.api.int
import com.matik.reatube.api.long
import com.matik.reatube.api.obj
import com.matik.reatube.api.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.util.concurrent.Executors

data class HistoryEntry(val video: VideoCard, val positionMs: Long, val durationMs: Long, val updatedAt: Long)

data class ChannelRef(val id: String, val name: String, val avatarUrl: String?)

/** Subscriptions, "watch later" and watch history kept on the TV itself (there is no Rutube account). */
class Library(private val file: File) {
    val history = mutableStateListOf<HistoryEntry>()
    val watchLater = mutableStateListOf<VideoCard>()
    val subscriptions = mutableStateListOf<ChannelRef>()
    private val writer = Executors.newSingleThreadExecutor()

    fun load() {
        val root = runCatching { Json.parseToJsonElement(file.readText()).asObject() }.getOrNull() ?: return
        val data = LibraryCodec.decode(root)
        history.addAll(data.history)
        watchLater.addAll(data.watchLater)
        subscriptions.addAll(data.subscriptions)
    }

    private fun save() {
        val text = LibraryCodec.encode(LibraryData(history.toList(), watchLater.toList(), subscriptions.toList())).toString()
        writer.execute { runCatching { file.writeText(text) } }
    }

    /** Where to continue a video; 0 if it was never started or was watched to the end. */
    fun resumePositionMs(videoId: String): Long {
        val entry = history.firstOrNull { it.video.id == videoId } ?: return 0
        val nearEnd = entry.durationMs > 0 && entry.positionMs > entry.durationMs - 30_000
        return if (nearEnd || entry.positionMs < 10_000) 0 else entry.positionMs
    }

    fun progress(videoId: String): Float? {
        val entry = history.firstOrNull { it.video.id == videoId } ?: return null
        return if (entry.durationMs > 0) (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else null
    }

    fun recordProgress(video: VideoCard, positionMs: Long, durationMs: Long) {
        history.removeAll { it.video.id == video.id }
        history.add(0, HistoryEntry(video, positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0), System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        save()
    }

    fun removeFromHistory(videoId: String) {
        if (history.removeAll { it.video.id == videoId }) save()
    }

    fun clearHistory() {
        history.clear()
        save()
    }

    fun isInWatchLater(videoId: String) = watchLater.any { it.id == videoId }

    /** Returns true if the video was added, false if it was removed. */
    fun toggleWatchLater(video: VideoCard): Boolean {
        val removed = watchLater.removeAll { it.id == video.id }
        if (!removed) watchLater.add(0, video)
        save()
        return !removed
    }

    fun isSubscribed(channelId: String) = subscriptions.any { it.id == channelId }

    /** Returns true if subscribed, false if unsubscribed. */
    fun toggleSubscription(channel: ChannelRef): Boolean {
        val removed = subscriptions.removeAll { it.id == channel.id }
        if (!removed) subscriptions.add(0, channel)
        save()
        return !removed
    }

    private companion object {
        const val MAX_HISTORY = 300
    }
}

data class LibraryData(val history: List<HistoryEntry>, val watchLater: List<VideoCard>, val subscriptions: List<ChannelRef>)

/** JSON form of the library; separate from [Library] so it can be unit-tested without Android. */
object LibraryCodec {
    fun encode(data: LibraryData): JsonObject = buildJsonObject {
        put("version", 1)
        putJsonArray("history") {
            data.history.forEach { entry ->
                addJsonObject {
                    put("video", entry.video.toJson())
                    put("position", entry.positionMs)
                    put("duration", entry.durationMs)
                    put("updated", entry.updatedAt)
                }
            }
        }
        putJsonArray("watchLater") { data.watchLater.forEach { add(it.toJson()) } }
        putJsonArray("subscriptions") {
            data.subscriptions.forEach { channel ->
                addJsonObject {
                    put("id", channel.id)
                    put("name", channel.name)
                    channel.avatarUrl?.let { put("avatar", it) }
                }
            }
        }
    }

    fun decode(root: JsonObject): LibraryData = LibraryData(
        history = root.arr("history")?.mapNotNull { element ->
            val o = element.asObject() ?: return@mapNotNull null
            HistoryEntry(
                video = o.obj("video")?.toVideo() ?: return@mapNotNull null,
                positionMs = o.long("position") ?: 0,
                durationMs = o.long("duration") ?: 0,
                updatedAt = o.long("updated") ?: 0,
            )
        }.orEmpty(),
        watchLater = root.arr("watchLater")?.mapNotNull { it.asObject()?.toVideo() }.orEmpty(),
        subscriptions = root.arr("subscriptions")?.mapNotNull { element ->
            val o = element.asObject() ?: return@mapNotNull null
            ChannelRef(o.str("id") ?: return@mapNotNull null, o.str("name").orEmpty(), o.str("avatar"))
        }.orEmpty(),
    )

    private fun VideoCard.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        thumbnailUrl?.let { put("thumb", it) }
        durationSec?.let { put("duration", it) }
        author?.let { put("author", it) }
        authorId?.let { put("authorId", it) }
        put("live", isLive)
    }

    private fun JsonObject.toVideo(): VideoCard? = VideoCard(
        id = str("id") ?: return null,
        title = str("title").orEmpty(),
        thumbnailUrl = str("thumb"),
        durationSec = int("duration"),
        author = str("author"),
        authorId = str("authorId"),
        isLive = bool("live") == true,
        isPaid = false,
    )
}
