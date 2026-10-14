package com.matik.reatube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.matik.reatube.nav.Route
import com.matik.reatube.net.catching
import kotlinx.coroutines.delay

// Our own keyboard: the China-market firmware may have no Russian layout in its system keyboard.
private val RussianRows = listOf("абвгдеёжзий", "клмнопрстуф", "хцчшщъыьэюя")
private val LatinRows = listOf("abcdefghijklm", "nopqrstuvwxyz")
private const val DIGITS = "1234567890"

@Composable
fun SearchScreen(onOpen: (Route) -> Unit) {
    val api = LocalServices.current.api
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var latin by rememberSaveable { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(text) {
        val typed = text.trim()
        suggestions = if (typed.length < 2) {
            emptyList()
        } else {
            delay(300)
            catching { api.suggestions(typed) }.getOrDefault(emptyList()).take(6)
        }
    }
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    if (query.isNotEmpty()) {
        key(query) {
            PagedGrid(api.searchUrl(query), onOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ScreenTitle("Поиск: «$query»") }
                    Button(onClick = { query = "" }) { Text("Изменить запрос") }
                }
            }
        }
        return
    }

    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstKey.requestFocus() }
    }
    val submit = { value: String -> if (value.isNotBlank()) { text = value; query = value.trim() } }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Поиск")
        Box(
            Modifier.width(560.dp).height(52.dp).background(Panel, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                if (text.isEmpty()) "Набери запрос кнопками ниже" else "$text▏",
                style = MaterialTheme.typography.titleLarge,
                color = if (text.isEmpty()) Muted else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (latin) LatinRows else RussianRows).forEachIndexed { rowIndex, letters ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        letters.forEachIndexed { i, ch ->
                            Key(ch.toString(), modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstKey) else Modifier) {
                                text += ch
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DIGITS.forEach { ch -> Key(ch.toString()) { text += ch } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Key("Пробел", width = 120.dp) { text += " " }
                    Key("⌫", width = 64.dp) { text = text.dropLast(1) }
                    Key("Очистить", width = 110.dp) { text = "" }
                    Key(if (latin) "РУС" else "ENG", width = 72.dp) { latin = !latin }
                    Key("Найти", width = 96.dp, accent = true) { submit(text) }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 280.dp)) {
                suggestions.forEach { suggestion ->
                    Button(onClick = { submit(suggestion) }) { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun Key(
    label: String,
    width: Dp = 40.dp,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).height(40.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) Accent else Panel,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
