@file:OptIn(ExperimentalComposeUiApi::class)

package com.matik.reatube.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.matik.reatube.api.NavGroup
import com.matik.reatube.nav.Links
import com.matik.reatube.nav.Route
import com.matik.reatube.nav.SavedKind
import com.matik.reatube.net.catching

private data class MenuEntry(val title: String, val route: Route, val iconUrl: String?, val fallback: ImageVector)

/** Which menu item a screen belongs to; titles differ between the server menu and our defaults. */
fun Route.menuKey(): String = when (this) {
    is Route.Feed -> "feed:$slug"
    is Route.Saved -> "saved:$kind"
    else -> javaClass.simpleName
}

// Used when the server menu cannot be loaded (same items as pangolin's navigation/Android_TV).
private val defaultEntries = listOf(
    MenuEntry("Поиск", Route.Search, null, Icons.Default.Search),
    MenuEntry("Главная", Route.Feed("tab_new_main", "Главная"), null, Icons.Default.Home),
    MenuEntry("Категории", Route.Feed("smart_category", "Категории"), null, Icons.Default.Menu),
    MenuEntry("Кино и сериалы", Route.Feed("movies-serials", "Кино и сериалы"), null, Icons.Default.PlayArrow),
    MenuEntry("Детям", Route.Feed("kids", "Детям"), null, Icons.Default.Face),
    MenuEntry("Спорт", Route.Feed("sport", "Спорт"), null, Icons.Default.Star),
    MenuEntry("ТВ-каналы", Route.Feed("live_smarttv", "ТВ-каналы"), null, Icons.Default.PlayArrow),
)

private fun fallbackIcon(route: Route): ImageVector = when (route) {
    Route.Search -> Icons.Default.Search
    is Route.Saved -> when (route.kind) {
        SavedKind.Subscriptions -> Icons.Default.Person
        SavedKind.WatchLater -> Icons.Default.DateRange
        SavedKind.History -> Icons.Default.Refresh
    }
    is Route.Feed -> if (route.slug == "tab_new_main") Icons.Default.Home else Icons.Default.Star
    else -> Icons.Default.Home
}

private fun menuEntries(groups: List<NavGroup>): List<MenuEntry> {
    val server = groups.flatMap { it.links }.mapNotNull { link ->
        Links.route(link.url, link.title)?.let { MenuEntry(link.title, it, link.iconUrl, fallbackIcon(it)) }
    }
    val saved = SavedKind.entries.map { MenuEntry(it.title, Route.Saved(it), null, fallbackIcon(Route.Saved(it))) }
    val diagnostics = MenuEntry("Диагностика", Route.Diagnostics, null, Icons.Default.Info)
    return (server.ifEmpty { defaultEntries } + saved + diagnostics).distinctBy { it.route.menuKey() }
}

@Composable
fun Sidebar(selected: Route, onSelect: (Route) -> Unit, content: @Composable () -> Unit) {
    val api = LocalServices.current.api
    var groups by remember { mutableStateOf<List<NavGroup>>(emptyList()) }
    LaunchedEffect(Unit) { groups = catching { api.navigation() }.getOrDefault(emptyList()) }
    val entries = remember(groups) { menuEntries(groups) }
    val selectedKey = selected.menuKey()
    // Entering the menu from the content lands on the current section, not on the nearest item.
    val selectedItem = remember { FocusRequester() }
    NavigationDrawer(
        drawerContent = {
            Column(
                Modifier.fillMaxHeight()
                    .focusRestorer(selectedItem)
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                entries.forEach { entry ->
                    val isSelected = entry.route.menuKey() == selectedKey
                    NavigationDrawerItem(
                        selected = isSelected,
                        onClick = { onSelect(entry.route) },
                        leadingContent = { MenuIcon(entry) },
                        modifier = if (isSelected) Modifier.focusRequester(selectedItem) else Modifier,
                    ) {
                        Text(entry.title, maxLines = 1)
                    }
                }
            }
        },
        content = content,
    )
}

/** Server icons are glyphs; tint them with the item's content color so they stay visible when focused. */
@Composable
private fun MenuIcon(entry: MenuEntry) {
    if (entry.iconUrl != null) {
        AsyncImage(
            model = entry.iconUrl,
            contentDescription = null,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            modifier = Modifier.size(24.dp),
        )
    } else {
        Icon(entry.fallback, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}
