package com.beatsync.cut.render

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.beatsync.cut.model.AspectRatioOption
import com.beatsync.cut.model.ProjectDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume

enum class ExportResolution(val label: String, val is4K: Boolean) {
    RES_720P("720p HD", false),
    RES_1080P("1080p Full HD", false),
    RES_4K("4K Ultra HD (HEVC)", true)
}

enum class ExportFps(val label: String, val fps: Int) {
    FPS_30("30 fps", 30),
    FPS_60("60 fps", 60)
}

data class ExportOutcome(
    val success: Boolean,
    val savedDisplayPath: String = "",
    val outputUri: Uri? = null,
    val errorMessage: String = ""
)

@UnstableApi
object BeatVideoExporter {

    fun isDevice4KSupported(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { type ->
                    if (type.equals(MimeTypes.VIDEO_H265, true) || type.equals(MimeTypes.VIDEO_H264, true)) {
                        val caps = info.getCapabilitiesForType(type)?.videoCapabilities
                        caps?.isSizeSupported(3840, 2160) == true || caps?.isSizeSupported(2160, 3840) == true
                    } else false
                }
            }
        } catch (_: Exception) {
            true
        }
    }

    suspend fun exportProject(
        context: Context,
        draft: ProjectDraft,
        resolution: ExportResolution,
        fps: ExportFps,
        onProgress: (Int) -> Unit
    ): ExportOutcome = withContext(Dispatchers.Main) {
        if (draft.slots.isEmpty()) {
            return@withContext ExportOutcome(false, errorMessage = "Timeline masih kosong, tambahkan minimal 1 klip.")
        }

        val effectiveRes = if (resolution == ExportResolution.RES_4K && !isDevice4KSupported()) {
            ExportResolution.RES_1080P
        } else {
            resolution
        }

        val (targetW, targetH) = when (effectiveRes) {
            ExportResolution.RES_4K -> draft.aspectRatio.width4K to draft.aspectRatio.height4K
            ExportResolution.RES_1080P -> draft.aspectRatio.width1080 to draft.aspectRatio.height1080
            ExportResolution.RES_720P -> if (draft.aspectRatio == AspectRatioOption.PORTRAIT_9_16) 720 to 1280 else 1280 to 720
        }

        val mediaMap = draft.mediaBin.associateBy { it.id }
        val videoSequenceItems = mutableListOf<EditedMediaItem>()

        for (slot in draft.slots) {
            val sourceMedia = mediaMap[slot.mediaBinId] ?: continue
            if (sourceMedia.isMissing) continue

            val sourceStartMs = slot.inPointMs.coerceIn(0L, (sourceMedia.totalDurationMs - 200L).coerceAtLeast(0L))
            val rawNeededMs = (slot.durationMs * slot.speed.multiplier).toLong().coerceAtLeast(250L)
            val sourceEndMs = (sourceStartMs + rawNeededMs).coerceAtMost(sourceMedia.totalDurationMs)

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(sourceMedia.uriString))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(sourceStartMs)
                        .setEndPositionMs(sourceEndMs)
                        .build()
                )
                .build()

            val videoEffects = mutableListOf<Effect>()
            videoEffects.add(
                Presentation.createForWidthAndHeight(
                    targetW,
                    targetH,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            )
            if (slot.speed.multiplier != 1.0f) {
                videoEffects.add(SpeedChangeEffect(slot.speed.multiplier))
            }
            videoEffects.add(
                CinematicEffectFactory.createColorAndFlashEffect(
                    lutId = slot.lutId,
                    lutIntensity = slot.lutIntensity,
                    adj = slot.adjustments,
                    beatFx = slot.beatFx
                )
            )
            CinematicEffectFactory.createBeatMotionEffect(slot.beatFx)?.let {
                videoEffects.add(it)
            }
            CinematicEffectFactory.createTextOverlayEffect(draft.textOverlay)?.let {
                videoEffects.add(it)
            }

            val clipVol = draft.audio.originalClipVolume.coerceIn(0f, 1f)
            val audioProcessors = mutableListOf<AudioProcessor>()
            if (clipVol > 0.01f) {
                audioProcessors.add(createVolumeProcessor(clipVol))
            }

            val editedItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(clipVol <= 0.01f)
                .setEffects(Effects(audioProcessors, videoEffects))
                .build()

            videoSequenceItems.add(editedItem)
        }

        if (videoSequenceItems.isEmpty()) {
            return@withContext ExportOutcome(false, errorMessage = "Tidak ada klip valid untuk di-export.")
        }

        val sequences = mutableListOf(EditedMediaItemSequence(videoSequenceItems))

        // Add Music Track Sequence if audio is loaded
        val audioPath = draft.audio.localAudioPath
        if (!audioPath.isNullOrBlank() && File(audioPath).exists() && draft.audio.musicVolume > 0.01f) {
            val totalVideoMs = draft.totalTimelineDurationMs.coerceAtLeast(1000L)
            val musicStartMs = draft.audio.spanStartMs
            val musicEndMs = (musicStartMs + totalVideoMs).coerceAtMost(draft.audio.totalDurationMs)

            val musicItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(audioPath)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(musicStartMs)
                        .setEndPositionMs(musicEndMs)
                        .build()
                )
                .build()

            val musicProcessor = createVolumeProcessor(draft.audio.musicVolume.coerceIn(0f, 1.5f))
            val editedMusic = EditedMediaItem.Builder(musicItem)
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(musicProcessor), emptyList()))
                .build()

            sequences.add(EditedMediaItemSequence(listOf(editedMusic)))
        }

        val composition = Composition.Builder(sequences).build()
        val tempOutFile = File(context.cacheDir, "BeatSync_Export_${System.currentTimeMillis()}.mp4")
        if (tempOutFile.exists()) tempOutFile.delete()

        val videoMime = if (effectiveRes == ExportResolution.RES_4K) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264

        val outcome = suspendCancellableCoroutine<ExportOutcome> { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMime)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        val savedUri = saveToMediaStore(context, tempOutFile)
                        tempOutFile.delete()
                        if (continuation.isActive) {
                            continuation.resume(
                                ExportOutcome(
                                    success = true,
                                    savedDisplayPath = "Movies/BeatSync/${savedUri.second}",
                                    outputUri = savedUri.first
                                )
                            )
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        tempOutFile.delete()
                        if (continuation.isActive) {
                            continuation.resume(
                                ExportOutcome(
                                    success = false,
                                    errorMessage = exportException.message ?: "Gagal melakukan rendering video"
                                )
                            )
                        }
                    }
                })
                .build()

            transformer.start(composition, tempOutFile.absolutePath)

            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 99))
                    }
                    handler.postDelayed(this, 200L)
                }
            }
            handler.post(pollRunnable)

            continuation.invokeOnCancellation {
                handler.removeCallbacks(pollRunnable)
                transformer.cancel()
                tempOutFile.delete()
            }
        }

        outcome
    }

    private fun createVolumeProcessor(gain: Float): ChannelMixingAudioProcessor {
        return ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(
                ChannelMixingMatrix(1, 1, floatArrayOf(gain))
            )
            putChannelMixingMatrix(
                ChannelMixingMatrix(2, 2, floatArrayOf(gain, 0f, 0f, gain))
            )
        }
    }

    private fun saveToMediaStore(context: Context, sourceFile: File): Pair<Uri?, String> {
        val fileName = "BeatSync_${System.currentTimeMillis()}.mp4"
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BeatSync")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            uri to fileName
        } catch (_: Exception) {
            null to fileName
        }
    }
}
