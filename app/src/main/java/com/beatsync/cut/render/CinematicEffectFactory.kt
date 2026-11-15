package com.beatsync.cut.render

import android.graphics.Matrix
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.beatsync.cut.model.BeatFxType
import com.beatsync.cut.model.ColorAdjustments
import com.beatsync.cut.model.FilmLutPreset
import com.beatsync.cut.model.TextFontStyle
import com.beatsync.cut.model.TextOverlayConfig
import com.google.common.collect.ImmutableList
import kotlin.math.exp
import kotlin.math.sin

@UnstableApi
object CinematicEffectFactory {

    /**
     * Builds an OpenGL RgbMatrix combining the selected 3D-style Film LUT preset,
     * intensity blend, manual Exposure/Contrast/Saturation/Warmth adjustments,
     * and optional White Flash pulse at the start of the beat slot.
     */
    fun createColorAndFlashEffect(
        lutId: String,
        lutIntensity: Float,
        adj: ColorAdjustments,
        beatFx: BeatFxType
    ): RgbMatrix {
        val baseLut = getLutMatrix(lutId)
        val t = lutIntensity.coerceIn(0f, 1f)

        // Blend Identity with LUT preset
        val blended = FloatArray(16) { idx ->
            val identityVal = if (idx % 5 == 0) 1f else 0f
            identityVal * (1f - t) + baseLut[idx] * t
        }

        // Apply manual Exposure, Contrast, Saturation, Warmth
        val expGain = 1f + (adj.exposure * 0.45f)
        val contrastFactor = 1f + (adj.contrast * 0.55f)
        val contrastOffset = 0.5f * (1f - contrastFactor)
        val sat = 1f + (adj.saturation * 0.65f)
        val warmR = 1f + (adj.warmth * 0.16f)
        val warmB = 1f - (adj.warmth * 0.16f)

        val lumR = 0.2126f
        val lumG = 0.7152f
        val lumB = 0.0722f

        val sr = (1f - sat) * lumR
        val sg = (1f - sat) * lumG
        val sb = (1f - sat) * lumB

        val finalMatrix = FloatArray(16)
        // Column-major 4x4 RGBA matrix
        val rScale = expGain * contrastFactor * warmR
        val gScale = expGain * contrastFactor
        val bScale = expGain * contrastFactor * warmB

        finalMatrix[0] = blended[0] * (sr + sat) * rScale
        finalMatrix[1] = blended[1] * sg * gScale
        finalMatrix[2] = blended[2] * sb * bScale
        finalMatrix[3] = 0f

        finalMatrix[4] = blended[4] * sr * rScale
        finalMatrix[5] = blended[5] * (sg + sat) * gScale
        finalMatrix[6] = blended[6] * sb * bScale
        finalMatrix[7] = 0f

        finalMatrix[8] = blended[8] * sr * rScale
        finalMatrix[9] = blended[9] * sg * gScale
        finalMatrix[10] = blended[10] * (sb + sat) * bScale
        finalMatrix[11] = 0f

        finalMatrix[12] = blended[12] + contrastOffset + (adj.shadows * 0.06f)
        finalMatrix[13] = blended[13] + contrastOffset + (adj.shadows * 0.06f)
        finalMatrix[14] = blended[14] + contrastOffset + (adj.shadows * 0.06f)
        finalMatrix[15] = 1f

        return RgbMatrix { presentationTimeUs, _ ->
            if (beatFx == BeatFxType.WHITE_FLASH && presentationTimeUs < 180_000L) {
                val decay = (1f - (presentationTimeUs / 180_000f)).coerceIn(0f, 1f)
                val flashBoost = decay * 0.72f
                finalMatrix.copyOf().apply {
                    this[12] = (this[12] + flashBoost).coerceAtMost(1f)
                    this[13] = (this[13] + flashBoost).coerceAtMost(1f)
                    this[14] = (this[14] + flashBoost).coerceAtMost(1f)
                }
            } else {
                finalMatrix
            }
        }
    }

    /**
     * Builds hardware-accelerated MatrixTransformation for selective Jedag-Jedug FX
     * (Beat Shake Subtle/Hard, Zoom Punch, Whip Zoom) triggered at each beat cut.
     */
    fun createBeatMotionEffect(beatFx: BeatFxType): MatrixTransformation? {
        if (beatFx == BeatFxType.NONE || beatFx == BeatFxType.WHITE_FLASH) return null

        return MatrixTransformation { presentationTimeUs ->
            val matrix = Matrix()
            val sec = presentationTimeUs / 1_000_000.0
            when (beatFx) {
                BeatFxType.ZOOM_PUNCH -> {
                    // Punch in 1.14x at t=0 and settle smoothly back to 1.0x
                    val zoom = 1.0 + 0.14 * exp(-sec * 8.5)
                    matrix.postScale(zoom.toFloat(), zoom.toFloat())
                }
                BeatFxType.SHAKE_SUBTLE -> {
                    val decay = exp(-sec * 9.0)
                    val scale = (1.0 + 0.05 * decay).toFloat()
                    val dx = (sin(sec * 55.0) * 0.025 * decay).toFloat()
                    val dy = (sin(sec * 42.0) * 0.02 * decay).toFloat()
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(dx, dy)
                }
                BeatFxType.SHAKE_HARD -> {
                    val decay = exp(-sec * 7.5)
                    val scale = (1.0 + 0.11 * decay).toFloat()
                    val dx = (sin(sec * 75.0) * 0.055 * decay).toFloat()
                    val dy = (sin(sec * 62.0) * 0.045 * decay).toFloat()
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(dx, dy)
                }
                BeatFxType.WHIP_BLUR -> {
                    val decay = exp(-sec * 10.0)
                    val scale = (1.0 + 0.18 * decay).toFloat()
                    val slideX = (-0.28 * decay).toFloat()
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(slideX, 0f)
                }
                else -> Unit
            }
            matrix
        }
    }

    fun createTextOverlayEffect(config: TextOverlayConfig): Effect? {
        if (!config.enabled || config.text.isBlank()) return null
        val spannable = SpannableString(config.text).apply {
            setSpan(
                ForegroundColorSpan(config.textColorHex.toInt()),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                RelativeSizeSpan(1.45f),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val style = when (config.fontStyle) {
                TextFontStyle.SANS_BOLD -> Typeface.BOLD
                TextFontStyle.CINEMA_SERIF -> Typeface.ITALIC
                TextFontStyle.MONO_TAG -> Typeface.BOLD
            }
            setSpan(StyleSpan(style), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (config.hasPillBackground) {
                setSpan(
                    BackgroundColorSpan(0xB80057B8.toInt()),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(
                (config.offsetX * 1.8f).coerceIn(-0.85f, 0.85f),
                (-config.offsetY * 1.8f).coerceIn(-0.85f, 0.85f)
            )
            .build()
        val overlay: TextureOverlay = TextOverlay.createStaticTextOverlay(spannable, settings)
        return OverlayEffect(ImmutableList.of(overlay))
    }

    private fun getLutMatrix(lutId: String): FloatArray {
        return when (lutId) {
            FilmLutPreset.KODAK_PORTRA.id -> floatArrayOf(
                1.08f, 0.02f, -0.02f, 0f,
                0.02f, 1.03f, 0.00f, 0f,
                -0.02f, 0.01f, 0.92f, 0f,
                0.02f, 0.015f, 0.01f, 1f
            )
            FilmLutPreset.CINESTILL_800T.id -> floatArrayOf(
                0.92f, 0.00f, 0.04f, 0f,
                0.01f, 1.02f, 0.05f, 0f,
                0.03f, 0.06f, 1.16f, 0f,
                0.00f, 0.01f, 0.03f, 1f
            )
            FilmLutPreset.TEAL_ORANGE.id -> floatArrayOf(
                1.14f, -0.04f, -0.06f, 0f,
                0.00f, 1.01f, 0.03f, 0f,
                -0.05f, 0.06f, 1.12f, 0f,
                0.01f, 0.00f, 0.02f, 1f
            )
            FilmLutPreset.BLEACH_BYPASS.id -> floatArrayOf(
                0.88f, 0.22f, 0.14f, 0f,
                0.18f, 0.90f, 0.14f, 0f,
                0.16f, 0.20f, 0.86f, 0f,
                -0.05f, -0.05f, -0.05f, 1f
            )
            FilmLutPreset.FUJI_SUPERIA.id -> floatArrayOf(
                0.98f, 0.02f, 0.00f, 0f,
                0.01f, 1.08f, 0.02f, 0f,
                0.00f, 0.03f, 1.04f, 0f,
                0.01f, 0.02f, 0.015f, 1f
            )
            FilmLutPreset.GOLDEN_HOUR.id -> floatArrayOf(
                1.18f, 0.04f, -0.04f, 0f,
                0.03f, 1.05f, -0.02f, 0f,
                -0.04f, -0.02f, 0.82f, 0f,
                0.03f, 0.015f, 0.00f, 1f
            )
            FilmLutPreset.LATTE_MATTE.id -> floatArrayOf(
                1.04f, 0.03f, 0.02f, 0f,
                0.02f, 1.01f, 0.02f, 0f,
                0.00f, 0.02f, 0.93f, 0f,
                0.045f, 0.04f, 0.035f, 1f
            )
            FilmLutPreset.GOTHAM_NOIR.id -> floatArrayOf(
                0.86f, 0.04f, 0.04f, 0f,
                0.04f, 0.88f, 0.05f, 0f,
                0.05f, 0.06f, 0.98f, 0f,
                -0.04f, -0.04f, -0.02f, 1f
            )
            FilmLutPreset.SUPER_8.id -> floatArrayOf(
                1.15f, 0.06f, -0.03f, 0f,
                0.04f, 1.02f, -0.02f, 0f,
                -0.05f, 0.00f, 0.78f, 0f,
                0.035f, 0.025f, 0.01f, 1f
            )
            FilmLutPreset.VHS_90S.id -> floatArrayOf(
                1.06f, 0.02f, 0.06f, 0f,
                0.02f, 0.96f, 0.04f, 0f,
                0.06f, 0.04f, 1.12f, 0f,
                0.03f, 0.02f, 0.04f, 1f
            )
            FilmLutPreset.LEICA_MONO.id -> floatArrayOf(
                0.30f, 0.30f, 0.30f, 0f,
                0.59f, 0.59f, 0.59f, 0f,
                0.11f, 0.11f, 0.11f, 0f,
                -0.02f, -0.02f, -0.02f, 1f
            )
            else -> floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
        }
    }
}