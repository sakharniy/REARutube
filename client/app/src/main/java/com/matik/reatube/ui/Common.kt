package com.matik.reatube.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.matik.reatube.RemoteKeys
import com.matik.reatube.api.Item
import com.matik.reatube.api.Page
import com.matik.reatube.api.RutubeApi
import com.matik.reatube.data.Library
import com.matik.reatube.data.PlaybackLog
import com.matik.reatube.net.describe
import kotlinx.coroutines.CancellationException

data class Services(val api: RutubeApi, val library: Library, val playbackLog: PlaybackLog)

val LocalServices = staticCompositionLocalOf<Services> { error("Services are not provided") }

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text("Загрузка…", color = Muted)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val retry = remember { FocusRequester() }
    Column(
        modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Не получилось загрузить", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, modifier = Modifier.focusRequester(retry)) { Text("Повторить") }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { retry.requestFocus() }
    }
}

/** Loads a paged list; every page carries the URL of the next one. [cached] is the first page if already in memory. */
class Pager(firstUrl: String, cached: Page?, private val load: suspend (String) -> Page) {
    val items = mutableStateListOf<Item>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var finished by mutableStateOf(false)
        private set
    private var nextUrl: String? = firstUrl
    private val keys = HashSet<String>()

    init {
        cached?.let { add(firstUrl, it) }
    }

    private fun add(url: String, page: Page) {
        items.addAll(page.items.filter { keys.add(it.key) })
        nextUrl = page.next?.takeIf { it != url && page.items.isNotEmpty() }
        if (nextUrl == null) finished = true
    }

    suspend fun loadMore() {
        val url = nextUrl ?: return
        if (loading) return
        loading = true
        error = null
        try {
            add(url, load(url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.describe()
        } finally {
            loading = false
        }
    }
}

@Composable
fun rememberPager(url: String, api: RutubeApi): Pager {
    val pager = remember(url) { Pager(url, api.cachedPage(url)) { api.page(it) } }
    LaunchedEffect(pager) { if (pager.items.isEmpty()) pager.loadMore() }
    return pager
}

/**
 * Remembers which card had the remote's focus, so it gets it back after returning from the
 * player; on the first visit the screen focuses its first card instead. Both happen only if the
 * user has not pressed anything since the screen opened: rows load at different speeds, and a
 * late row must not pull focus away from where the user already is.
 */
class FocusMemory(private val savedKey: String?, private val save: (String) -> Unit) {
    private val openedAt = SystemClock.uptimeMillis()
    private var restoring = savedKey != null
    private var initialPending = savedKey == null

    fun wants(key: String, isFirst: Boolean): Boolean =
        (restoring && key == savedKey) || (initialPending && isFirst)

    fun untouched(): Boolean = RemoteKeys.lastKeyDownAt < openedAt

    fun done() {
        restoring = false
        initialPending = false
    }

    fun focused(key: String) = save(key)
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    var savedKey by rememberSaveable { mutableStateOf<String?>(null) }
    return remember { FocusMemory(savedKey) { savedKey = it } }
}

/** Attach to a card: reports its focus and takes focus when [FocusMemory] asks for it. */
@Composable
fun Modifier.remembersFocus(memory: FocusMemory, key: String, isFirst: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    // Only the one card that needs focus launches an effect; dozens of cards per row would otherwise each start a coroutine.
    if (memory.wants(key, isFirst)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!memory.untouched()) memory.done() else if (runCatching { requester.requestFocus() }.isSuccess) memory.done()
        }
    }
    return focusRequester(requester).onFocusChanged { if (it.hasFocus) memory.focused(key) }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f тыс.".format(n / 1_000.0)
    else -> n.toString()
}
