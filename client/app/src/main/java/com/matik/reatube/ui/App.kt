package com.matik.reatube.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.matik.reatube.nav.Route

private val Home = Route.Feed("tab_new_main", "Главная")

@Composable
fun App() {
    ReaTheme {
        Surface(modifier = Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(containerColor = Background)) {
            val stack = remember { mutableStateListOf<Route>(Home) }
            // Keeps each screen's scroll position and focused card while it waits under the player.
            val states = rememberSaveableStateHolder()
            val context = LocalContext.current
            var lastBackAt by remember { mutableLongStateOf(0L) }

            fun keyAt(depth: Int) = "$depth:${stack[depth]}"
            fun open(route: Route) {
                stack.add(route)
            }
            fun back() {
                states.removeState(keyAt(stack.lastIndex))
                stack.removeAt(stack.lastIndex)
            }
            fun selectRoot(route: Route) {
                if (stack.size == 1 && stack[0].menuKey() == route.menuKey()) return
                stack.indices.forEach { states.removeState(keyAt(it)) }
                stack.clear()
                stack.add(route)
            }
            fun playNext(route: Route) {
                back()
                open(route)
            }

            BackHandler {
                if (stack.size > 1) {
                    back()
                } else if (SystemClock.uptimeMillis() - lastBackAt < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackAt = SystemClock.uptimeMillis()
                    Toast.makeText(context, "Нажми «Назад» ещё раз, чтобы выйти", Toast.LENGTH_SHORT).show()
                }
            }

            val depth = stack.lastIndex
            val route = stack.last()
            if (route is Route.Player) {
                states.SaveableStateProvider(keyAt(depth)) {
                    PlayerScreen(route.video, onPlayNext = { playNext(Route.Player(it)) }, onOpen = ::open)
                }
            } else {
                Sidebar(selected = stack.first(), onSelect = ::selectRoot) {
                    states.SaveableStateProvider(keyAt(depth)) {
                        Box(Modifier.fillMaxSize().padding(start = 24.dp, top = 24.dp)) {
                            ScreenContent(route, onOpen = ::open)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(route: Route, onOpen: (Route) -> Unit) {
    when (route) {
        is Route.Feed -> FeedScreen(route.slug, onOpen)
        is Route.Channel -> ChannelScreen(route.id, route.name, onOpen)
        is Route.Show -> ShowScreen(route.id, route.name, onOpen)
        Route.Search -> SearchScreen(onOpen)
        is Route.Saved -> SavedScreen(route.kind, onOpen)
        Route.Diagnostics -> DiagnosticsScreen()
        is Route.Player -> Unit
    }
}
