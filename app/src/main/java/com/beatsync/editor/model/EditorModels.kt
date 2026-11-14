package com.beatsync.editor.model

import android.net.Uri

enum class AspectRatioOption(
    val label: String,
    val width: Int,
    val height: Int
) {
    VERTICAL_9_16("9:16 Reels / TikTok", 1080, 1920),
    LANDSCAPE_16_9("16:9 YouTube", 1920, 1080)
}

enum class BeatDensity(
    val label: String,
    val description: String,
    val beatStep: Int
) {
    CHILL("Chill", "Ganti klip tiap 4 beat (sinematik)", 4),
    DYNAMIC("Dynamic", "Ganti klip tiap 2 beat (seimbang)", 2),
    HYPE("Hype", "Ganti klip di tiap hentakan kuat", 1)
}

enum class VideoFilterPreset(
    val label: String,
    val accentHex: Long
) {
    ORIGINAL("Original", 0xFF94A3B8),
    TEAL_ORANGE("Teal & Orange", 0xFF06B6D4),
    WARM_VINTAGE("Warm Vintage", 0xFFF59E0B),
    MOODY_DARK("Moody Dark", 0xFF6366F1),
    VIVID_POP("Vivid Pop", 0xFFEC4899),
    BW_CONTRAST("B&W Contrast", 0xFFE2E8F0)
}

data class AudioTrackInfo(
    val title: String,
    val sourceLabel: String,
    val localUri: Uri,
    val totalDurationMs: Long
)

data class VideoClipItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long
)

data class BeatAnalysisResult(
    val beatTimestampsMs: List<Long>,
    val estimatedBpm: Int,
    val waveformPeaks: List<Float>
)

sealed class ExportState {
    data object Idle : ExportState()
    data class Exporting(val progressPercent: Int, val statusText: String) : ExportState()
    data class Success(val outputUri: Uri, val fileName: String) : ExportState()
    data class Error(val message: String) : ExportState()
}
