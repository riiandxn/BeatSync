package com.beatsync.cut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatsync.cut.model.*
import com.beatsync.cut.ui.theme.*
import com.beatsync.cut.viewmodel.StudioTab

@Composable
fun StudioToolPanels(
    activeTab: StudioTab,
    onSelectTab: (StudioTab) -> Unit,
    draft: ProjectDraft,
    selectedSlotIndex: Int,
    onPickVideos: () -> Unit,
    onReuseMediaItem: (MediaBinItem) -> Unit,
    onUpdateInPoint: (Long) -> Unit,
    onUpdateSpeed: (SpeedPreset) -> Unit,
    onDuplicateSlot: () -> Unit,
    onDeleteSlot: () -> Unit,
    onMoveSlot: (Int) -> Unit,
    onSelectLut: (FilmLutPreset, Float?) -> Unit,
    onApplyLutToAll: () -> Unit,
    onUpdateAdjustments: (ColorAdjustments) -> Unit,
    onApplyAdjustToAll: () -> Unit,
    onSelectBeatFx: (BeatFxType) -> Unit,
    onApplyBeatFxToDrop: (BeatFxType) -> Unit,
    onUpdateTextOverlay: (TextOverlayConfig) -> Unit,
    onOpenAutoBeatSync: () -> Unit,
    onUpdateAudioVolumes: (Float, Float, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSlot = draft.slots.getOrNull(selectedSlotIndex)
    val activeMedia = activeSlot?.let { slot -> draft.mediaBin.find { it.id == slot.mediaBinId } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
    ) {
        // Active Tab Panel Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (activeTab) {
                StudioTab.KLIP -> KlipAndSlipTrimPanel(
                    draft = draft,
                    selectedSlotIndex = selectedSlotIndex,
                    activeSlot = activeSlot,
                    activeMedia = activeMedia,
                    onPickVideos = onPickVideos,
                    onReuseMediaItem = onReuseMediaItem,
                    onUpdateInPoint = onUpdateInPoint,
                    onUpdateSpeed = onUpdateSpeed,
                    onDuplicateSlot = onDuplicateSlot,
                    onDeleteSlot = onDeleteSlot,
                    onMoveSlot = onMoveSlot
                )
                StudioTab.FILTER -> FilmLutPanel(
                    activeSlot = activeSlot,
                    onSelectLut = onSelectLut,
                    onApplyLutToAll = onApplyLutToAll
                )
                StudioTab.ADJUST -> ManualAdjustPanel(
                    activeSlot = activeSlot,
                    onUpdateAdjustments = onUpdateAdjustments,
                    onApplyAdjustToAll = onApplyAdjustToAll
                )
                StudioTab.BEAT_FX -> BeatFxPanel(
                    activeSlot = activeSlot,
                    onSelectBeatFx = onSelectBeatFx,
                    onApplyBeatFxToDrop = onApplyBeatFxToDrop
                )
                StudioTab.TEKS -> TextOverlayPanel(
                    config = draft.textOverlay,
                    onUpdateTextOverlay = onUpdateTextOverlay
                )
                StudioTab.AUDIO -> AudioMixerPanel(
                    audio = draft.audio,
                    onOpenAutoBeatSync = onOpenAutoBeatSync,
                    onUpdateAudioVolumes = onUpdateAudioVolumes
                )
            }
        }

        // Bottom Studio Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StudioTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val icon = when (tab) {
                    StudioTab.KLIP -> Icons.Default.ContentCut
                    StudioTab.FILTER -> Icons.Default.MovieFilter
                    StudioTab.ADJUST -> Icons.Default.Tune
                    StudioTab.BEAT_FX -> Icons.Default.Bolt
                    StudioTab.TEKS -> Icons.Default.Title
                    StudioTab.AUDIO -> Icons.Default.GraphicEq
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.title,
                        tint = if (selected) PantoneBlueLight else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun KlipAndSlipTrimPanel(
    draft: ProjectDraft,
    selectedSlotIndex: Int,
    activeSlot: TimelineSlot?,
    activeMedia: MediaBinItem?,
    onPickVideos: () -> Unit,
    onReuseMediaItem: (MediaBinItem) -> Unit,
    onUpdateInPoint: (Long) -> Unit,
    onUpdateSpeed: (SpeedPreset) -> Unit,
    onDuplicateSlot: () -> Unit,
    onDeleteSlot: () -> Unit,
    onMoveSlot: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Media Bin (Import 1x, Reuse Unlimited Times)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MEDIA BIN (Pakai 1 Video Berkali-kali)",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = onPickVideos,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = PantoneBlueLight, modifier = Modifier.size(15.dp))
                Text(" Impor Video", color = PantoneBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (draft.mediaBin.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                draft.mediaBin.forEach { item ->
                    val usedCount = draft.slots.count { it.mediaBinId == item.id }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.widthIn(max = 120.dp)) {
                            Text(
                                text = item.displayName,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Durasi ${formatMs(item.totalDurationMs)} • Dipakai ${usedCount}x",
                                color = TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PantoneBlue)
                                .clickable { onReuseMediaItem(item) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("+ Pakai Lagi", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. 2-Level Slip-Trim for Active Slot
        if (activeSlot != null && activeMedia != null) {
            val maxInMs = (activeMedia.totalDurationMs - activeSlot.durationMs).coerceAtLeast(1L)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Slip-Trim Slot #${selectedSlotIndex + 1} (${activeMedia.displayName})",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "In: ${formatMs(activeSlot.inPointMs)} (${formatMs(activeSlot.durationMs)})",
                        color = PantoneBlueLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Level 1: Macro Bar (00:00 -> Full 3-Minute Length)
                Text("1. Macro Scrub (Lompat Menit/Detik)", color = TextSecondary, fontSize = 9.sp)
                Slider(
                    value = activeSlot.inPointMs.toFloat().coerceIn(0f, maxInMs.toFloat()),
                    onValueChange = { onUpdateInPoint(it.toLong()) },
                    valueRange = 0f..maxInMs.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = PantoneBlueLight, activeTrackColor = PantoneBlue),
                    modifier = Modifier.height(28.dp)
                )

                // Level 2: Micro Precision Scrubber (+/- 3000ms window)
                val microMin = (activeSlot.inPointMs - 3000L).coerceAtLeast(0L)
                val microMax = (activeSlot.inPointMs + 3000L).coerceAtMost(maxInMs).coerceAtLeast(microMin + 100L)
                Text("2. Micro Scrub (Presisi Frame)", color = TextSecondary, fontSize = 9.sp)
                Slider(
                    value = activeSlot.inPointMs.toFloat().coerceIn(microMin.toFloat(), microMax.toFloat()),
                    onValueChange = { onUpdateInPoint(it.toLong()) },
                    valueRange = microMin.toFloat()..microMax.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = BeatAmber, activeTrackColor = BeatAmber),
                    modifier = Modifier.height(28.dp)
                )

                // Speed & Slot Actions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeedPreset.entries.forEach { sp ->
                        val sel = sp == activeSlot.speed
                        FilterChip(
                            selected = sel,
                            onClick = { onUpdateSpeed(sp) },
                            label = { Text(sp.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PantoneBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    IconButton(onClick = { onMoveSlot(-1) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Geser Kiri", tint = TextPrimary)
                    }
                    IconButton(onClick = { onMoveSlot(1) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Geser Kanan", tint = TextPrimary)
                    }
                    IconButton(onClick = onDuplicateSlot, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplikat Slot", tint = PantoneBlueLight)
                    }
                    IconButton(onClick = onDeleteSlot, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Slot", tint = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmLutPanel(
    activeSlot: TimelineSlot?,
    onSelectLut: (FilmLutPreset, Float?) -> Unit,
    onApplyLutToAll: () -> Unit
) {
    val currentLutId = activeSlot?.lutId ?: FilmLutPreset.ORIGINAL.id
    val intensity = activeSlot?.lutIntensity ?: 0.85f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CINEMATIC FILM LUTS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onApplyLutToAll, contentPadding = PaddingValues(0.dp)) {
                Text("Terapkan ke Semua Klip", color = PantoneBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilmLutPreset.entries.forEach { preset ->
                val selected = preset.id == currentLutId
                Column(
                    modifier = Modifier
                        .width(74.dp)
                        .clickable { onSelectLut(preset, null) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(preset.previewHex))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) PantoneBlueLight else BorderSubtle,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(preset.category.take(3), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preset.displayName,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Intensitas LUT: ${(intensity * 100).toInt()}%", color = TextPrimary, fontSize = 11.sp, modifier = Modifier.width(120.dp))
            Slider(
                value = intensity,
                onValueChange = { value ->
                    val lut = FilmLutPreset.entries.find { it.id == currentLutId } ?: FilmLutPreset.ORIGINAL
                    onSelectLut(lut, value)
                },
                colors = SliderDefaults.colors(thumbColor = PantoneBlueLight, activeTrackColor = PantoneBlue),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ManualAdjustPanel(
    activeSlot: TimelineSlot?,
    onUpdateAdjustments: (ColorAdjustments) -> Unit,
    onApplyAdjustToAll: () -> Unit
) {
    val adj = activeSlot?.adjustments ?: ColorAdjustments()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MANUAL COLOR & FILM TEXTURE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { onUpdateAdjustments(ColorAdjustments()) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Reset", color = TextSecondary, fontSize = 11.sp)
                }
                TextButton(onClick = onApplyAdjustToAll, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Apply All", color = PantoneBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        AdjustSliderRow("Exposure", adj.exposure, -1f..1f) { onUpdateAdjustments(adj.copy(exposure = it)) }
        AdjustSliderRow("Contrast", adj.contrast, -1f..1f) { onUpdateAdjustments(adj.copy(contrast = it)) }
        AdjustSliderRow("Saturation", adj.saturation, -1f..1f) { onUpdateAdjustments(adj.copy(saturation = it)) }
        AdjustSliderRow("Warmth", adj.warmth, -1f..1f) { onUpdateAdjustments(adj.copy(warmth = it)) }
        AdjustSliderRow("Film Grain", adj.filmGrain, 0f..1f) { onUpdateAdjustments(adj.copy(filmGrain = it)) }
        AdjustSliderRow("Vignette", adj.vignette, 0f..1f) { onUpdateAdjustments(adj.copy(vignette = it)) }
    }
}

@Composable
private fun AdjustSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(30.dp)) {
        Text(label, color = TextPrimary, fontSize = 11.sp, modifier = Modifier.width(84.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = PantoneBlueLight, activeTrackColor = PantoneBlue),
            modifier = Modifier.weight(1f)
        )
        Text(String.format("%.2f", value), color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun BeatFxPanel(
    activeSlot: TimelineSlot?,
    onSelectBeatFx: (BeatFxType) -> Unit,
    onApplyBeatFxToDrop: (BeatFxType) -> Unit
) {
    val currentFx = activeSlot?.beatFx ?: BeatFxType.NONE
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BEAT FX SELEKTIF (JEDAG-JEDUG)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onApplyBeatFxToDrop(currentFx) }, contentPadding = PaddingValues(0.dp)) {
                Text("Pasang di Bagian Drop", color = BeatAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BeatFxType.entries.forEach { fx ->
                val selected = fx == currentFx
                FilterChip(
                    selected = selected,
                    onClick = { onSelectBeatFx(fx) },
                    label = { Text(fx.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PantoneBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Text(
            text = "Tips: Biarkan bagian intro memakai Hard Cut agar sinematik, lalu pasang White Flash atau Shake Hard hanya di titik drop lagu.",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TextOverlayPanel(
    config: TextOverlayConfig,
    onUpdateTextOverlay: (TextOverlayConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TEXT & TITLE OVERLAY", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = config.enabled,
                onCheckedChange = { onUpdateTextOverlay(config.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = PantoneBlue)
            )
        }

        OutlinedTextField(
            value = config.text,
            onValueChange = { onUpdateTextOverlay(config.copy(text = it, enabled = true)) },
            singleLine = true,
            label = { Text("Teks di atas video (geser posisi di layar preview)", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextFontStyle.entries.forEach { style ->
                FilterChip(
                    selected = config.fontStyle == style,
                    onClick = { onUpdateTextOverlay(config.copy(fontStyle = style, enabled = true)) },
                    label = { Text(style.label, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PantoneBlue, selectedLabelColor = Color.White)
                )
            }
        }
    }
}

@Composable
private fun AudioMixerPanel(
    audio: AudioTrackConfig,
    onOpenAutoBeatSync: () -> Unit,
    onUpdateAudioVolumes: (Float, Float, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(audio.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Span ${formatMs(audio.spanStartMs)} • ${audio.beatTimestampsMs.size} Beat Cuts", color = TextSecondary, fontSize = 10.sp)
            }
            Button(
                onClick = onOpenAutoBeatSync,
                colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Ganti / Atur Lagu", fontSize = 11.sp)
            }
        }

        AdjustSliderRow("Volume Lagu", audio.musicVolume, 0f..1.5f) {
            onUpdateAudioVolumes(it, audio.originalClipVolume, audio.fadeInOut)
        }
        AdjustSliderRow("Suara Asli Klip", audio.originalClipVolume, 0f..1f) {
            onUpdateAudioVolumes(audio.musicVolume, it, audio.fadeInOut)
        }
    }
}
