package com.matik.reatube.data

import com.matik.reatube.api.VideoCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCodecTest {
    private val video = VideoCard("8584286624cd4e9ffa71c4a6233e69fe", "Фильм", "https://pic/x.jpg?size=m", 6288, "Автор", "45087022", isLive = false, isPaid = false)
    private val live = video.copy(id = "c58f502c7bb34a8fcdd976b221fca292", title = "Эфир", durationSec = null, isLive = true)

    @Test
    fun roundTrip() {
        val data = LibraryData(
            history = listOf(HistoryEntry(video, 61_000, 6_288_000, 1_760_000_000_000), HistoryEntry(live, 0, 0, 1_760_000_100_000)),
            watchLater = listOf(video),
            subscriptions = listOf(ChannelRef("45087022", "Документальные Фильмы", null)),
        )
        val text = LibraryCodec.encode(data).toString()
        assertEquals(data, LibraryCodec.decode(Json.parseToJsonElement(text) as JsonObject))
    }

    @Test
    fun brokenFileGivesEmptyLibrary() {
        val data = LibraryCodec.decode(Json.parseToJsonElement("""{"history": [{"video": {}}], "watchLater": 1}""") as JsonObject)
        assertTrue(data.history.isEmpty() && data.watchLater.isEmpty() && data.subscriptions.isEmpty())
    }
}
