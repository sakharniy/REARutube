@file:OptIn(UnstableApi::class)

package com.matik.reatube.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.matik.reatube.R
import com.matik.reatube.RemoteKeys
import com.matik.reatube.api.PlaySource
import com.matik.reatube.api.PlayerMarks
import com.matik.reatube.api.VideoCard
import com.matik.reatube.data.PlaybackSession
import com.matik.reatube.diag.Diagnostics
import com.matik.reatube.nav.Route
import com.matik.reatube.net.Http
import com.matik.reatube.net.catching
import com.matik.reatube.net.describe
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(video: VideoCard, onPlayNext: (VideoCard) -> Unit, onOpen: (Route) -> Unit) {
    val api = LocalServices.current.api
    var source by remember(video.id) { mutableStateOf<PlaySource?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var paywall by remember(video.id) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(video.id, attempt) {
        error = null
        // Once on the emulator an episode came without a stream and opened fine on retry: ask twice before giving up.
        suspend fun load() = api.playOptions(video.id).let { first ->
            if (first.hlsUrl != null || first.paywall != null || first.hasDrm) first else {
                delay(1500)
                api.playOptions(video.id)
            }
        }
        catching { load() }
            .onSuccess {
                when {
                    it.hlsUrl == null && it.paywall != null -> paywall = it.paywall
                    it.hlsUrl == null && it.hasDrm -> error = "Видео защищено (DRM), его можно смотреть только в официальном приложении"
                    it.hlsUrl == null -> error = "Видео сейчас недоступно"
                    else -> source = it
                }
            }
            .onFailure { error = it.describe() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val playable = source
        when {
            playable != null -> VideoSurface(video, playable, onPlayNext, onOpen)
            paywall != null -> PaywallBox(paywall.orEmpty(), Modifier.align(Alignment.Center))
            error != null -> ErrorBox(error.orEmpty(), onRetry = { attempt++ }, modifier = Modifier.align(Alignment.Center))
            else -> Loading(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun VideoSurface(video: VideoCard, source: PlaySource, onPlayNext: (VideoCard) -> Unit, onOpen: (Route) -> Unit) {
    val context = LocalContext.current
    val services = LocalServices.current
    val stats = remember { SessionStats() }
    val player = remember(source.hlsUrl) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Http.client)))
            // By default Media3 drops what has been played, so every ← downloaded the chunk and the 50 s ahead again.
            .setLoadControl(DefaultLoadControl.Builder().setBackBuffer(if (source.isLive) 0 else BACK_BUFFER_MS, true).build())
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            .apply {
                addAnalyticsListener(stats)
                setMediaItem(MediaItem.Builder().setUri(source.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                if (!source.isLive) services.library.resumePositionMs(video.id).takeIf { it > 0 }?.let(::seekTo)
                prepare()
                playWhenReady = true
            }
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }

    // Intro/credits marks and the next video load along with the stream; live streams have neither.
    // Next is the episode from the credits marks, else the next episode of a series, else Rutube's suggestion.
    var marks by remember { mutableStateOf<PlayerMarks?>(null) }
    var next by remember { mutableStateOf<VideoCard?>(null) }
    var nextIsEpisode by remember { mutableStateOf(false) }
    LaunchedEffect(video.id) {
        if (source.isLive) return@LaunchedEffect
        val api = services.api
        val loaded = video.authorId?.let { author -> catching { api.marks(video.id, author) }.getOrNull() }
        marks = loaded
        val fromMarks = loaded?.credits?.let { catching { api.video(it.nextVideoId) }.getOrNull() }
        val suggested = if (fromMarks != null) null else catching { api.nextVideo(video.id) }.getOrNull()
        val episode = fromMarks ?: catching { api.nextEpisode(video, suggested) }.getOrNull()
        next = episode ?: suggested
        nextIsEpisode = episode != null
    }
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, marks) {
        if (marks?.intro == null && marks?.credits == null) return@LaunchedEffect
        while (true) {
            positionMs = player.currentPosition
            delay(500)
        }
    }
    var introDismissed by remember { mutableStateOf(false) }
    var creditsDismissed by remember { mutableStateOf(false) }
    val overlay by remember {
        derivedStateOf {
            val intro = marks?.intro
            val credits = marks?.credits
            // A subscription episode would only open the paywall: no "Следующая серия" for it.
            val freeNext = next?.takeUnless { it.isPaid }
            when {
                settingsOpen || ended -> null
                intro != null && !introDismissed && positionMs in intro.startMs until intro.endMs -> Overlay.SkipIntro
                credits != null && freeNext != null && !creditsDismissed && positionMs in credits.startMs until credits.endMs ->
                    Overlay.NextEpisode
                else -> null
            }
        }
    }
    val overlayFocus = remember { FocusRequester() }

    fun saveProgress() {
        val position = if (source.isLive) 0 else player.currentPosition
        services.library.recordProgress(video, position, if (source.isLive) 0 else player.duration)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                ended = playbackState == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        RemoteKeys.onMenu = { settingsOpen = !settingsOpen }
        // ←/→ with the panel hidden seek right away (YouTube-style) and leave focus on the time bar;
        // holding the key on the time bar scrubs faster and faster, the seek happens on release.
        RemoteKeys.onKey = handler@{ event ->
            val view = playerView
            if (settingsOpen || view == null || source.isLive) return@handler false
            val forward = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                KeyEvent.KEYCODE_DPAD_LEFT -> false
                else -> return@handler false
            }
            if (view.isControllerFullyVisible) {
                val timeBar = view.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
                if (timeBar?.isFocused == true && event.action == KeyEvent.ACTION_DOWN) {
                    timeBar.setKeyTimeIncrement(seekStep(event.repeatCount, player.duration))
                }
                return@handler false
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (forward) player.seekForward() else player.seekBack()
                view.showController()
                view.findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.requestFocus()
            }
            true
        }
        saveProgress()
        onDispose {
            RemoteKeys.onMenu = null
            RemoteKeys.onKey = null
            saveProgress()
            services.playbackLog.add(stats.session(video, source))
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) saveProgress()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                        // The focused button disappears with the panel and focus falls to the Compose root,
                        // where OK and the arrows reach nothing; take it back ("Пропустить" gets it if shown).
                        if (visibility != View.VISIBLE && !settingsOpen && !ended) post {
                            if (overlay == null || runCatching { overlayFocus.requestFocus() }.isFailure) requestFocus()
                        }
                    },
                )
                // The default time bar jumps 1/20 of the video per press (3 min on an hour-long episode).
                findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.setKeyTimeIncrement(SEEK_STEP_MS)
                // The gear opens our panel: Media3's own settings have speed but no video quality.
                findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener { settingsOpen = true }
                // The channel and the next episode go next to the gear as plain Views, so the remote reaches them
                // like the other buttons. The player stays under the channel page: Back returns to the video.
                findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls)?.let { bar ->
                    val nextEpisode = pillButton(viewContext, "Следующая серия", R.drawable.ic_next) {}
                    bar.addView(nextEpisode.apply { tag = NEXT_EPISODE_TAG; visibility = View.GONE }, 0)
                    val channelId = video.authorId
                    if (channelId != null) {
                        val name = video.author.orEmpty()
                        bar.addView(pillButton(viewContext, name.ifEmpty { "Канал" }, R.drawable.ic_channel) { onOpen(Route.Channel(channelId, name)) }, 0)
                    }
                }
                post { requestFocus() }
                playerView = this
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
    // "Следующая серия" appears once the next episode is known; subscription episodes are left out.
    LaunchedEffect(playerView, next, nextIsEpisode) {
        val target = next?.takeIf { nextIsEpisode && !it.isPaid }
        playerView?.findViewWithTag<View>(NEXT_EPISODE_TAG)?.apply {
            visibility = if (target != null) View.VISIBLE else View.GONE
            setOnClickListener { target?.let(onPlayNext) }
        }
    }
    // At the end the panel pops up and takes focus from the end screen's card: switch it off while that card shows.
    // Set here and not in AndroidView's update block: that block re-ran on every recomposition and broke the panel.
    LaunchedEffect(playerView, ended, next) {
        playerView?.useController = !(ended && next != null)
    }

    if (controlsVisible && !settingsOpen) TitleBar(video)
    // Gone with its time window or with Back; then the player gets focus back unless its panel is in use.
    val returnFocus = { if (!controlsVisible && !settingsOpen) playerView?.requestFocus() }
    val shownMarks = marks
    when (overlay) {
        Overlay.SkipIntro -> shownMarks?.intro?.let { intro ->
            SkipIntro(
                focus = overlayFocus,
                takeFocus = !controlsVisible,
                onSkip = {
                    introDismissed = true
                    player.seekTo(intro.targetMs)
                },
                onDismiss = { introDismissed = true },
                onGone = returnFocus,
            )
        }
        Overlay.NextEpisode -> next?.let { target ->
            NextEpisode(
                next = target,
                autoSwitchMs = shownMarks?.credits?.autoSwitchMs,
                focus = overlayFocus,
                takeFocus = !controlsVisible,
                isPlaying = { player.isPlaying },
                onPlay = { onPlayNext(target) },
                onDismiss = { creditsDismissed = true },
                onGone = returnFocus,
            )
        }
        null -> Unit
    }
    if (settingsOpen) {
        SettingsPanel(player, onClose = {
            settingsOpen = false
            // Opened by ☰ over a hidden panel, the gear can't take focus; then the player takes it, or OK reaches nothing.
            playerView?.let { view ->
                if (view.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.requestFocus() != true) view.requestFocus()
            }
        })
    }
    if (ended && !source.isLive && !settingsOpen) NextUp(video, next, isEpisode = nextIsEpisode, onPlayNext)
}

private enum class Overlay { SkipIntro, NextEpisode }

/** Subscription-only video: we do not play paid content, so a plain notice instead of "Повторить". */
@Composable
private fun PaywallBox(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Это видео — только по подписке", style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(16.dp))
        Text("«Назад» — вернуться", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

private const val NEXT_EPISODE_TAG = "next-episode"

/** A pill in the player's bottom bar (channel, next episode); white with black text when focused, like our tabs. */
private fun pillButton(context: Context, text: String, icon: Int, onClick: () -> Unit): TextView {
    val density = context.resources.displayMetrics.density
    fun px(dp: Int) = (dp * density).toInt()
    val colors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE),
    )
    fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = px(20).toFloat()
        setColor(color)
    }
    return TextView(context).apply {
        this.text = text
        textSize = 16f
        setTextColor(colors)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = px(320)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(12), 0, px(16), 0)
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        compoundDrawablePadding = px(8)
        compoundDrawableTintList = colors
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), pill(android.graphics.Color.WHITE))
            addState(intArrayOf(), pill(0x33FFFFFF))
        }
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40)).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginEnd = px(8)
        }
    }
}

/** Focus for "Пропустить"/"Следующая серия": taken on appearing unless the player's panel is in use, given back on leaving. */
@Composable
private fun OverlayFocus(focus: FocusRequester, takeFocus: Boolean, onGone: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (takeFocus) runCatching { focus.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose(onGone) }
}

/** Over a series intro, like the official app; Back hides it ("Смотреть заставку"). */
@Composable
private fun SkipIntro(focus: FocusRequester, takeFocus: Boolean, onSkip: () -> Unit, onDismiss: () -> Unit, onGone: () -> Unit) {
    BackHandler(onBack = onDismiss)
    OverlayFocus(focus, takeFocus, onGone)
    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 120.dp), contentAlignment = Alignment.BottomEnd) {
        Button(onClick = onSkip, modifier = Modifier.focusRequester(focus)) { Text("Пропустить заставку") }
    }
}

/**
 * Over the end credits: the next episode, started by itself after the server's countdown (6 s on START series).
 * The countdown waits while the video is paused; Back keeps the credits ("Смотреть титры").
 */
@Composable
private fun NextEpisode(
    next: VideoCard,
    autoSwitchMs: Long?,
    focus: FocusRequester,
    takeFocus: Boolean,
    isPlaying: () -> Boolean,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    onGone: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    OverlayFocus(focus, takeFocus, onGone)
    var seconds by remember { mutableIntStateOf(((autoSwitchMs ?: 0L) / 1000).toInt()) }
    if (autoSwitchMs != null) {
        LaunchedEffect(Unit) {
            while (seconds > 0) {
                delay(1000)
                if (isPlaying()) seconds--
            }
            onPlay()
        }
    }
    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 120.dp), contentAlignment = Alignment.BottomEnd) {
        Column(Modifier.width(360.dp).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text(
                if (autoSwitchMs != null) "Следующая серия через $seconds с" else "Следующая серия",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            VideoTile(next, onClick = onPlay, onLongClick = null, progress = null, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            Spacer(Modifier.height(8.dp))
            Text("OK — смотреть сейчас, Назад — досмотреть титры", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

private const val SEEK_STEP_MS = 10_000L

/** Played video kept in memory for an instant ←: ~28 MB at 1080p. Live streams cannot be rewound, so they keep none. */
private const val BACK_BUFFER_MS = 30_000

/**
 * Scrub step for a held ←/→: 10 s, then 30 s, 1 min, 2 min as the key repeats (~20 repeats a second).
 * Never more than 1/30 of the video, so a short clip is not skipped in one step.
 */
internal fun seekStep(repeatCount: Int, durationMs: Long): Long {
    val step = when {
        repeatCount < 10 -> SEEK_STEP_MS
        repeatCount < 30 -> 30_000L
        repeatCount < 60 -> 60_000L
        else -> 120_000L
    }
    return if (durationMs > 0) minOf(step, maxOf(SEEK_STEP_MS, durationMs / 30)) else step
}

@Composable
private fun TitleBar(video: VideoCard) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        Text(video.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        video.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
        Text(
            "Шестерёнка или ☰ — качество и скорость, ←/→ — перемотка на 10 с, внизу — канал и следующая серия",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

private data class QualityOption(val label: String, val group: TrackGroup?, val index: Int, val selected: Boolean)

private class VideoTrack(val group: Tracks.Group, val index: Int, val format: Format)

private fun qualityOptions(player: Player): List<QualityOption> {
    val auto = player.trackSelectionParameters.overrides.keys.none { it.type == C.TRACK_TYPE_VIDEO }
    val tracks = player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group -> (0 until group.length).filter(group::isTrackSupported).map { VideoTrack(group, it, group.getTrackFormat(it)) } }
        .sortedWith(compareByDescending<VideoTrack> { it.format.width * it.format.height }.thenByDescending { it.format.bitrate })
        // The master playlist lists each size twice; one button per name, the best track behind it.
        .groupBy { qualityLabel(it.format.width, it.format.height) }
        .map { (label, same) ->
            val best = same.first()
            QualityOption(label, best.group.mediaTrackGroup, best.index, !auto && same.any { it.group.isTrackSelected(it.index) })
        }
    return listOf(QualityOption("Авто", null, -1, auto)) + tracks
}

private val standardSizes = listOf(144 to 256, 240 to 426, 360 to 640, 480 to 854, 720 to 1280, 1080 to 1920, 1440 to 2560, 2160 to 3840)

/**
 * Names a frame size the YouTube way, by the 16:9 frame it fits in: a 2:1 film at 1920×960 is "1080p", not "960p",
 * and a vertical 720×1280 clip is "720p". 3% slack covers encoder sizes like 856×424 and 1920×1088.
 */
internal fun qualityLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "—"
    val long = maxOf(width, height)
    val short = minOf(width, height)
    val size = standardSizes.firstOrNull { (h, w) -> long <= w * 1.03 && short <= h * 1.03 } ?: standardSizes.last()
    return "${size.first}p"
}

private fun applyQuality(player: Player, option: QualityOption) {
    val parameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    option.group?.let { parameters.setOverrideForType(TrackSelectionOverride(it, option.index)) }
    player.trackSelectionParameters = parameters.build()
}

private val speeds = listOf(0.75f to "0.75×", 1f to "Обычная", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×")

/** Opened by the gear in the player's panel or by the remote's ☰ key. */
@Composable
private fun SettingsPanel(player: Player, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val qualities = remember { qualityOptions(player) }
    val currentSpeed = player.playbackParameters.speed
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.width(320.dp).fillMaxHeight().background(Panel).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Качество", style = MaterialTheme.typography.titleLarge)
            qualities.forEachIndexed { i, option ->
                Button(
                    onClick = {
                        applyQuality(player, option)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) {
                    Text(if (option.selected) "${option.label}  ✓" else option.label)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Скорость", style = MaterialTheme.typography.titleLarge)
            speeds.forEach { (speed, label) ->
                Button(
                    onClick = {
                        player.setPlaybackSpeed(speed)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (speed == currentSpeed) "$label  ✓" else label)
                }
            }
        }
    }
}

/**
 * Rutube-style end screen: the next video starts by itself after a countdown. [preloaded] came with the stream.
 * A subscription video is only shown, never started by itself.
 */
@Composable
private fun NextUp(current: VideoCard, preloaded: VideoCard?, isEpisode: Boolean, onPlayNext: (VideoCard) -> Unit) {
    val api = LocalServices.current.api
    var next by remember { mutableStateOf(preloaded) }
    var seconds by remember { mutableIntStateOf(10) }
    LaunchedEffect(current.id) { if (next == null) next = catching { api.nextVideo(current.id) }.getOrNull() }
    val target = next ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(target.id) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
        if (target.isPaid) return@LaunchedEffect
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
        onPlayNext(target)
    }
    val what = if (isEpisode) "Следующая серия" else "Далее"
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (target.isPaid) "$what — только по подписке RUTUBE" else "$what через $seconds с",
                style = MaterialTheme.typography.titleMedium,
                color = Muted,
            )
            Spacer(Modifier.height(12.dp))
            VideoTile(target, onClick = { onPlayNext(target) }, onLongClick = null, progress = null, modifier = Modifier.width(360.dp).focusRequester(focus))
        }
    }
}

/** Collected on the main thread by ExoPlayer callbacks; saved to the playback log when the player closes. */
private class SessionStats : AnalyticsListener {
    private var decoder: String? = null
    private var droppedFrames = 0
    private var maxPixels = 0
    private var quality: String? = null
    private var error: String? = null

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        decoder = decoderName
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: androidx.media3.common.Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        val pixels = format.width * format.height
        if (pixels > maxPixels) {
            maxPixels = pixels
            quality = qualityLabel(format.width, format.height)
        }
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        this.droppedFrames += droppedFrames
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        this.error = "${error.errorCodeName}: ${error.cause?.message ?: error.message}"
    }

    fun session(video: VideoCard, source: PlaySource) = PlaybackSession(
        at = System.currentTimeMillis(),
        title = video.title.ifEmpty { source.title },
        live = source.isLive,
        quality = quality,
        decoder = decoder?.let { "$it (${if (Diagnostics.isHardwareName(it)) "аппаратный" else "программный"})" },
        droppedFrames = droppedFrames,
        error = error,
    )
}
