package com.beatsync.editor.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.beatsync.editor.model.AspectRatioOption
import com.beatsync.editor.model.AudioTrackInfo
import com.beatsync.editor.model.VideoClipItem
import com.beatsync.editor.model.VideoFilterPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@UnstableApi
class BeatVideoExporter(private val context: Context) {

    suspend fun exportBeatSyncedVideo(
        clips: List<VideoClipItem>,
        audioTrack: AudioTrackInfo,
        audioStartMs: Long,
        audioEndMs: Long,
        beatCutPointsMs: List<Long>,
        aspectRatio: AspectRatioOption,
        filterPreset: VideoFilterPreset,
        filterIntensity: Float,
        enableBeatPunch: Boolean,
        onProgress: (Int, String) -> Unit
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.Main) {
        if (clips.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Pilih minimal 1 video terlebih dahulu."))
        }
        if (beatCutPointsMs.size < 2) {
            return@withContext Result.failure(IllegalArgumentException("Titik beat lagu belum siap."))
        }

        val tempDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "BeatSync_${System.currentTimeMillis()}.mp4"
        val tempOutputFile = File(tempDir, fileName)
        if (tempOutputFile.exists()) tempOutputFile.delete()

        // Lacak posisi terakhir tiap klip agar saat klip dipakai ulang, aplikasi mengambil bagian detik berikutnya
        val clipCursorMs = LongArray(clips.size) { 0L }
        val videoEditedItems = mutableListOf<EditedMediaItem>()

        for (segmentIdx in 0 until beatCutPointsMs.lastIndex) {
            val segStart = beatCutPointsMs[segmentIdx]
            val segEnd = beatCutPointsMs[segmentIdx + 1]
            val neededDurationMs = (segEnd - segStart).coerceAtLeast(300L)

            val clipIndex = segmentIdx % clips.size
            val clip = clips[clipIndex]
            val safeClipDuration = clip.durationMs.coerceAtLeast(500L)

            val sliceDuration = neededDurationMs.coerceAtMost(safeClipDuration)
            var sourceStart = clipCursorMs[clipIndex]
            if (sourceStart + sliceDuration > safeClipDuration) {
                sourceStart = 0L
            }
            val sourceEnd = (sourceStart + sliceDuration).coerceAtMost(safeClipDuration)
            clipCursorMs[clipIndex] = if (sourceEnd >= safeClipDuration - 200L) 0L else sourceEnd

            val clippedMediaItem = MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(sourceStart)
                        .setEndPositionMs(sourceEnd)
                        .build()
                )
                .build()

            val effectsList = buildEffectsForSegment(
                aspectRatio = aspectRatio,
                filterPreset = filterPreset,
                intensity = filterIntensity,
                applyBeatPunch = enableBeatPunch && (segmentIdx % 2 == 1)
            )

            val editedVideoItem = EditedMediaItem.Builder(clippedMediaItem)
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), effectsList))
                .build()

            videoEditedItems.add(editedVideoItem)
        }

        // Susun Sequence Audio sesuai span yang dipilih user
        val clippedAudioItem = MediaItem.Builder()
            .setUri(audioTrack.localUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(audioStartMs)
                    .setEndPositionMs(audioEndMs)
                    .build()
            )
            .build()

        val editedAudioItem = EditedMediaItem.Builder(clippedAudioItem)
            .setRemoveVideo(true)
            .build()

        val videoSequence = EditedMediaItemSequence(videoEditedItems)
        val audioSequence = EditedMediaItemSequence(listOf(editedAudioItem))

        val composition = Composition.Builder(listOf(videoSequence, audioSequence))
            .build()

        var isFinished = false
        var exportError: Throwable? = null

        val transformer = Transformer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    isFinished = true
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    exportError = exportException
                    isFinished = true
                }
            })
            .build()

        onProgress(5, "Menyiapkan timeline ${videoEditedItems.size} potongan beat...")
        transformer.start(composition, tempOutputFile.absolutePath)

        val progressHolder = ProgressHolder()
        while (!isFinished) {
            val state = transformer.getProgress(progressHolder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                val pct = progressHolder.progress.coerceIn(5, 95)
                onProgress(pct, "Merender video 1080p ($pct%)...")
            }
            delay(250L)
        }

        if (exportError != null || !tempOutputFile.exists() || tempOutputFile.length() == 0L) {
            return@withContext Result.failure(
                exportError ?: IllegalStateException("Gagal merender file video.")
            )
        }

        onProgress(98, "Menyimpan ke Galeri HP...")
        val savedUri = withContext(Dispatchers.IO) {
            saveVideoToGallery(tempOutputFile, fileName)
        }

        onProgress(100, "Selesai!")
        Result.success(savedUri to fileName)
    }

    private fun buildEffectsForSegment(
        aspectRatio: AspectRatioOption,
        filterPreset: VideoFilterPreset,
        intensity: Float,
        applyBeatPunch: Boolean
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        val t = intensity.coerceIn(0f, 1f)

        // 1. Auto-crop & scale ke resolusi target (9:16 atau 16:9)
        effects.add(
            Presentation.createForWidthAndHeight(
                aspectRatio.width,
                aspectRatio.height,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        // 2. Beat Punch zoom mikro (1.06x) pada hentakan bergantian
        if (applyBeatPunch) {
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(1.06f, 1.06f)
                    .build()
            )
        }

        // 3. Color Grading Filter Preset
        if (t > 0.02f && filterPreset != VideoFilterPreset.ORIGINAL) {
            when (filterPreset) {
                VideoFilterPreset.ORIGINAL -> Unit

                VideoFilterPreset.TEAL_ORANGE -> {
                    effects.add(
                        RgbAdjustment.Builder()
                            .setRedScale(1f + 0.14f * t)
                            .setGreenScale(1f + 0.02f * t)
                            .setBlueScale(1f + 0.12f * t)
                            .build()
                    )
                    effects.add(
                        HslAdjustment.Builder()
                            .adjustSaturation(18f * t)
                            .build()
                    )
                    effects.add(Contrast(0.18f * t))
                }

                VideoFilterPreset.WARM_VINTAGE -> {
                    effects.add(
                        RgbAdjustment.Builder()
                            .setRedScale(1f + 0.18f * t)
                            .setGreenScale(1f + 0.07f * t)
                            .setBlueScale(1f - 0.16f * t)
                            .build()
                    )
                    effects.add(
                        HslAdjustment.Builder()
                            .adjustSaturation(-12f * t)
                            .adjustLightness(4f * t)
                            .build()
                    )
                }

                VideoFilterPreset.MOODY_DARK -> {
                    effects.add(
                        RgbAdjustment.Builder()
                            .setRedScale(1f - 0.06f * t)
                            .setGreenScale(1f - 0.02f * t)
                            .setBlueScale(1f + 0.14f * t)
                            .build()
                    )
                    effects.add(
                        HslAdjustment.Builder()
                            .adjustSaturation(-20f * t)
                            .adjustLightness(-10f * t)
                            .build()
                    )
                    effects.add(Contrast(0.25f * t))
                }

                VideoFilterPreset.VIVID_POP -> {
                    effects.add(
                        HslAdjustment.Builder()
                            .adjustSaturation(38f * t)
                            .adjustLightness(3f * t)
                            .build()
                    )
                    effects.add(Contrast(0.15f * t))
                }

                VideoFilterPreset.BW_CONTRAST -> {
                    effects.add(
                        HslAdjustment.Builder()
                            .adjustSaturation(-100f * t)
                            .build()
                    )
                    effects.add(Contrast(0.32f * t))
                }
            }
        }

        return effects
    }

    private fun saveVideoToGallery(sourceFile: File, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BeatSync")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values)
            ?: return Uri.fromFile(sourceFile)

        resolver.openOutputStream(itemUri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }

        sourceFile.delete()
        return itemUri
    }
}
