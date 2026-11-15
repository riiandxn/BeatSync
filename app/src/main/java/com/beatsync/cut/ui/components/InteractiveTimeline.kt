package com.beatsync.cut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatsync.cut.model.BeatFxType
import com.beatsync.cut.model.ProjectDraft
import com.beatsync.cut.ui.theme.*

@Composable
fun InteractiveTimeline(
    draft: ProjectDraft,
    selectedSlotIndex: Int,
    onSelectSlot: (Int) -> Unit,
    onOpenAutoBeatSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val mediaMap = draft.mediaBin.associateBy { it.id }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle)
            .padding(vertical = 8.dp)
    ) {
        // Quick Action Strip above tracks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TIMELINE & BEAT MARKERS",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PantoneBlue)
                    .clickable { onOpenAutoBeatSync() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Auto Beat-Sync (2 Langkah)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Scrollable Tracks
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp)
        ) {
            // TRACK 1: VIDEO SLOTS
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (draft.slots.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onOpenAutoBeatSync() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Tambah Klip di Tab 'Klip' atau Klik Auto Beat-Sync",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    draft.slots.forEachIndexed { index, slot ->
                        val isSelected = index == selectedSlotIndex
                        val media = mediaMap[slot.mediaBinId]
                        val widthDp = ((slot.durationMs / 1000f) * 68f).coerceIn(66f, 180f).dp

                        Box(
                            modifier = Modifier
                                .width(widthDp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PantoneBlue.copy(alpha = 0.28f) else SurfaceElevated)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) PantoneBlueLight else BorderSubtle,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectSlot(index) }
                                .padding(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        color = if (isSelected) Color.White else PantoneBlueLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (slot.beatFx != BeatFxType.NONE) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = BeatAmber,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = media?.displayName ?: "Clip",
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "@${formatMs(slot.inPointMs)}",
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        if (index < draft.slots.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(16.dp)
                                    .background(
                                        if (draft.slots[index + 1].beatFx != BeatFxType.NONE) BeatAmber else SurfaceElevated,
                                        CircleShape
                                    )
                                    .clickable { onSelectSlot(index + 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚡",
                                    fontSize = 8.sp,
                                    color = if (draft.slots[index + 1].beatFx != BeatFxType.NONE) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // TRACK 2: AUDIO WAVEFORM & BEAT CUT TICKS
            val trackWidthDp = if (draft.slots.isEmpty()) 260.dp else {
                draft.slots.sumOf { (((it.durationMs / 1000f) * 68f).coerceIn(66f, 180f) + 22f).toDouble() }.dp
            }

            Box(
                modifier = Modifier
                    .width(trackWidthDp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val peaks = draft.audio.waveformPeaks.ifEmpty {
                    List(48) { i -> if (i % 3 == 0) 0.85f else 0.42f }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barStep = size.width / peaks.size.coerceAtLeast(1)
                    peaks.forEachIndexed { idx, amp ->
                        val x = idx * barStep + barStep / 2f
                        val barH = size.height * amp.coerceIn(0.15f, 1f)
                        val isBeatBar = idx % 4 == 0
                        drawLine(
                            color = if (isBeatBar) BeatAmber else PantoneBlueLight.copy(alpha = 0.7f),
                            start = Offset(x, (size.height - barH) / 2f),
                            end = Offset(x, (size.height + barH) / 2f),
                            strokeWidth = (barStep * 0.55f).coerceIn(2f, 5f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .background(Color(0xCC0A0A0D), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = PantoneBlueLight,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = draft.audio.title,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
