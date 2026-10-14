package com.matik.reatube.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Titles as channels write them on Rutube (search results of 2026-10-13). */
class EpisodesTest {
    @Test
    fun seasonAndEpisodeInAnyOrder() {
        assertEquals(EpisodeTag("ведьмак", 1, 1), Episodes.tag("Ведьмак 1 сезон. 1 серия"))
        assertEquals(EpisodeTag("ведьмак", 1, 2), Episodes.tag("Ведьмак  / 2 серия / 1 сезон\nЦири встречает Дару"))
        assertEquals(EpisodeTag("ведьмак", 2, 1), Episodes.tag("Ведьмак / 2 сезон / 1 серия и Крупица истины"))
        assertEquals(EpisodeTag("интерны", 4, 2), Episodes.tag("Интерны, 4 сезон, 2 серия"))
        assertEquals(EpisodeTag("кухня отеля элеон", 1, 1), Episodes.tag("Кухня «Отеля Элеон», 1 сезон, 1 серия"))
    }

    @Test
    fun numberAfterTheWord() {
        assertEquals(EpisodeTag("ведьмак", 1, 2), Episodes.tag("Ведьмак Сезон 1 серия 2"))
        assertEquals(EpisodeTag("кухня", 1, 1), Episodes.tag("Кухня | Сезон 1 | Серия 1 - 5"))
    }

    @Test
    fun endingsAndShortForms() {
        assertEquals(EpisodeTag("сериал сваты 2", 2, 1), Episodes.tag("Сериал \"Сваты\" 2 (2-ой сезон, 1-я серия) комедийный фильм"))
        assertEquals(EpisodeTag("", null, 1), Episodes.tag("1 серия"))
        assertEquals(EpisodeTag("the witcher", 1, 3), Episodes.tag("The Witcher S01E03"))
    }

    @Test
    fun notAnEpisode() {
        assertNull(Episodes.tag("Борис Ханчалян: «Пора снимать кино про победу»"))
        assertNull(Episodes.tag("Ведьмак 2 сезон")) // a trailer or a review, no episode number
    }

    @Test
    fun nextEpisodeOfTheSameSeries() {
        val first = EpisodeTag("ведьмак", 1, 1)
        assertTrue(Episodes.isNext(first, EpisodeTag("ведьмак", 1, 2)))
        assertFalse(Episodes.isNext(first, EpisodeTag("ведьмак", 1, 3))) // what Rutube suggested on one channel
        assertFalse(Episodes.isNext(first, EpisodeTag("кухня", 1, 2)))
        assertTrue(Episodes.isNext(EpisodeTag("ведьмак", 1, 8), EpisodeTag("ведьмак", 2, 1)))
    }

    @Test
    fun nextEpisodeMustBeOnTheSameChannel() {
        val ruoff = VideoCard("a".repeat(32), "Ведьмак 1 сезон 1 серия", null, 3600, "Фильмы и сериалы RUOFF", "32417914", isLive = false, isPaid = false)
        val second = ruoff.copy(id = "b".repeat(32), title = "Ведьмак  / 2 серия / 1 сезон")
        assertTrue(Episodes.isNextEpisode(ruoff, second))
        assertFalse(Episodes.isNextEpisode(ruoff, second.copy(authorId = "14791407")))
    }

    @Test
    fun searchTheSameSeasonThenTheNextOne() {
        assertEquals(
            listOf("ведьмак 1 сезон 2 серия", "ведьмак 2 сезон 1 серия"),
            Episodes.searchQueries(EpisodeTag("ведьмак", 1, 1)),
        )
    }
}
