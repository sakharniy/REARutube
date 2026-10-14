package com.matik.reatube.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.matik.reatube.api.ChannelItem
import com.matik.reatube.api.Item
import com.matik.reatube.api.LinkItem
import com.matik.reatube.api.ShowItem
import com.matik.reatube.api.VideoCard
import com.matik.reatube.api.VideoItem
import com.matik.reatube.data.ChannelRef

val CardWidth = 208.dp
private val Corner = RoundedCornerShape(8.dp)

@Composable
private fun cardBorder(shape: androidx.compose.ui.graphics.Shape = Corner) =
    CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Accent), shape = shape))

/** Any row item; long press adds a video to "watch later" or subscribes to a channel, unless [onLongClick] overrides it. */
@Composable
fun ItemCard(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val services = LocalServices.current
    val context = LocalContext.current
    when (item) {
        is VideoItem -> VideoTile(
            video = item.video,
            onClick = onClick,
            onLongClick = onLongClick ?: {
                val added = services.library.toggleWatchLater(item.video)
                Toast.makeText(context, if (added) "Добавлено в «Смотреть позже»" else "Убрано из «Смотреть позже»", Toast.LENGTH_SHORT).show()
            },
            progress = services.library.progress(item.video.id),
            modifier = modifier,
        )
        is ChannelItem -> ChannelTile(
            name = item.title,
            imageUrl = item.imageUrl,
            onClick = onClick,
            onLongClick = {
                val added = services.library.toggleSubscription(ChannelRef(item.id, item.title, item.imageUrl))
                Toast.makeText(context, if (added) "Вы подписались на «${item.title}»" else "Подписка отменена", Toast.LENGTH_SHORT).show()
            },
            modifier = modifier,
        )
        is ShowItem -> PosterTile(item.title, item.imageUrl, onClick, modifier)
        is LinkItem -> LinkTile(item.title, onClick, modifier)
    }
}

@Composable
fun VideoTile(
    video: VideoCard,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = CardDefaults.shape(Corner),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = cardBorder(),
        ) {
            Box(Modifier.fillMaxSize().background(Panel)) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (video.isLive) {
                    Badge("ЭФИР", LiveRed, Modifier.align(Alignment.TopStart).padding(6.dp))
                } else {
                    video.durationSec?.takeIf { it > 0 }?.let {
                        Badge(formatDuration(it), Color(0xCC000000), Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    }
                    if (video.isPaid) Badge("ПОДПИСКА", Color(0xE6000000), Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                progress?.let {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                        Box(Modifier.fillMaxWidth(it).fillMaxHeight().background(LiveRed))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        video.author?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelTile(name: String, imageUrl: String?, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(112.dp),
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = cardBorder(CircleShape),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(Panel),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(140.dp),
        )
    }
}

@Composable
private fun PosterTile(title: String, imageUrl: String?, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = CardDefaults.shape(Corner),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = cardBorder(),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(Panel),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Promo tiles come without pictures, so draw the title on a gradient. */
@Composable
private fun LinkTile(title: String, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        shape = CardDefaults.shape(Corner),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = cardBorder(),
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF16324F), Color(0xFF0E4D64)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
