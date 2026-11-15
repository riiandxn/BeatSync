package com.beatsync.cut.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beatsync.cut.model.BeatDensity
import com.beatsync.cut.model.ProjectDraft
import com.beatsync.cut.render.ExportFps
import com.beatsync.cut.render.ExportOutcome
import com.beatsync.cut.render.ExportResolution
import com.beatsync.cut.ui.theme.*

@Composable
fun AutoBeatSyncWizardDialog(
    draft: ProjectDraft,
    step: Int,
    isResolvingAudio: Boolean,
    audioStatusMessage: String,
    onDismiss: () -> Unit,
    onSetStep: (Int) -> Unit,
    onResolveUrl: (String) -> Unit,
    onPickVideoForAudioExtract: () -> Unit,
    onPickLocalAudio: () -> Unit,
    onUpdateSpanAndDensity: (Long?, Long?, BeatDensity?) -> Unit,
    onPickVideosForTimeline: () -> Unit
) {
    var linkInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto Beat-Sync • Langkah $step / 2",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }

            if (step == 1) {
                Text(
                    text = "1. Pilih Lagu & Rentang Beat (Maks 60 Detik)",
                    color = PantoneBlueLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    placeholder = { Text("Tempel link YouTube / Spotify...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onResolveUrl(linkInput) },
                        enabled = !isResolvingAudio && linkInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ambil Link", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onPickVideoForAudioExtract,
                        enabled = !isResolvingAudio,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ekstrak dari Video", fontSize = 11.sp)
                    }
                }

                OutlinedButton(
                    onClick = onPickLocalAudio,
                    enabled = !isResolvingAudio,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pilih File MP3 / M4A Lokal", fontSize = 12.sp)
                }

                if (audioStatusMessage.isNotBlank()) {
                    Text(audioStatusMessage, color = BeatAmber, fontSize = 11.sp)
                }

                // Target Duration Chips (15s, 30s, 45s, 60s)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15_000L, 30_000L, 45_000L, 60_000L).forEach { durMs ->
                        val selected = draft.audio.spanDurationMs == durMs
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateSpanAndDensity(null, durMs, null) },
                            label = { Text("${durMs / 1000}d", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PantoneBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Span Start Slider
                val maxStart = (draft.audio.totalDurationMs - draft.audio.spanDurationMs).coerceAtLeast(1L)
                Text(
                    text = "Mulai Lagu: ${formatMs(draft.audio.spanStartMs)} • ${draft.audio.beatTimestampsMs.size} Potongan Beat",
                    color = TextPrimary,
                    fontSize = 11.sp
                )
                Slider(
                    value = draft.audio.spanStartMs.toFloat().coerceIn(0f, maxStart.toFloat()),
                    onValueChange = { onUpdateSpanAndDensity(it.toLong(), null, null) },
                    valueRange = 0f..maxStart.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = PantoneBlueLight, activeTrackColor = PantoneBlue)
                )

                // Density Mode
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BeatDensity.entries.forEach { d ->
                        FilterChip(
                            selected = draft.audio.beatDensity == d,
                            onClick = { onUpdateSpanAndDensity(null, null, d) },
                            label = { Text(d.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PantoneBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Button(
                    onClick = { onSetStep(2) },
                    colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lanjut Pilih Video →", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "2. Pilih Klip Video (Smart Spread Otomatis)",
                    color = PantoneBlueLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Jika kamu memilih 1 video rekaman panjang (misal 3 menit), aplikasi otomatis menyebar titik potongnya secara merata dari awal sampai akhir video agar tidak menumpuk di detik pertama.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Button(
                    onClick = {
                        onPickVideosForTimeline()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih Video & Susun ke Studio", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { onSetStep(1) }, modifier = Modifier.fillMaxWidth()) {
                    Text("← Kembali Atur Lagu", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Export4KModalDialog(
    resolution: ExportResolution,
    fps: ExportFps,
    isDevice4KCapable: Boolean,
    isExporting: Boolean,
    progress: Int,
    outcome: ExportOutcome?,
    onSelectOptions: (ExportResolution?, ExportFps?) -> Unit,
    onStartExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Export Video", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            if (outcome != null) {
                if (outcome.success) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Video Berhasil Disimpan!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Lokasi: ${outcome.savedDisplayPath}", color = TextSecondary, fontSize = 12.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Tutup")
                        }
                        if (outcome.outputUri != null) {
                            Button(
                                onClick = {
                                    val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(outcome.outputUri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try { context.startActivity(playIntent) } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Putar Video")
                            }
                        }
                    }
                } else {
                    Text("Gagal Export: ${outcome.errorMessage}", color = DangerRed, fontSize = 12.sp)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Tutup")
                    }
                }
            } else if (isExporting) {
                Text("Merender Video (${resolution.label} • ${fps.label})...", color = TextPrimary, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = PantoneBlueLight,
                    trackColor = SurfaceElevated,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Text("$progress%", color = PantoneBlueLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("Resolusi Output", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportResolution.entries.forEach { res ->
                        val selected = res == resolution
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) PantoneBlue.copy(alpha = 0.25f) else SurfaceElevated)
                                .border(1.dp, if (selected) PantoneBlueLight else BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { onSelectOptions(res, null) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(res.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (res == ExportResolution.RES_4K && !isDevice4KCapable) {
                                Text("Auto-fallback 1080p", color = BeatAmber, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Text("Frame Rate", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFps.entries.forEach { f ->
                        FilterChip(
                            selected = f == fps,
                            onClick = { onSelectOptions(null, f) },
                            label = { Text(f.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PantoneBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal")
                    }
                    Button(
                        onClick = onStartExport,
                        colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mulai Export", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
