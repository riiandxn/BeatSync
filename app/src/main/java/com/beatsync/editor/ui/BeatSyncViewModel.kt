package com.beatsync.editor.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.beatsync.editor.audio.AudioLinkResolver
import com.beatsync.editor.audio.BeatDetector
import com.beatsync.editor.model.AspectRatioOption
import com.beatsync.editor.model.AudioTrackInfo
import com.beatsync.editor.model.BeatAnalysisResult
import com.beatsync.editor.model.BeatDensity
import com.beatsync.editor.model.ExportState
import com.beatsync.editor.model.VideoClipItem
import com.beatsync.editor.model.VideoFilterPreset
import com.beatsync.editor.video.BeatVideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val linkInput: String = "",
    val isLoadingAudio: Boolean = false,
    val audioError: String? = null,
    val audioTrack: AudioTrackInfo? = null,
    val maxDurationSec: Int = 30,
    val spanStartMs: Long = 0L,
    val spanEndMs: Long = 30_000L,
    val isPlayingAudioPreview: Boolean = false,
    val beatDensity: BeatDensity = BeatDensity.DYNAMIC,
    val isAnalyzingBeats: Boolean = false,
    val beatAnalysis: BeatAnalysisResult? = null,
    val videoClips: List<VideoClipItem> = emptyList(),
    val aspectRatio: AspectRatioOption = AspectRatioOption.VERTICAL_9_16,
    val filterPreset: VideoFilterPreset = VideoFilterPreset.TEAL_ORANGE,
    val filterIntensity: Float = 0.85f,
    val enableBeatPunch: Boolean = true,
    val exportState: ExportState = ExportState.Idle
)

@UnstableApi
class BeatSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = AudioLinkResolver(application)
    private val beatDetector = BeatDetector(application)
    private val exporter = BeatVideoExporter(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var previewPlayer: ExoPlayer? = null
    private var previewMonitorJob: Job? = null
    private var beatJob: Job? = null

    fun onLinkInputChange(value: String) {
        _uiState.update { it.copy(linkInput = value, audioError = null) }
    }

    fun loadAudioFromLink() {
        val url = _uiState.value.linkInput
        viewModelScope.launch {
            stopAudioPreview()
            _uiState.update { it.copy(isLoadingAudio = true, audioError = null) }
            val result = resolver.resolveFromLink(url)
            result.fold(
                onSuccess = { track -> applyLoadedTrack(track) },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoadingAudio = false, audioError = err.message ?: "Gagal memuat lagu")
                    }
                }
            )
        }
    }

    fun loadLocalAudio(uri: Uri) {
        viewModelScope.launch {
            stopAudioPreview()
            _uiState.update { it.copy(isLoadingAudio = true, audioError = null) }
            val name = queryDisplayName(uri) ?: "Lagu Lokal"
            val result = resolver.resolveFromLocalUri(uri, name)
            result.fold(
                onSuccess = { track -> applyLoadedTrack(track) },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoadingAudio = false, audioError = err.message ?: "Gagal membaca file audio")
                    }
                }
            )
        }
    }

    private fun applyLoadedTrack(track: AudioTrackInfo) {
        val targetSpanMs = (_uiState.value.maxDurationSec * 1000L).coerceAtMost(track.totalDurationMs)
        _uiState.update {
            it.copy(
                isLoadingAudio = false,
                audioError = null,
                audioTrack = track,
                spanStartMs = 0L,
                spanEndMs = targetSpanMs
            )
        }
        reanalyzeBeats()
    }

    fun setMaxDurationSec(seconds: Int) {
        val clampedSec = seconds.coerceIn(10, 60)
        val track = _uiState.value.audioTrack
        if (track == null) {
            _uiState.update { it.copy(maxDurationSec = clampedSec) }
            return
        }
        val desiredMs = clampedSec * 1000L
        var newStart = _uiState.value.spanStartMs
        var newEnd = (newStart + desiredMs).coerceAtMost(track.totalDurationMs)
        if (newEnd - newStart < desiredMs && track.totalDurationMs >= desiredMs) {
            newStart = (track.totalDurationMs - desiredMs).coerceAtLeast(0L)
            newEnd = track.totalDurationMs
        }
        _uiState.update {
            it.copy(
                maxDurationSec = clampedSec,
                spanStartMs = newStart,
                spanEndMs = newEnd
            )
        }
        reanalyzeBeats()
    }

    fun updateAudioSpan(startMs: Long, endMs: Long) {
        val track = _uiState.value.audioTrack ?: return
        val maxSpanMs = 60_000L
        val safeStart = startMs.coerceIn(0L, (track.totalDurationMs - 2000L).coerceAtLeast(0L))
        val safeEnd = endMs.coerceIn(safeStart + 2000L, minOf(track.totalDurationMs, safeStart + maxSpanMs))
        _uiState.update {
            it.copy(spanStartMs = safeStart, spanEndMs = safeEnd)
        }
    }

    fun onSpanDragFinished() {
        if (_uiState.value.isPlayingAudioPreview) {
            startAudioPreview()
        }
        reanalyzeBeats()
    }

    fun setBeatDensity(density: BeatDensity) {
        _uiState.update { it.copy(beatDensity = density) }
        reanalyzeBeats()
    }

    private fun reanalyzeBeats() {
        val state = _uiState.value
        val track = state.audioTrack ?: return
        beatJob?.cancel()
        beatJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingBeats = true) }
            val result = beatDetector.analyzeBeats(
                audioUri = track.localUri,
                startMs = state.spanStartMs,
                endMs = state.spanEndMs,
                density = state.beatDensity
            )
            _uiState.update {
                it.copy(isAnalyzingBeats = false, beatAnalysis = result)
            }
        }
    }

    fun toggleAudioPreview() {
        if (_uiState.value.isPlayingAudioPreview) {
            stopAudioPreview()
        } else {
            startAudioPreview()
        }
    }

    private fun startAudioPreview() {
        val state = _uiState.value
        val track = state.audioTrack ?: return
        stopAudioPreview()

        val player = ExoPlayer.Builder(getApplication()).build().apply {
            setMediaItem(MediaItem.fromUri(track.localUri))
            prepare()
            seekTo(state.spanStartMs)
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        stopAudioPreview()
                    }
                }
            })
        }
        previewPlayer = player
        _uiState.update { it.copy(isPlayingAudioPreview = true) }

        previewMonitorJob = viewModelScope.launch {
            while (true) {
                delay(150L)
                val pos = previewPlayer?.currentPosition ?: break
                if (pos >= _uiState.value.spanEndMs) {
                    previewPlayer?.seekTo(_uiState.value.spanStartMs)
                }
            }
        }
    }

    fun stopAudioPreview() {
        previewMonitorJob?.cancel()
        previewPlayer?.release()
        previewPlayer = null
        _uiState.update { it.copy(isPlayingAudioPreview = false) }
    }

    fun addVideoClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val dur = extractVideoDurationMs(uri)
                    if (dur > 200L) {
                        VideoClipItem(
                            uri = uri,
                            name = queryDisplayName(uri) ?: "Klip Video",
                            durationMs = dur
                        )
                    } else null
                }
            }
            _uiState.update {
                it.copy(videoClips = it.videoClips + loaded)
            }
        }
    }

    fun removeVideoClip(index: Int) {
        _uiState.update { state ->
            val updated = state.videoClips.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            state.copy(videoClips = updated)
        }
    }

    fun moveClipLeft(index: Int) {
        if (index <= 0) return
        _uiState.update { state ->
            val list = state.videoClips.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            state.copy(videoClips = list)
        }
    }

    fun setAspectRatio(option: AspectRatioOption) {
        _uiState.update { it.copy(aspectRatio = option) }
    }

    fun setFilterPreset(preset: VideoFilterPreset) {
        _uiState.update { it.copy(filterPreset = preset) }
    }

    fun setFilterIntensity(value: Float) {
        _uiState.update { it.copy(filterIntensity = value.coerceIn(0f, 1f)) }
    }

    fun setBeatPunch(enabled: Boolean) {
        _uiState.update { it.copy(enableBeatPunch = enabled) }
    }

    fun exportVideo() {
        val state = _uiState.value
        val track = state.audioTrack ?: return
        val cuts = state.beatAnalysis?.beatTimestampsMs ?: return
        if (state.videoClips.isEmpty()) return

        stopAudioPreview()
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Exporting(2, "Memulai proses...")) }
            val result = exporter.exportBeatSyncedVideo(
                clips = state.videoClips,
                audioTrack = track,
                audioStartMs = state.spanStartMs,
                audioEndMs = state.spanEndMs,
                beatCutPointsMs = cuts,
                aspectRatio = state.aspectRatio,
                filterPreset = state.filterPreset,
                filterIntensity = state.filterIntensity,
                enableBeatPunch = state.enableBeatPunch,
                onProgress = { pct, status ->
                    _uiState.update {
                        it.copy(exportState = ExportState.Exporting(pct, status))
                    }
                }
            )
            result.fold(
                onSuccess = { (uri, name) ->
                    _uiState.update {
                        it.copy(exportState = ExportState.Success(uri, name))
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(exportState = ExportState.Error(err.message ?: "Terjadi kesalahan saat export."))
                    }
                }
            )
        }
    }

    fun dismissExportState() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    private fun extractVideoDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(getApplication(), uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPreview()
    }
}
