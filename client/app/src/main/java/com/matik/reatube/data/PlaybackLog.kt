package com.matik.reatube.data

import androidx.compose.runtime.mutableStateListOf
import com.matik.reatube.api.asObject
import com.matik.reatube.api.bool
import com.matik.reatube.api.int
import com.matik.reatube.api.long
import com.matik.reatube.api.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.Executors

data class PlaybackSession(
    val at: Long,
    val title: String,
    val live: Boolean,
    val quality: String?,
    val decoder: String?,
    val droppedFrames: Int,
    val error: String?,
)

/** The last playback sessions, shown in Diagnostics: the only view of the player on a TV without ADB. */
class PlaybackLog(private val file: File) {
    val sessions = mutableStateListOf<PlaybackSession>()
    private val writer = Executors.newSingleThreadExecutor()

    fun load() {
        val root = runCatching { Json.parseToJsonElement(file.readText()) as? JsonArray }.getOrNull() ?: return
        sessions.addAll(root.mapNotNull { element ->
            val o = element.asObject() ?: return@mapNotNull null
            PlaybackSession(
                at = o.long("at") ?: 0,
                title = o.str("title").orEmpty(),
                live = o.bool("live") == true,
                quality = o.str("quality"),
                decoder = o.str("decoder"),
                droppedFrames = o.int("dropped") ?: 0,
                error = o.str("error"),
            )
        })
    }

    fun add(session: PlaybackSession) {
        sessions.add(0, session)
        while (sessions.size > MAX_SESSIONS) sessions.removeAt(sessions.lastIndex)
        val text = buildJsonArray {
            sessions.forEach { s ->
                addJsonObject {
                    put("at", s.at)
                    put("title", s.title)
                    put("live", s.live)
                    s.quality?.let { put("quality", it) }
                    s.decoder?.let { put("decoder", it) }
                    put("dropped", s.droppedFrames)
                    s.error?.let { put("error", it) }
                }
            }
        }.toString()
        writer.execute { runCatching { file.writeText(text) } }
    }

    private companion object {
        const val MAX_SESSIONS = 10
    }
}
