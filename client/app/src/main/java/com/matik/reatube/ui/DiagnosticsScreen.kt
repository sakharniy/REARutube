package com.matik.reatube.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.matik.reatube.data.PlaybackSession
import com.matik.reatube.diag.DiagSection
import com.matik.reatube.diag.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val services = LocalServices.current
    val local = remember { Diagnostics.local(context) }
    var network by remember { mutableStateOf<DiagSection?>(null) }
    var savedTo by remember { mutableStateOf<List<String>?>(null) }
    val playback = playbackSection(services.playbackLog.sessions)

    LaunchedEffect(Unit) {
        val checked = Diagnostics.network(services.api)
        network = checked
        val report = Diagnostics.report(local + checked + playback)
        savedTo = withContext(Dispatchers.IO) { Diagnostics.save(context, report) }
    }

    val pending = DiagSection("Сеть и серверы Rutube", listOf("Проверка" to "идёт…"))
    val focus = rememberFocusMemory()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, end = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle("Диагностика", "Листай кнопками вниз и сфотографируй блоки. Воспроизведения записываются сами.")
        }
        items(local + (network ?: pending), key = { it.title }) { section ->
            SectionCard(section, Modifier.remembersFocus(focus, section.title, isFirst = section === local.first()))
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "playback") {
            SectionCard(playback, Modifier.remembersFocus(focus, "playback", isFirst = false))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val text = when (val paths = savedTo) {
                null -> "Отчёт сохранится после проверки сети…"
                emptyList<String>() -> "Отчёт сохранить не удалось"
                else -> "Отчёт сохранён в файл ${Diagnostics.REPORT_FILE}:\n" + paths.joinToString("\n")
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

private fun playbackSection(sessions: List<PlaybackSession>): DiagSection {
    val time = SimpleDateFormat("dd.MM HH:mm", Locale.ROOT)
    val rows = sessions.take(5).map { s ->
        val details = listOfNotNull(
            if (s.live) "эфир" else null,
            s.quality,
            s.decoder,
            "потеряно кадров: ${s.droppedFrames}",
            s.error?.let { "ошибка: $it" },
        ).joinToString(", ")
        time.format(Date(s.at)) to "${s.title.take(50)}: $details"
    }
    return DiagSection("Последние воспроизведения", rows.ifEmpty { listOf("—" to "пока ничего не смотрели") })
}

/** Focusable so the remote can scroll through the grid; it does nothing on click. */
@Composable
private fun SectionCard(section: DiagSection, modifier: Modifier) {
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panel,
            contentColor = Color.White,
            focusedContainerColor = PanelFocused,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent))),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, color = Accent)
            Spacer(Modifier.height(8.dp))
            section.rows.forEach { (label, value) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.width(140.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
