package com.beatsync.editor.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.beatsync.editor.model.AspectRatioOption
import com.beatsync.editor.model.BeatDensity
import com.beatsync.editor.model.ExportState
import com.beatsync.editor.model.VideoFilterPreset
import java.util.Locale

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatSyncScreen(vm: BeatSyncViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        vm.addVideoClips(uris)
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            vm.loadLocalAudio(uri)
        }
    }

    val bgDark = Color(0xFF0B0F19)
    val cardBg = Color(0xFF131A2A)
    val accentCyan = Color(0xFF06B6D4)
    val accentPurple = Color(0xFF8B5CF6)

    Scaffold(
        containerColor = bgDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = accentCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BeatSync Cut",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgDark)
            )
        },
        bottomBar = {
            val canExport = state.audioTrack != null &&
                state.videoClips.isNotEmpty() &&
                !state.isAnalyzingBeats &&
                state.exportState is ExportState.Idle

            Surface(color = cardBg, tonalElevation = 8.dp) {
                Button(
                    onClick = { vm.exportVideo() },
                    enabled = canExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentCyan,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF263147),
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val cutCount = (state.beatAnalysis?.beatTimestampsMs?.size ?: 1) - 1
                    Text(
                        text = if (canExport) {
                            "Export Video ($cutCount Potongan Beat)"
                        } else {
                            "Pilih Lagu & Video untuk Mulai"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: LAGU & RENTANG BEAT
            SectionCard(title = "1. Pilih Lagu & Rentang Beat (Maks 60 Detik)", cardBg = cardBg) {
                OutlinedTextField(
                    value = state.linkInput,
                    onValueChange = vm::onLinkInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Tempel link YouTube / Spotify atau judul lagu...",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentCyan,
                        unfocusedBorderColor = Color(0xFF293548),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.loadAudioFromLink() },
                        enabled = !state.isLoadingAudio && state.linkInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentPurple)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ambil Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        enabled = !state.isLoadingAudio,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("MP3 Lokal", color = Color.White, fontSize = 13.sp)
                    }
                }

                if (state.isLoadingAudio) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = accentCyan
                    )
                    Text(
                        "Mengambil stream audio & menyiapkan gelombang...",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                state.audioError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp
                    )
                }

                // Target Max Duration Chips
                Spacer(modifier = Modifier.height(12.dp))
                Text("Target Durasi Maksimal:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { sec ->
                        FilterChip(
                            selected = state.maxDurationSec == sec,
                            onClick = { vm.setMaxDurationSec(sec) },
                            label = { Text("${sec}d") }
                        )
                    }
                }

                // Track Loaded & Span Range Selector
                state.audioTrack?.let { track ->
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${track.sourceLabel} • Total ${formatTimeMs(track.totalDurationMs)}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = { vm.toggleAudioPreview() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accentCyan)
                        ) {
                            Icon(
                                imageVector = if (state.isPlayingAudioPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Preview Lagu",
                                tint = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val spanSec = ((state.spanEndMs - state.spanStartMs) / 1000f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Span: ${formatTimeMs(state.spanStartMs)} - ${formatTimeMs(state.spanEndMs)}",
                            color = accentCyan,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Text(
                            text = String.format(Locale.US, "Durasi: %.1f detik", spanSec),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    val totalFloat = track.totalDurationMs.toFloat().coerceAtLeast(2000f)
                    RangeSlider(
                        value = state.spanStartMs.toFloat()..state.spanEndMs.toFloat(),
                        onValueChange = { range ->
                            vm.updateAudioSpan(range.start.toLong(), range.endInclusive.toLong())
                        },
                        onValueChangeFinished = { vm.onSpanDragFinished() },
                        valueRange = 0f..totalFloat,
                        colors = SliderDefaults.colors(
                            thumbColor = accentCyan,
                            activeTrackColor = accentCyan
                        )
                    )

                    // Beat Waveform Visualizer
                    state.beatAnalysis?.let { analysis ->
                        BeatWaveformBox(
                            peaks = analysis.waveformPeaks,
                            cutPointsMs = analysis.beatTimestampsMs,
                            spanDurationMs = (state.spanEndMs - state.spanStartMs).coerceAtLeast(1000L),
                            bpm = analysis.estimatedBpm
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Kerapatan Hentakan (Beat Sync Mode):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BeatDensity.entries.forEach { density ->
                            val selected = state.beatDensity == density
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) accentCyan.copy(alpha = 0.2f) else Color(0xFF1E293B))
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) accentCyan else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { vm.setBeatDensity(density) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = density.label,
                                    color = if (selected) accentCyan else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // STEP 2: UPLOAD KLIP VIDEO
            SectionCard(title = "2. Upload Klip Video (${state.videoClips.size} dipilih)", cardBg = cardBg) {
                Button(
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = accentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Beberapa Video dari Galeri", color = Color.White)
                }

                if (state.videoClips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(state.videoClips) { index, clip ->
                            Box(
                                modifier = Modifier
                                    .width(126.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            color = accentCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Row {
                                            if (index > 0) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Geser Kiri",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { vm.moveClipLeft(index) }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Hapus",
                                                tint = Color(0xFFF87171),
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { vm.removeVideoClip(index) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = clip.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatTimeMs(clip.durationMs),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3: FORMAT & FILTER WARNA
            SectionCard(title = "3. Rasio Layar, Filter & Efek Beat", cardBg = cardBg) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspectRatioOption.entries.forEach { ratio ->
                        val selected = state.aspectRatio == ratio
                        FilterChip(
                            selected = selected,
                            onClick = { vm.setAspectRatio(ratio) },
                            label = { Text(ratio.label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Preset Filter Warna:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(VideoFilterPreset.entries) { _, preset ->
                        val selected = state.filterPreset == preset
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0xFF26344D) else Color(0xFF1E293B))
                                .border(
                                    width = if (selected) 1.5.dp else 0.dp,
                                    color = if (selected) accentCyan else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { vm.setFilterPreset(preset) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.accentHex))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preset.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (state.filterPreset != VideoFilterPreset.ORIGINAL) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Intensitas Filter: ${(state.filterIntensity * 100).toInt()}%",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = state.filterIntensity,
                        onValueChange = vm::setFilterIntensity,
                        colors = SliderDefaults.colors(
                            thumbColor = accentCyan,
                            activeTrackColor = accentCyan
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Efek Beat Punch (Zoom on Beat)", color = Color.White, fontSize = 13.sp)
                        Text(
                            "Zoom mikro 106% tiap pergantian klip agar hentakan lebih terasa",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = state.enableBeatPunch,
                        onCheckedChange = vm::setBeatPunch
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialog Status Export
    when (val exp = state.exportState) {
        is ExportState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Merender Video Beat-Sync") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { exp.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(exp.statusText, fontSize = 13.sp)
                    }
                },
                confirmButton = {}
            )
        }

        is ExportState.Success -> {
            AlertDialog(
                onDismissRequest = { vm.dismissExportState() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E)) },
                title = { Text("Video Berhasil Disimpan!") },
                text = {
                    Text("Tersimpan di folder Movies/BeatSync/${exp.fileName}")
                },
                confirmButton = {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(exp.outputUri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                        vm.dismissExportState()
                    }) {
                        Text("Putar Video")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissExportState() }) {
                        Text("Tutup")
                    }
                }
            )
        }

        is ExportState.Error -> {
            AlertDialog(
                onDismissRequest = { vm.dismissExportState() },
                title = { Text("Export Gagal") },
                text = { Text(exp.message) },
                confirmButton = {
                    TextButton(onClick = { vm.dismissExportState() }) {
                        Text("OK")
                    }
                }
            )
        }

        ExportState.Idle -> Unit
    }
}

@Composable
private fun SectionCard(
    title: String,
    cardBg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun BeatWaveformBox(
    peaks: List<Float>,
    cutPointsMs: List<Long>,
    spanDurationMs: Long,
    bpm: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0F19))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Peta Hentakan Lagu (~$bpm BPM)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            Text(
                text = "${(cutPointsMs.size - 1).coerceAtLeast(1)} Potongan Klip",
                color = Color(0xFF06B6D4),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val barCount = peaks.size.coerceAtLeast(1)
            val barSpacing = size.width / barCount
            val barWidth = (barSpacing * 0.55f).coerceAtLeast(2f)

            peaks.forEachIndexed { idx, amp ->
                val x = idx * barSpacing + barSpacing / 2f
                val barHeight = size.height * amp
                val topY = (size.height - barHeight) / 2f
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4))
                    ),
                    start = Offset(x, topY),
                    end = Offset(x, topY + barHeight),
                    strokeWidth = barWidth
                )
            }

            // Garis penanda titik pergantian video sesuai beat
            cutPointsMs.forEach { cutMs ->
                val ratio = (cutMs.toFloat() / spanDurationMs.toFloat()).coerceIn(0f, 1f)
                val x = ratio * size.width
                drawLine(
                    color = Color(0xFFFACC15),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
