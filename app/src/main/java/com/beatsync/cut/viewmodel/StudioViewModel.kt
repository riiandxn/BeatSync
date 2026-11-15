package com.beatsync.cut.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beatsync.cut.audio.AudioLinkResolver
import com.beatsync.cut.audio.BeatDetector
import com.beatsync.cut.data.ProjectRepository
import com.beatsync.cut.model.*
import com.beatsync.cut.render.BeatVideoExporter
import com.beatsync.cut.render.ExportFps
import com.beatsync.cut.render.ExportOutcome
import com.beatsync.cut.render.ExportResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AppScreen {
    DRAFTS_HOME,
    STUDIO_EDITOR
}

enum class StudioTab(val title: String) {
    KLIP("Klip"),
    FILTER("Filter"),
    ADJUST("Adjust"),
    BEAT_FX("Beat FX"),
    TEKS("Teks"),
    AUDIO("Audio")
}

data class StudioUiState(
    val currentScreen: AppScreen = AppScreen.STUDIO_EDITOR,
    val drafts: List<ProjectDraft> = emptyList(),
    val activeDraft: ProjectDraft = ProjectDraft(),
    val selectedSlotIndex: Int = 0,
    val activeTab: StudioTab = StudioTab.KLIP,
    val isPlaying: Boolean = false,
    val playheadMs: Long = 0L,
    val autoSaveStatus: String = "Tersimpan",
    // 2-Step Auto Beat-Sync Wizard Modal
    val showAutoWizard: Boolean = false,
    val wizardStep: Int = 1,
    val isResolvingAudio: Boolean = false,
    val audioStatusMessage: String = "",
    // Export Modal
    val showExportModal: Boolean = false,
    val exportResolution: ExportResolution = ExportResolution.RES_4K,
    val exportFps: ExportFps = ExportFps.FPS_30,
    val isDevice4KCapable: Boolean = true,
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val exportOutcome: ExportOutcome? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProjectRepository(application)
    private val audioResolver = AudioLinkResolver(application)

    private val _uiState = MutableStateFlow(
        StudioUiState(
            currentScreen = AppScreen.STUDIO_EDITOR,
            isDevice4KCapable = BeatVideoExporter.isDevice4KSupported()
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.drafts.collect { list ->
                _uiState.update { it.copy(drafts = list) }
            }
        }
    }

    fun navigateToDraftsHome() {
        stopPlayback()
        repository.reloadAll()
        _uiState.update { it.copy(currentScreen = AppScreen.DRAFTS_HOME) }
    }

    fun createNewProject(openAutoWizard: Boolean = false) {
        stopPlayback()
        val count = _uiState.value.drafts.size + 1
        val fresh = ProjectDraft(title = "Project #$count")
        repository.saveProject(fresh)
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.STUDIO_EDITOR,
                activeDraft = fresh,
                selectedSlotIndex = 0,
                playheadMs = 0L,
                showAutoWizard = openAutoWizard,
                wizardStep = 1
            )
        }
    }

    fun openDraft(draft: ProjectDraft) {
        stopPlayback()
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.STUDIO_EDITOR,
                activeDraft = draft,
                selectedSlotIndex = 0,
                playheadMs = 0L
            )
        }
    }

    fun duplicateDraft(draft: ProjectDraft) {
        repository.duplicateProject(draft)
    }

    fun deleteDraft(projectId: String) {
        repository.deleteProject(projectId)
    }

    fun relinkMedia(projectId: String, mediaBinId: String, newUri: Uri) {
        viewModelScope.launch {
            val (name, durationMs) = probeVideoMetadata(newUri)
            val updated = repository.relinkMediaItem(projectId, mediaBinId, newUri, name, durationMs)
            if (updated != null && _uiState.value.activeDraft.id == projectId) {
                _uiState.update { it.copy(activeDraft = updated) }
            }
        }
    }

    fun updateProjectTitle(newTitle: String) {
        mutateDraft { it.copy(title = newTitle.ifBlank { "Untitled Cut" }) }
    }

    fun toggleAspectRatio() {
        mutateDraft { draft ->
            val next = if (draft.aspectRatio == AspectRatioOption.PORTRAIT_9_16)
                AspectRatioOption.LANDSCAPE_16_9 else AspectRatioOption.PORTRAIT_9_16
            draft.copy(aspectRatio = next)
        }
    }

    fun selectTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectSlot(index: Int) {
        val slots = _uiState.value.activeDraft.slots
        if (index !in slots.indices) return
        val startMs = slots.take(index).sumOf { it.durationMs }
        _uiState.update {
            it.copy(selectedSlotIndex = index, playheadMs = startMs)
        }
    }

    // --- MEDIA BIN & MULTI-USE SLIP-TRIM ---

    fun importVideosToMediaBin(uris: List<Uri>, autoGenerateSlots: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newItems = mutableListOf<MediaBinItem>()
            for (uri in uris) {
                try {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                val (name, durationMs) = probeVideoMetadata(uri)
                newItems.add(
                    MediaBinItem(
                        uriString = uri.toString(),
                        displayName = name,
                        totalDurationMs = durationMs
                    )
                )
            }

            mutateDraft { draft ->
                val mergedBin = draft.mediaBin + newItems
                val updatedSlots = if (autoGenerateSlots || draft.slots.isEmpty()) {
                    generateSmartSpreadSlots(mergedBin, draft.audio)
                } else {
                    draft.slots
                }
                draft.copy(mediaBin = mergedBin, slots = updatedSlots)
            }
        }
    }

    /**
     * Reuses an existing MediaBinItem (e.g., a 3-minute take) by appending a new slot
     * starting at a smart offset so 1 video can be used unlimited times.
     */
    fun reuseMediaItemAsNewSlot(mediaBinItem: MediaBinItem) {
        mutateDraft { draft ->
            val usageCount = draft.slots.count { it.mediaBinId == mediaBinItem.id }
            val stepOffset = (mediaBinItem.totalDurationMs / 6L).coerceAtLeast(1500L)
            val suggestedInPoint = ((usageCount * stepOffset) % (mediaBinItem.totalDurationMs - 1500L).coerceAtLeast(1L))
            val newSlot = TimelineSlot(
                mediaBinId = mediaBinItem.id,
                inPointMs = suggestedInPoint,
                durationMs = 1500L
            )
            draft.copy(slots = draft.slots + newSlot)
        }
        val newIndex = (_uiState.value.activeDraft.slots.size - 1).coerceAtLeast(0)
        selectSlot(newIndex)
    }

    fun updateSelectedSlotInPoint(newInPointMs: Long) {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            val slot = slots.getOrNull(idx) ?: return@mutateDraft draft
            val media = draft.mediaBin.find { it.id == slot.mediaBinId }
            val maxIn = ((media?.totalDurationMs ?: 180_000L) - slot.durationMs).coerceAtLeast(0L)
            slots[idx] = slot.copy(inPointMs = newInPointMs.coerceIn(0L, maxIn))
            draft.copy(slots = slots)
        }
    }

    fun updateSelectedSlotSpeed(speed: SpeedPreset) {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            val slot = slots.getOrNull(idx) ?: return@mutateDraft draft
            slots[idx] = slot.copy(speed = speed)
            draft.copy(slots = slots)
        }
    }

    fun duplicateSelectedSlot() {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            val slot = slots.getOrNull(idx) ?: return@mutateDraft draft
            val media = draft.mediaBin.find { it.id == slot.mediaBinId }
            val nextIn = ((slot.inPointMs + slot.durationMs + 1000L)
                .coerceAtMost(((media?.totalDurationMs ?: 180_000L) - slot.durationMs).coerceAtLeast(0L)))
            slots.add(idx + 1, slot.copy(id = UUID.randomUUID().toString(), inPointMs = nextIn))
            draft.copy(slots = slots)
        }
        selectSlot(idx + 1)
    }

    fun deleteSelectedSlot() {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            if (idx in slots.indices) slots.removeAt(idx)
            draft.copy(slots = slots)
        }
        val nextIdx = (idx - 1).coerceAtLeast(0)
        _uiState.update { it.copy(selectedSlotIndex = nextIdx) }
    }

    fun moveSelectedSlot(delta: Int) {
        val idx = _uiState.value.selectedSlotIndex
        val target = idx + delta
        val size = _uiState.value.activeDraft.slots.size
        if (idx !in 0 until size || target !in 0 until size) return
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            val item = slots.removeAt(idx)
            slots.add(target, item)
            draft.copy(slots = slots)
        }
        selectSlot(target)
    }

    // --- CINEMATIC FILM LUTS, ADJUSTMENTS & BEAT FX ---

    fun setSlotLut(lutPreset: FilmLutPreset, intensity: Float? = null) {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            val slot = slots.getOrNull(idx) ?: return@mutateDraft draft
            slots[idx] = slot.copy(
                lutId = lutPreset.id,
                lutIntensity = intensity ?: slot.lutIntensity
            )
            draft.copy(slots = slots)
        }
    }

    fun applyLutToAllSlots() {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val ref = draft.slots.getOrNull(idx) ?: return@mutateDraft draft
            draft.copy(
                slots = draft.slots.map {
                    it.copy(lutId = ref.lutId, lutIntensity = ref.lutIntensity)
                }
            )
        }
    }

    fun updateSlotAdjustments(newAdj: ColorAdjustments) {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            val slot = slots.getOrNull(idx) ?: return@mutateDraft draft
            slots[idx] = slot.copy(adjustments = newAdj)
            draft.copy(slots = slots)
        }
    }

    fun applyAdjustmentsToAllSlots() {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val ref = draft.slots.getOrNull(idx) ?: return@mutateDraft draft
            draft.copy(slots = draft.slots.map { it.copy(adjustments = ref.adjustments) })
        }
    }

    fun setSlotBeatFx(fx: BeatFxType) {
        val idx = _uiState.value.selectedSlotIndex
        mutateDraft { draft ->
            val slots = draft.slots.toMutableList()
            val slot = slots.getOrNull(idx) ?: return@mutateDraft draft
            slots[idx] = slot.copy(beatFx = fx)
            draft.copy(slots = slots)
        }
    }

    fun applyBeatFxToDropSection(fx: BeatFxType) {
        mutateDraft { draft ->
            val halfIdx = draft.slots.size / 2
            draft.copy(
                slots = draft.slots.mapIndexed { index, slot ->
                    if (index >= halfIdx) slot.copy(beatFx = fx) else slot
                }
            )
        }
    }

    fun updateTextOverlay(config: TextOverlayConfig) {
        mutateDraft { it.copy(textOverlay = config) }
    }

    // --- 3-LAYER AUDIO RESOLVER & 2-STEP AUTO BEAT-SYNC ---

    fun setShowAutoWizard(show: Boolean, step: Int = 1) {
        _uiState.update {
            it.copy(showAutoWizard = show, wizardStep = step, audioStatusMessage = "")
        }
    }

    fun resolveAudioUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingAudio = true, audioStatusMessage = "Menghubungkan...") }
            val result = audioResolver.resolveFromUrl(url) { msg ->
                _uiState.update { it.copy(audioStatusMessage = msg) }
            }
            result.fold(
                onSuccess = { resolved ->
                    applyResolvedAudio(resolved.title, resolved.localFilePath, resolved.durationMs)
                    _uiState.update { it.copy(isResolvingAudio = false, audioStatusMessage = "Berhasil: ${resolved.title}") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isResolvingAudio = false, audioStatusMessage = err.message ?: "Gagal ambil link") }
                }
            )
        }
    }

    fun extractAudioFromVideo(videoUri: Uri) {
        viewModelScope.launch {
            val (name, _) = probeVideoMetadata(videoUri)
            _uiState.update { it.copy(isResolvingAudio = true, audioStatusMessage = "Mengekstrak audio dari video...") }
            val result = audioResolver.extractAudioFromVideoUri(videoUri, name) { msg ->
                _uiState.update { it.copy(audioStatusMessage = msg) }
            }
            result.fold(
                onSuccess = { resolved ->
                    applyResolvedAudio(resolved.title, resolved.localFilePath, resolved.durationMs)
                    _uiState.update { it.copy(isResolvingAudio = false, audioStatusMessage = "Audio diekstrak dari $name") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isResolvingAudio = false, audioStatusMessage = err.message ?: "Gagal ekstrak audio") }
                }
            )
        }
    }

    fun importLocalAudio(audioUri: Uri) {
        viewModelScope.launch {
            val (name, _) = probeVideoMetadata(audioUri)
            _uiState.update { it.copy(isResolvingAudio = true, audioStatusMessage = "Memuat file audio...") }
            val result = audioResolver.importLocalAudioUri(audioUri, name)
            result.fold(
                onSuccess = { resolved ->
                    applyResolvedAudio(resolved.title, resolved.localFilePath, resolved.durationMs)
                    _uiState.update { it.copy(isResolvingAudio = false, audioStatusMessage = "Lagu dimuat: $name") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isResolvingAudio = false, audioStatusMessage = err.message ?: "Gagal memuat audio") }
                }
            )
        }
    }

    fun updateAudioSpanAndDensity(spanStartMs: Long? = null, spanDurationMs: Long? = null, density: BeatDensity? = null) {
        viewModelScope.launch {
            val curr = _uiState.value.activeDraft.audio
            val newDur = (spanDurationMs ?: curr.spanDurationMs).coerceIn(5000L, 60_000L)
            val maxStart = (curr.totalDurationMs - newDur).coerceAtLeast(0L)
            val newStart = (spanStartMs ?: curr.spanStartMs).coerceIn(0L, maxStart)
            val newDensity = density ?: curr.beatDensity

            val analysis = BeatDetector.analyzeSpan(curr.localAudioPath, newStart, newDur, newDensity)
            mutateDraft { draft ->
                val updatedAudio = draft.audio.copy(
                    spanStartMs = newStart,
                    spanDurationMs = newDur,
                    beatDensity = newDensity,
                    beatTimestampsMs = analysis.beatTimestampsMs,
                    waveformPeaks = analysis.waveformPeaks,
                    bpmEstimate = analysis.estimatedBpm
                )
                val syncedSlots = if (draft.mediaBin.isNotEmpty()) {
                    generateSmartSpreadSlots(draft.mediaBin, updatedAudio)
                } else {
                    draft.slots
                }
                draft.copy(audio = updatedAudio, slots = syncedSlots)
            }
        }
    }

    fun updateAudioVolumes(musicVol: Float, clipVol: Float, fadeInOut: Boolean) {
        mutateDraft { draft ->
            draft.copy(
                audio = draft.audio.copy(
                    musicVolume = musicVol.coerceIn(0f, 1.5f),
                    originalClipVolume = clipVol.coerceIn(0f, 1f),
                    fadeInOut = fadeInOut
                )
            )
        }
    }

    private suspend fun applyResolvedAudio(title: String, localPath: String, totalDurationMs: Long) {
        val curr = _uiState.value.activeDraft.audio
        val spanDur = curr.spanDurationMs.coerceAtMost(totalDurationMs).coerceAtLeast(5000L)
        val analysis = BeatDetector.analyzeSpan(localPath, 0L, spanDur, curr.beatDensity)
        mutateDraft { draft ->
            val newAudio = draft.audio.copy(
                title = title,
                localAudioPath = localPath,
                totalDurationMs = totalDurationMs,
                spanStartMs = 0L,
                spanDurationMs = spanDur,
                bpmEstimate = analysis.estimatedBpm,
                beatTimestampsMs = analysis.beatTimestampsMs,
                waveformPeaks = analysis.waveformPeaks
            )
            val syncedSlots = if (draft.mediaBin.isNotEmpty()) {
                generateSmartSpreadSlots(draft.mediaBin, newAudio)
            } else {
                draft.slots
            }
            draft.copy(audio = newAudio, slots = syncedSlots)
        }
    }

    /**
     * Distributes cuts across all beat timestamps, spreading selection windows evenly
     * across long recordings (e.g., 3-minute takes) so every cut shows a fresh moment.
     */
    private fun generateSmartSpreadSlots(mediaBin: List<MediaBinItem>, audio: AudioTrackConfig): List<TimelineSlot> {
        val validMedia = mediaBin.filterNot { it.isMissing }
        if (validMedia.isEmpty()) return emptyList()

        val beats = audio.beatTimestampsMs.ifEmpty { listOf(0L, 1500L, 3000L, 4500L, 6000L, 7500L) }
        val totalSpan = audio.spanDurationMs.coerceAtLeast(1500L)
        val slotCount = beats.size

        return List(slotCount) { idx ->
            val startBeat = beats[idx]
            val endBeat = if (idx + 1 < beats.size) beats[idx + 1] else totalSpan
            val slotDur = (endBeat - startBeat).coerceIn(280L, 4500L)

            val media = validMedia[idx % validMedia.size]
            val occurrencesOfThisMedia = idx / validMedia.size
            val totalUsesForMedia = ((slotCount - (idx % validMedia.size) + validMedia.size - 1) / validMedia.size).coerceAtLeast(1)

            val usableSpan = (media.totalDurationMs - slotDur).coerceAtLeast(0L)
            val spreadInPoint = if (totalUsesForMedia > 1 && usableSpan > 0L) {
                (usableSpan * occurrencesOfThisMedia) / totalUsesForMedia
            } else {
                (usableSpan / 3L)
            }

            TimelineSlot(
                mediaBinId = media.id,
                inPointMs = spreadInPoint,
                durationMs = slotDur
            )
        }
    }

    // --- LIVE PREVIEW PLAYBACK ---

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    fun seekPlayhead(ms: Long) {
        val total = _uiState.value.activeDraft.totalTimelineDurationMs.coerceAtLeast(1000L)
        val clamped = ms.coerceIn(0L, total)
        val slotIdx = findSlotIndexAtTime(clamped)
        _uiState.update { it.copy(playheadMs = clamped, selectedSlotIndex = slotIdx) }
    }

    private fun startPlayback() {
        val total = _uiState.value.activeDraft.totalTimelineDurationMs
        if (total <= 0L) return
        if (_uiState.value.playheadMs >= total - 50L) {
            _uiState.update { it.copy(playheadMs = 0L, selectedSlotIndex = 0) }
        }
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive && _uiState.value.isPlaying) {
                delay(33L)
                val now = System.currentTimeMillis()
                val delta = now - lastTick
                lastTick = now
                val maxDur = _uiState.value.activeDraft.totalTimelineDurationMs.coerceAtLeast(1000L)
                val nextMs = _uiState.value.playheadMs + delta
                if (nextMs >= maxDur) {
                    _uiState.update { it.copy(isPlaying = false, playheadMs = 0L, selectedSlotIndex = 0) }
                    break
                } else {
                    val slotIdx = findSlotIndexAtTime(nextMs)
                    _uiState.update { it.copy(playheadMs = nextMs, selectedSlotIndex = slotIdx) }
                }
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    private fun findSlotIndexAtTime(ms: Long): Int {
        var acc = 0L
        val slots = _uiState.value.activeDraft.slots
        for (i in slots.indices) {
            val end = acc + slots[i].durationMs
            if (ms < end) return i
            acc = end
        }
        return (slots.size - 1).coerceAtLeast(0)
    }

    // --- 4K 30FPS / 1080P EXPORT ---

    fun setShowExportModal(show: Boolean) {
        stopPlayback()
        _uiState.update { it.copy(showExportModal = show, exportOutcome = null, exportProgress = 0) }
    }

    fun setExportOptions(res: ExportResolution? = null, fps: ExportFps? = null) {
        _uiState.update {
            it.copy(
                exportResolution = res ?: it.exportResolution,
                exportFps = fps ?: it.exportFps
            )
        }
    }

    fun startExport() {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportProgress = 0, exportOutcome = null) }
            val outcome = BeatVideoExporter.exportProject(
                context = getApplication(),
                draft = _uiState.value.activeDraft,
                resolution = _uiState.value.exportResolution,
                fps = _uiState.value.exportFps,
                onProgress = { pct ->
                    _uiState.update { it.copy(exportProgress = pct) }
                }
            )
            _uiState.update {
                it.copy(isExporting = false, exportProgress = if (outcome.success) 100 else 0, exportOutcome = outcome)
            }
        }
    }

    // --- AUTO-SAVE HELPER ---

    private fun mutateDraft(transform: (ProjectDraft) -> ProjectDraft) {
        val updated = transform(_uiState.value.activeDraft)
        _uiState.update { it.copy(activeDraft = updated, autoSaveStatus = "Menyimpan...") }
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(350L)
            repository.saveProject(updated)
            _uiState.update { it.copy(autoSaveStatus = "Tersimpan") }
        }
    }

    private suspend fun probeVideoMetadata(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        var displayName = "clip_${System.currentTimeMillis() % 10000}.mp4"
        try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) displayName = cursor.getString(idx) ?: displayName
                }
            }
        } catch (_: Exception) {
        }

        var durationMs = 15_000L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull()?.coerceAtLeast(1000L) ?: 15_000L
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        displayName to durationMs
    }
}
