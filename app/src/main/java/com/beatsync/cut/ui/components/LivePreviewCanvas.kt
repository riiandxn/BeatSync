package com.beatsync.cut.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.beatsync.cut.model.*
import com.beatsync.cut.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun LivePreviewCanvas(
    draft: ProjectDraft,
    selectedSlotIndex: Int,
    playheadMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onUpdateTextOffset: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeSlot = draft.slots.getOrNull(selectedSlotIndex)
    val activeMedia = activeSlot?.let { slot -> draft.mediaBin.find { it.id == slot.mediaBinId } }
    val activeLut = FilmLutPreset.entries.find { it.id == activeSlot?.lutId } ?: FilmLutPreset.ORIGINAL

    // Compute local offset inside current slot
    val slotStartTimelineMs = remember(draft.slots, selectedSlotIndex) {
        draft.slots.take(selectedSlotIndex).sumOf { it.durationMs }
    }
    val offsetInSlotMs = (playheadMs - slotStartTimelineMs).coerceAtLeast(0L)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(activeMedia?.uriString) {
        val uriStr = activeMedia?.uriString
        if (!uriStr.isNullOrBlank() && activeMedia.isMissing.not()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            exoPlayer.prepare()
        } else {
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(activeSlot?.id, activeSlot?.inPointMs, offsetInSlotMs, isPlaying) {
        if (activeSlot != null && activeMedia != null && !activeMedia.isMissing) {
            val targetMediaPos = activeSlot.inPointMs + (offsetInSlotMs * activeSlot.speed.multiplier).toLong()
            if (!isPlaying) {
                exoPlayer.pause()
                exoPlayer.seekTo(targetMediaPos)
            } else {
                if (!exoPlayer.isPlaying) {
                    exoPlayer.seekTo(targetMediaPos)
                    exoPlayer.play()
                }
            }
        } else {
            exoPlayer.pause()
        }
    }

    val aspectRatioFloat = draft.aspectRatio.ratioFloat

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(aspectRatioFloat, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (activeMedia != null && !activeMedia.isMissing) {
                // Beat FX live visual pulse on first 180ms of slot
                val isBeatTriggerWindow = offsetInSlotMs < 180L
                val scalePulse = when {
                    isBeatTriggerWindow && activeSlot?.beatFx == BeatFxType.ZOOM_PUNCH -> 1.12f
                    isBeatTriggerWindow && activeSlot?.beatFx == BeatFxType.SHAKE_HARD -> 1.09f
                    isBeatTriggerWindow && activeSlot?.beatFx == BeatFxType.SHAKE_SUBTLE -> 1.04f
                    else -> 1f
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            player = exoPlayer
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scalePulse
                            scaleY = scalePulse
                        }
                )

                // Live Film LUT Color Grade Preview Overlay
                if (activeLut != FilmLutPreset.ORIGINAL && activeSlot != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(activeLut.previewHex).copy(alpha = 0.22f * activeSlot.lutIntensity))
                    )
                }

                // Live White Flash Beat FX Preview
                if (isBeatTriggerWindow && activeSlot?.beatFx == BeatFxType.WHITE_FLASH) {
                    val flashAlpha = (1f - (offsetInSlotMs / 180f)) * 0.65f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = flashAlpha.coerceIn(0f, 0.7f)))
                    )
                }

                // Top HUD Badge: Active Slot + In-Point + LUT
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xAA0A0A0D), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${selectedSlotIndex + 1} • In ${formatMs(activeSlot?.inPointMs ?: 0L)} • ${activeLut.displayName}",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = PantoneBlueLight,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Impor Video atau Klik Auto Beat-Sync",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Draggable Text Overlay
            if (draft.textOverlay.enabled && draft.textOverlay.text.isNotBlank()) {
                var dragX by remember(draft.textOverlay.offsetX) { mutableStateOf(draft.textOverlay.offsetX * 220f) }
                var dragY by remember(draft.textOverlay.offsetY) { mutableStateOf(draft.textOverlay.offsetY * 320f) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    onUpdateTextOffset(
                                        (dragX / 220f).coerceIn(-0.42f, 0.42f),
                                        (dragY / 320f).coerceIn(-0.42f, 0.42f)
                                    )
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                dragX += dragAmount.x
                                dragY += dragAmount.y
                            }
                        }
                        .background(
                            if (draft.textOverlay.hasPillBackground) PantoneBlue.copy(alpha = 0.85f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = draft.textOverlay.text,
                        color = Color(draft.textOverlay.textColorHex),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = if (draft.textOverlay.fontStyle == TextFontStyle.CINEMA_SERIF) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = if (draft.textOverlay.fontStyle == TextFontStyle.MONO_TAG) FontFamily.Monospace else FontFamily.SansSerif
                    )
                }
            }
        }

        // Transport Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatMs(playheadMs)} / ${formatMs(draft.totalTimelineDurationMs)}",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(34.dp)
                    .background(PantoneBlue, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "${draft.slots.size} Klip • ${draft.audio.bpmEstimate} BPM",
                color = PantoneBlueLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val tenths = ((ms.coerceAtLeast(0L)) % 1000L) / 100L
    return String.format("%02d:%02d.%d", min, sec, tenths)
}
