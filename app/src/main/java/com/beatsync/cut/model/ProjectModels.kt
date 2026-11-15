package com.beatsync.cut.model

import java.util.UUID

enum class AspectRatioOption(val label: String, val ratioFloat: Float, val width4K: Int, val height4K: Int, val width1080: Int, val height1080: Int) {
    PORTRAIT_9_16("9:16", 9f / 16f, 2160, 3840, 1080, 1920),
    LANDSCAPE_16_9("16:9", 16f / 9f, 3840, 2160, 1920, 1080)
}

enum class BeatDensity(val label: String, val minIntervalMs: Long, val subtitle: String) {
    CHILL("Chill", 1200L, "Santai / Cinematic"),
    DYNAMIC("Dynamic", 600L, "Seimbang / Vlog"),
    HYPE("Hype", 300L, "Drop / Jedag-Jedug")
}

enum class FilmLutPreset(val id: String, val displayName: String, val category: String, val previewHex: Long) {
    ORIGINAL("original", "Original", "Natural", 0xFF3A3F4B),
    KODAK_PORTRA("kodak_portra", "Kodak Portra 400", "Film", 0xFFD99B66),
    CINESTILL_800T("cinestill_800t", "CineStill 800T", "Night", 0xFF1D6B87),
    TEAL_ORANGE("teal_orange", "Teal & Orange", "Cinema", 0xFF0E7490),
    BLEACH_BYPASS("bleach_bypass", "Bleach Bypass", "Cinema", 0xFF64748B),
    FUJI_SUPERIA("fuji_superia", "Fuji Superia", "Film", 0xFF4D7C5B),
    GOLDEN_HOUR("golden_hour", "Golden Hour", "Warm", 0xFFE07A2E),
    LATTE_MATTE("latte_matte", "Latte Matte", "Daily", 0xFFA38970),
    GOTHAM_NOIR("gotham_noir", "Gotham Noir", "Moody", 0xFF27303F),
    SUPER_8("super_8", "Super 8 Vintage", "Retro", 0xFFB45309),
    VHS_90S("vhs_90s", "VHS 90s", "Retro", 0xFF6D28D9),
    LEICA_MONO("leica_mono", "Leica Mono", "B&W", 0xFF52525B)
}

data class ColorAdjustments(
    val exposure: Float = 0f,      // -1f .. +1f
    val contrast: Float = 0f,      // -1f .. +1f
    val saturation: Float = 0f,    // -1f .. +1f
    val warmth: Float = 0f,        // -1f .. +1f
    val highlights: Float = 0f,    // -1f .. +1f
    val shadows: Float = 0f,       // -1f .. +1f
    val filmGrain: Float = 0.15f,  // 0f .. 1f
    val vignette: Float = 0.1f     // 0f .. 1f
)

enum class BeatFxType(val id: String, val label: String) {
    NONE("none", "Hard Cut"),
    WHITE_FLASH("white_flash", "White Flash"),
    SHAKE_SUBTLE("shake_subtle", "Shake Subtle"),
    SHAKE_HARD("shake_hard", "Shake Hard"),
    ZOOM_PUNCH("zoom_punch", "Zoom Punch"),
    WHIP_BLUR("whip_blur", "Whip Zoom")
}

enum class SpeedPreset(val label: String, val multiplier: Float) {
    SLOW_05("0.5x", 0.5f),
    SLOW_075("0.75x", 0.75f),
    NORMAL("1.0x", 1.0f),
    FAST_15("1.5x", 1.5f),
    FAST_20("2.0x", 2.0f),
    VELOCITY_FLASH("Flash In", 1.25f)
}

enum class TextFontStyle(val label: String) {
    SANS_BOLD("Sans Bold"),
    CINEMA_SERIF("Cinema Serif"),
    MONO_TAG("Mono Tag")
}

enum class TextAnimStyle(val label: String) {
    NONE("None"),
    POP("Pop"),
    FADE("Fade"),
    SLIDE_UP("Slide Up")
}

data class TextOverlayConfig(
    val enabled: Boolean = false,
    val text: String = "CINEMATIC CUT",
    val fontStyle: TextFontStyle = TextFontStyle.SANS_BOLD,
    val textColorHex: Long = 0xFFFFFFFF,
    val hasPillBackground: Boolean = true,
    val animStyle: TextAnimStyle = TextAnimStyle.POP,
    val offsetX: Float = 0f, // -0.4f .. +0.4f normalized
    val offsetY: Float = 0.25f // -0.4f .. +0.4f normalized
)

/**
 * Imported once into the Project Media Bin; can be referenced by unlimited TimelineSlots.
 */
data class MediaBinItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val totalDurationMs: Long,
    val isMissing: Boolean = false
)

/**
 * Independent slot on the timeline. Multiple slots can share the exact same mediaBinId
 * with completely different inPointMs windows (e.g., 5 moments from a single 3-minute take).
 */
data class TimelineSlot(
    val id: String = UUID.randomUUID().toString(),
    val mediaBinId: String,
    val inPointMs: Long = 0L,
    val durationMs: Long = 1500L,
    val speed: SpeedPreset = SpeedPreset.NORMAL,
    val lutId: String = FilmLutPreset.KODAK_PORTRA.id,
    val lutIntensity: Float = 0.85f,
    val adjustments: ColorAdjustments = ColorAdjustments(),
    val beatFx: BeatFxType = BeatFxType.NONE
)

data class AudioTrackConfig(
    val title: String = "Belum ada lagu",
    val localAudioPath: String? = null,
    val totalDurationMs: Long = 60_000L,
    val spanStartMs: Long = 0L,
    val spanDurationMs: Long = 30_000L,
    val bpmEstimate: Int = 124,
    val beatDensity: BeatDensity = BeatDensity.DYNAMIC,
    val beatTimestampsMs: List<Long> = emptyList(),
    val waveformPeaks: List<Float> = emptyList(),
    val musicVolume: Float = 1.0f,
    val originalClipVolume: Float = 0.15f,
    val fadeInOut: Boolean = true
)

data class ProjectDraft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Cut",
    val updatedAtMs: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatioOption = AspectRatioOption.PORTRAIT_9_16,
    val mediaBin: List<MediaBinItem> = emptyList(),
    val slots: List<TimelineSlot> = emptyList(),
    val audio: AudioTrackConfig = AudioTrackConfig(),
    val textOverlay: TextOverlayConfig = TextOverlayConfig()
) {
    val totalTimelineDurationMs: Long
        get() = slots.sumOf { it.durationMs }

    val hasMissingMedia: Boolean
        get() = mediaBin.any { it.isMissing }
}
