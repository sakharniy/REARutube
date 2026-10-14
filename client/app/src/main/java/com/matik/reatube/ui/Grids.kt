package com.matik.reatube.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.matik.reatube.api.ChannelInfo
import com.matik.reatube.api.ChannelItem
import com.matik.reatube.api.Item
import com.matik.reatube.api.VideoItem
import com.matik.reatube.data.ChannelRef
import com.matik.reatube.nav.Links
import com.matik.reatube.nav.Route
import com.matik.reatube.nav.SavedKind
import com.matik.reatube.net.catching
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A grid of cards with a header; [onNearEnd] asks for the next page. */
@Composable
fun ItemGrid(
    items: List<Item>,
    onOpen: (Route) -> Unit,
    header: @Composable () -> Unit,
    footer: String? = null,
    onNearEnd: () -> Unit = {},
    onLongClick: ((Item) -> Unit)? = null,
) {
    val focus = rememberFocusMemory()
    val channels = items.isNotEmpty() && items.all { it is ChannelItem }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (channels) 6 else 4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp, end = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            if (index >= items.size - 8) LaunchedEffect(index) { onNearEnd() }
            ItemCard(
                item = item,
                onClick = { Links.route(item)?.let(onOpen) },
                modifier = Modifier.remembersFocus(focus, item.key, isFirst = index == 0),
                onLongClick = onLongClick?.let { { it(item) } },
            )
        }
        footer?.let { item(span = { GridItemSpan(maxLineSpan) }) { Text(it, color = Muted) } }
    }
}

@Composable
fun PagedGrid(firstUrl: String, onOpen: (Route) -> Unit, header: @Composable () -> Unit) {
    val api = LocalServices.current.api
    val pager = rememberPager(firstUrl, api)
    val scope = rememberCoroutineScope()
    val footer = when {
        pager.error != null -> "Не загрузилось: ${pager.error}"
        pager.loading -> "Загрузка…"
        pager.finished && pager.items.isEmpty() -> "Здесь пока пусто"
        else -> null
    }
    ItemGrid(pager.items, onOpen, header, footer, onNearEnd = { scope.launch { pager.loadMore() } })
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
    }
}

@Composable
fun ChannelScreen(id: String, name: String, onOpen: (Route) -> Unit) {
    val services = LocalServices.current
    var info by remember(id) { mutableStateOf<ChannelInfo?>(null) }
    LaunchedEffect(id) { info = catching { services.api.channel(id) }.getOrNull() }
    val subscribed = services.library.isSubscribed(id)
    PagedGrid(services.api.channelVideosUrl(id), onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            AsyncImage(
                model = info?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val details = listOfNotNull(
                    info?.subscribers?.let { "${formatCount(it)} подписчиков" },
                    info?.videoCount?.let { "${formatCount(it)} видео" },
                ).joinToString(" · ")
                ScreenTitle(info?.name ?: name, details.ifEmpty { null })
            }
            Button(onClick = { services.library.toggleSubscription(ChannelRef(id, info?.name ?: name, info?.avatarUrl)) }) {
                Text(if (subscribed) "Вы подписаны" else "Подписаться")
            }
        }
    }
}

@Composable
fun ShowScreen(id: String, name: String, onOpen: (Route) -> Unit) {
    val api = LocalServices.current.api
    PagedGrid(api.showVideosUrl(id), onOpen) { ScreenTitle(name) }
}

@Composable
fun SavedScreen(kind: SavedKind, onOpen: (Route) -> Unit) {
    val library = LocalServices.current.library
    val context = LocalContext.current
    val items: List<Item> = when (kind) {
        SavedKind.History -> library.history.map { VideoItem(it.video) }
        SavedKind.WatchLater -> library.watchLater.map { VideoItem(it) }
        SavedKind.Subscriptions -> library.subscriptions.map { ChannelItem(it.id, it.name, it.avatarUrl) }
    }
    val hint = when (kind) {
        SavedKind.History -> if (items.isEmpty()) "Здесь появятся видео, которые ты смотрел" else "Долгое нажатие OK — убрать видео из истории"
        SavedKind.WatchLater -> if (items.isEmpty()) "Добавить видео: долгое нажатие OK на карточке" else null
        SavedKind.Subscriptions -> if (items.isEmpty()) "Подписаться: кнопка на странице канала или долгое нажатие OK на канале" else null
    }
    // In history a long press removes the video instead of adding it to "watch later".
    val removeFromHistory: ((Item) -> Unit)? = if (kind != SavedKind.History) null else { item ->
        (item as? VideoItem)?.let {
            library.removeFromHistory(it.video.id)
            Toast.makeText(context, "Удалено из истории", Toast.LENGTH_SHORT).show()
        }
    }
    ItemGrid(
        items = items,
        onOpen = onOpen,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle(kind.title, "Хранится на этом телевизоре") }
                if (kind == SavedKind.History && items.isNotEmpty()) ClearHistoryButton(onClear = library::clearHistory)
            }
        },
        footer = hint,
        onLongClick = removeFromHistory,
    )
}

/** Asks for a second press before wiping the whole history. */
@Composable
private fun ClearHistoryButton(onClear: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }
    Button(onClick = { if (armed) onClear() else armed = true }) {
        Text(if (armed) "Нажми ещё раз, чтобы очистить" else "Очистить историю")
    }
}
