package com.matik.reatube.diag

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.webkit.WebView
import androidx.core.content.pm.PackageInfoCompat
import com.matik.reatube.BuildConfig
import com.matik.reatube.api.RutubeApi
import com.matik.reatube.net.Http
import com.matik.reatube.net.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class DiagSection(val title: String, val rows: List<Pair<String, String>>)

/** What ADB would have told us about the TV, collected by the app itself (the TV has no ADB access). */
object Diagnostics {
    const val REPORT_FILE = "reatube-diagnostics.txt"
    private const val RUTUBE_PACKAGE = "ru.rutube.app"

    private val videoTypes = listOf(
        "video/avc" to "H.264",
        "video/hevc" to "HEVC",
        "video/av01" to "AV1",
        "video/x-vnd.on2.vp9" to "VP9",
    )
    private val audioTypes = listOf(
        "audio/mp4a-latm" to "AAC",
        "audio/ac3" to "AC-3",
        "audio/eac3" to "E-AC-3",
    )

    fun local(context: Context): List<DiagSection> =
        listOf(device(context), display(context), decoders(), software(context))

    private fun device(context: Context): DiagSection {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val lowRam = if (am.isLowRamDevice) ", low-RAM" else ""
        return DiagSection(
            "Устройство",
            listOf(
                "Модель" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                "Android" to "${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}, патч ${Build.VERSION.SECURITY_PATCH}",
                "Прошивка" to Build.DISPLAY,
                "Процессор" to "${Build.HARDWARE}, ядер ${Runtime.getRuntime().availableProcessors()}, ${Build.SUPPORTED_ABIS.joinToString()}",
                "Память" to "${mb(memory.totalMem)} всего, ${mb(memory.availMem)} свободно$lowRam",
                "Лимит приложения" to "${am.memoryClass} МБ (large ${am.largeMemoryClass} МБ)",
            ),
        )
    }

    private fun display(context: Context): DiagSection {
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displays.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val modes = display.supportedModes
            .map { "${it.physicalWidth}×${it.physicalHeight}@${it.refreshRate.roundToInt()}" }
            .distinct()
        val hdr = display.hdrCapabilities?.supportedHdrTypes?.map(::hdrName).orEmpty()
        val metrics = context.resources.displayMetrics
        return DiagSection(
            "Экран",
            listOf(
                "Сейчас" to "${mode.physicalWidth}×${mode.physicalHeight} @ ${mode.refreshRate.roundToInt()} Гц",
                "Режимы" to modes.joinToString(),
                "HDR" to hdr.ifEmpty { listOf("нет") }.joinToString(),
                "Интерфейс" to "${metrics.widthPixels}×${metrics.heightPixels}, ${metrics.densityDpi} dpi",
            ),
        )
    }

    private fun hdrName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        4 -> "HDR10+" // HDR_TYPE_HDR10_PLUS, API 29
        else -> "тип $type"
    }

    private fun decoders(): DiagSection {
        val all = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
        fun supporting(mime: String) = all.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }

        val video = videoTypes.map { (mime, name) ->
            val found = supporting(mime)
            val hardware = found.filter(::isHardware)
            name to when {
                hardware.isNotEmpty() -> hardware.joinToString("; ") { describeVideo(it, mime) }
                found.isNotEmpty() -> "только программно: " + found.joinToString { it.name }
                else -> "нет"
            }
        }
        val audio = audioTypes.map { (mime, name) ->
            name to supporting(mime).joinToString { it.name }.ifEmpty { "нет" }
        }
        return DiagSection("Декодеры", video + audio)
    }

    private fun describeVideo(info: MediaCodecInfo, mime: String): String {
        val type = info.supportedTypes.first { it.equals(mime, ignoreCase = true) }
        val video = info.getCapabilitiesForType(type).videoCapabilities ?: return info.name
        val flags = listOfNotNull(
            "4K30".takeIf { video.areSizeAndRateSupported(3840, 2160, 30.0) },
            "1080p60".takeIf { video.areSizeAndRateSupported(1920, 1080, 60.0) },
        )
        val extra = if (flags.isEmpty()) "" else ", " + flags.joinToString()
        return "${info.name} до ${video.supportedWidths.upper}×${video.supportedHeights.upper}$extra"
    }

    fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else isHardwareName(info.name)

    /** Before API 29 there is no flag, so judge by the well-known software codec names. */
    fun isHardwareName(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("omx.ffmpeg.") || ".sw." in n)
    }

    private fun software(context: Context): DiagSection {
        val pm = context.packageManager
        val otherRutube = runCatching {
            pm.getInstalledPackages(0).map { it.packageName }.filter { "rutube" in it && it != RUTUBE_PACKAGE }
        }.getOrDefault(emptyList())
        return DiagSection(
            "Программы",
            listOf(
                "Официальный Rutube" to (packageVersion(pm, RUTUBE_PACKAGE) ?: "не установлен"),
                "Другие пакеты Rutube" to otherRutube.joinToString().ifEmpty { "нет" },
                "WebView" to webView(),
                "Сервисы Google" to (packageVersion(pm, "com.google.android.gms") ?: "нет"),
                "Режим Android TV" to if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) "да (leanback)" else "нет",
                "REA Rutube" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            ),
        )
    }

    private fun packageVersion(pm: PackageManager, name: String): String? = try {
        val info = pm.getPackageInfo(name, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)}), обновлён ${date(info.lastUpdateTime)}"
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun webView(): String = try {
        val pkg = if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage() else null
        pkg?.let { "${it.packageName} ${it.versionName}" } ?: "не определить"
    } catch (e: Exception) {
        "ошибка: ${e.javaClass.simpleName}"
    }

    /** Walks the same chain as playback: API → thumbnail → play/options → master → variant → segment. */
    suspend fun network(api: RutubeApi): DiagSection {
        val rows = mutableListOf<Pair<String, String>>()

        suspend fun <T> check(name: String, block: suspend () -> T): T? {
            val start = SystemClock.elapsedRealtime()
            return try {
                block().also { rows += name to "✓ ${SystemClock.elapsedRealtime() - start} мс" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rows += name to "✗ ${e.describe()}"
                null
            }
        }

        val cards = check("API rutube.ru: поиск") { api.searchVideos("новости") }
        check("Меню ТВ: pangolin") { api.get("${RutubeApi.PANGOLIN_TV}navigation/Android_TV") }
        run {
            val candidates = cards?.filter { !it.isLive && !it.isPaid }.orEmpty()
            candidates.firstOrNull()?.thumbnailUrl?.let { url -> check("Картинки: ${host(url)}") { fetch(url, limit = 4096) } }
            val playable = check("Плеер: play/options") {
                api.firstPlayable(candidates) ?: throw IOException("ни у одного из первых видео нет ссылки на поток")
            } ?: return@run
            val masterUrl = playable.second.hlsUrl ?: return@run
            val master = check("Манифест: ${host(masterUrl)}") { fetchText(masterUrl) } ?: return@run
            val variantUrl = firstUri(master, masterUrl) ?: return@run
            val variant = check("Плейлист качества: ${host(variantUrl)}") { fetchText(variantUrl) } ?: return@run
            val segmentUrl = firstUri(variant, variantUrl) ?: return@run
            check("Сегмент видео: ${host(segmentUrl)}") { fetch(segmentUrl, limit = 65536) }
        }
        return DiagSection("Сеть и серверы Rutube", rows)
    }

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body!!.string()
        }
    }

    /** Reads at most [limit] bytes: enough to prove TLS and the CDN work. */
    private suspend fun fetch(url: String, limit: Int): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Range", "bytes=0-${limit - 1}").build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val buffer = ByteArray(8192)
            var total = 0
            response.body!!.byteStream().use { stream ->
                while (total < limit) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                }
            }
            total
        }
    }

    private fun firstUri(playlist: String, base: String): String? =
        playlist.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.let { base.toHttpUrl().resolve(it)?.toString() }

    private fun host(url: String): String = runCatching { url.toHttpUrl().host }.getOrDefault("?")

    fun report(sections: List<DiagSection>): String = buildString {
        appendLine("REA Rutube ${BuildConfig.VERSION_NAME}: диагностика, ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date())}")
        for (section in sections) {
            appendLine()
            appendLine("== ${section.title} ==")
            section.rows.forEach { (label, value) -> appendLine("$label: $value") }
        }
    }

    /** Writes the report to every app folder, including one on a USB drive if the TV exposes it. */
    fun save(context: Context, text: String): List<String> =
        context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
            runCatching { File(dir, REPORT_FILE).apply { writeText(text) }.absolutePath }.getOrNull()
        }

    private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} МБ"

    private fun date(millis: Long) = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date(millis))
}
