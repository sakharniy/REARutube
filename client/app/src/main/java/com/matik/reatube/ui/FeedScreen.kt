@file:OptIn(ExperimentalComposeUiApi::class)

package com.matik.reatube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.matik.reatube.api.Feed
import com.matik.reatube.api.FeedRow
import com.matik.reatube.nav.Links
import com.matik.reatube.nav.Route
import com.matik.reatube.net.catching
import com.matik.reatube.net.describe

@Composable
fun FeedScreen(slug: String, onOpen: (Route) -> Unit) {
    val api = LocalServices.current.api
    var feed by remember(slug) { mutableStateOf<Feed?>(null) }
    var error by remember(slug) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val focus = rememberFocusMemory() // created when the screen opens, before anything loads
    LaunchedEffect(slug, attempt) {
        error = null
        catching { api.feed(slug) }.onSuccess { feed = it }.onFailure { error = it.describe() }
    }
    val loaded = feed
    when {
        loaded != null -> FeedContent(loaded, focus, onOpen)
        error != null -> ErrorBox(error.orEmpty(), onRetry = { attempt++ })
        else -> Loading()
    }
}

@Composable
private fun FeedContent(feed: Feed, focus: FocusMemory, onOpen: (Route) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(feed.tabs.indexOfFirst { it.name == "Главная" }.coerceAtLeast(0)) }
    val tab = feed.tabs.getOrNull(selected) ?: return
    Column(Modifier.fillMaxSize()) {
        if (feed.tabs.size > 1) TabStrip(feed.tabs.map { it.name }, selected) { selected = it }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(tab.rows, key = { _, row -> "$selected|${row.url}" }) { index, row ->
                FeedRowView(row, focus, isFirstRow = index == 0, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun TabStrip(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.focusRestorer(),
        contentPadding = PaddingValues(bottom = 8.dp, end = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(names) { index, name ->
            val isSelected = index == selected
            Surface(
                onClick = { onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Accent.copy(alpha = 0.3f) else Color.Transparent,
                    contentColor = if (isSelected) Color.White else Muted,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun FeedRowView(row: FeedRow, focus: FocusMemory, isFirstRow: Boolean, onOpen: (Route) -> Unit) {
    val api = LocalServices.current.api
    val pager = rememberPager(api.rowUrl(row), api)
    // The server sends empty rows sometimes, and account-only rows ("Новое из подписок") answer HTTP 404.
    val accountOnly = pager.error?.contains("HTTP 4") == true
    if (pager.items.isEmpty() && (pager.finished || accountOnly)) return
    Column {
        Text(row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        when {
            pager.items.isNotEmpty() -> LazyRow(
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(end = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(pager.items, key = { _, item -> item.key }) { index, item ->
                    if (index >= pager.items.size - 5) LaunchedEffect(index) { pager.loadMore() }
                    ItemCard(
                        item = item,
                        onClick = { Links.route(item)?.let(onOpen) },
                        modifier = Modifier
                            .width(if (item is com.matik.reatube.api.ChannelItem) 140.dp else CardWidth)
                            .remembersFocus(focus, "${row.url}|${item.key}", isFirst = isFirstRow && index == 0),
                    )
                }
            }
            pager.error != null -> Text("Не загрузилось: ${pager.error}", color = Muted, style = MaterialTheme.typography.bodySmall)
            else -> RowPlaceholder()
        }
    }
}

/** Same height as a row of cards, so the list does not jump when the row's items arrive. */
@Composable
private fun RowPlaceholder() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(5) {
            Column(Modifier.width(CardWidth)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Panel, RoundedCornerShape(8.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.9f).height(14.dp).background(Panel, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(Panel, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).background(Panel, RoundedCornerShape(4.dp)))
            }
        }
    }
}
