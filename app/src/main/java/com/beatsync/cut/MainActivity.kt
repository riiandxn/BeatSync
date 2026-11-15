package com.beatsync.cut

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beatsync.cut.ui.components.*
import com.beatsync.cut.ui.screens.DraftsHomeScreen
import com.beatsync.cut.ui.theme.*
import com.beatsync.cut.viewmodel.AppScreen
import com.beatsync.cut.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeatSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    BeatSyncAppRoot()
                }
            }
        }
    }
}

@Composable
fun BeatSyncAppRoot(vm: StudioViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var pendingAutoSpread by remember { mutableStateOf(false) }
    var pendingRelinkTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val multiVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            vm.importVideosToMediaBin(uris, autoGenerateSlots = pendingAutoSpread)
        }
        pendingAutoSpread = false
    }

    val videoAudioExtractPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.extractAudioFromVideo(uri)
        }
    }

    val localAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.importLocalAudio(uri)
        }
    }

    val relinkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val target = pendingRelinkTarget
        if (uri != null && target != null) {
            vm.relinkMedia(target.first, target.second, uri)
        }
        pendingRelinkTarget = null
    }

    if (state.currentScreen == AppScreen.DRAFTS_HOME) {
        DraftsHomeScreen(
            drafts = state.drafts,
            onNewManualProject = { vm.createNewProject(openAutoWizard = false) },
            onNewAutoBeatSyncProject = { vm.createNewProject(openAutoWizard = true) },
            onOpenDraft = { vm.openDraft(it) },
            onDuplicateDraft = { vm.duplicateDraft(it) },
            onDeleteDraft = { vm.deleteDraft(it) },
            onRelinkMedia = { projId, mediaId ->
                pendingRelinkTarget = projId to mediaId
                relinkPicker.launch(arrayOf("video/*"))
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            // Studio Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.navigateToDraftsHome() }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Proyek Saya", tint = TextPrimary)
                    }
                    Column {
                        Text(
                            text = state.activeDraft.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.autoSaveStatus,
                            color = SuccessGreen,
                            fontSize = 9.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                            .clickable { vm.toggleAspectRatio() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = state.activeDraft.aspectRatio.label,
                            color = PantoneBlueLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { vm.setShowExportModal(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Export", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 1. Live Preview Canvas (~44%)
            LivePreviewCanvas(
                draft = state.activeDraft,
                selectedSlotIndex = state.selectedSlotIndex,
                playheadMs = state.playheadMs,
                isPlaying = state.isPlaying,
                onTogglePlay = { vm.togglePlayback() },
                onUpdateTextOffset = { x, y ->
                    vm.updateTextOverlay(state.activeDraft.textOverlay.copy(offsetX = x, offsetY = y))
                },
                modifier = Modifier.weight(0.44f)
            )

            // 2. Interactive Multi-Track Timeline (~22%)
            InteractiveTimeline(
                draft = state.activeDraft,
                selectedSlotIndex = state.selectedSlotIndex,
                onSelectSlot = { vm.selectSlot(it) },
                onOpenAutoBeatSync = { vm.setShowAutoWizard(true, step = 1) },
                modifier = Modifier.weight(0.22f)
            )

            // 3. Bottom Studio Tool Panels (~34%)
            StudioToolPanels(
                activeTab = state.activeTab,
                onSelectTab = { vm.selectTab(it) },
                draft = state.activeDraft,
                selectedSlotIndex = state.selectedSlotIndex,
                onPickVideos = {
                    pendingAutoSpread = false
                    multiVideoPicker.launch(arrayOf("video/*"))
                },
                onReuseMediaItem = { vm.reuseMediaItemAsNewSlot(it) },
                onUpdateInPoint = { vm.updateSelectedSlotInPoint(it) },
                onUpdateSpeed = { vm.updateSelectedSlotSpeed(it) },
                onDuplicateSlot = { vm.duplicateSelectedSlot() },
                onDeleteSlot = { vm.deleteSelectedSlot() },
                onMoveSlot = { vm.moveSelectedSlot(it) },
                onSelectLut = { lut, intensity -> vm.setSlotLut(lut, intensity) },
                onApplyLutToAll = { vm.applyLutToAllSlots() },
                onUpdateAdjustments = { vm.updateSlotAdjustments(it) },
                onApplyAdjustToAll = { vm.applyAdjustmentsToAllSlots() },
                onSelectBeatFx = { vm.setSlotBeatFx(it) },
                onApplyBeatFxToDrop = { vm.applyBeatFxToDropSection(it) },
                onUpdateTextOverlay = { vm.updateTextOverlay(it) },
                onOpenAutoBeatSync = { vm.setShowAutoWizard(true, step = 1) },
                onUpdateAudioVolumes = { m, c, f -> vm.updateAudioVolumes(m, c, f) },
                modifier = Modifier.weight(0.34f)
            )
        }
    }

    if (state.showAutoWizard) {
        AutoBeatSyncWizardDialog(
            draft = state.activeDraft,
            step = state.wizardStep,
            isResolvingAudio = state.isResolvingAudio,
            audioStatusMessage = state.audioStatusMessage,
            onDismiss = { vm.setShowAutoWizard(false) },
            onSetStep = { vm.setShowAutoWizard(true, it) },
            onResolveUrl = { vm.resolveAudioUrl(it) },
            onPickVideoForAudioExtract = { videoAudioExtractPicker.launch(arrayOf("video/*")) },
            onPickLocalAudio = { localAudioPicker.launch(arrayOf("audio/*")) },
            onUpdateSpanAndDensity = { start, dur, density -> vm.updateAudioSpanAndDensity(start, dur, density) },
            onPickVideosForTimeline = {
                pendingAutoSpread = true
                multiVideoPicker.launch(arrayOf("video/*"))
            }
        )
    }

    if (state.showExportModal) {
        Export4KModalDialog(
            resolution = state.exportResolution,
            fps = state.exportFps,
            isDevice4KCapable = state.isDevice4KCapable,
            isExporting = state.isExporting,
            progress = state.exportProgress,
            outcome = state.exportOutcome,
            onSelectOptions = { res, fps -> vm.setExportOptions(res, fps) },
            onStartExport = { vm.startExport() },
            onDismiss = { vm.setShowExportModal(false) }
        )
    }
}
