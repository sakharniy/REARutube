package com.matik.reatube.api

/**
 * Season and episode from a video title, for channels that upload series as plain videos:
 * "Ведьмак 1 сезон. 1 серия", "Ведьмак / 2 серия / 1 сезон", "Ведьмак Сезон 1 серия 2", "Сваты 2 (2-ой сезон, 1-я серия)".
 * [show] is the normalized text before the numbers, so episodes of one series compare equal.
 */
data class EpisodeTag(val show: String, val season: Int?, val episode: Int)

object Episodes {
    private const val ENDING = """(?:-?(?:я|ая|й|ой|ый))?"""
    private val seasonFirst = Regex("""(\d{1,2})\s*$ENDING\s*сезон""", RegexOption.IGNORE_CASE)
    private val seasonAfter = Regex("""сезон\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val episodeFirst = Regex("""(\d{1,3})\s*$ENDING\s*(?:серия|эпизод)""", RegexOption.IGNORE_CASE)
    private val episodeAfter = Regex("""(?:серия|эпизод)\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
    private val seasonEpisode = Regex("""\bs(\d{1,2})\s*e(\d{1,3})\b""", RegexOption.IGNORE_CASE)

    fun tag(title: String): EpisodeTag? {
        seasonEpisode.find(title)?.let { m ->
            return EpisodeTag(show(title, m.range.first), m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        // A number before "сезон" wins: in "2 сезон 3 серия" the 3 belongs to the episode.
        val season = seasonFirst.find(title) ?: seasonAfter.find(title)
        // The season's own number must not be read as the episode ("Сезон 1 серия 2").
        val rest = season?.let { title.replaceRange(it.range, " ".repeat(it.value.length)) } ?: title
        val episode = episodeFirst.find(rest) ?: episodeAfter.find(rest) ?: return null
        val start = minOf(episode.range.first, season?.range?.first ?: Int.MAX_VALUE)
        return EpisodeTag(show(title, start), season?.groupValues?.get(1)?.toInt(), episode.groupValues[1].toInt())
    }

    /** The next episode of the same series: the next number, or the first one of the next season. */
    fun isNext(current: EpisodeTag, candidate: EpisodeTag): Boolean {
        if (current.show != candidate.show) return false
        if (candidate.season == current.season && candidate.episode == current.episode + 1) return true
        return current.season != null && candidate.season == current.season + 1 && candidate.episode == 1
    }

    /** [candidate] is the next episode of [current] on the same channel. */
    fun isNextEpisode(current: VideoCard, candidate: VideoCard): Boolean {
        if (current.authorId == null || candidate.authorId != current.authorId) return false
        val now = tag(current.title) ?: return false
        val then = tag(candidate.title) ?: return false
        return isNext(now, then)
    }

    /** Search queries for the next episode: the same season first, then the start of the next one. */
    fun searchQueries(tag: EpisodeTag): List<String> = when (tag.season) {
        null -> listOf("${tag.show} ${tag.episode + 1} серия")
        else -> listOf("${tag.show} ${tag.season} сезон ${tag.episode + 1} серия", "${tag.show} ${tag.season + 1} сезон 1 серия")
    }

    private fun show(title: String, end: Int): String =
        title.substring(0, end).lowercase().replace('ё', 'е').replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}
