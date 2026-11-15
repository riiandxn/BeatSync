package com.beatsync.cut.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.beatsync.cut.model.BeatDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BeatAnalysisResult(
    val beatTimestampsMs: List<Long>,
    val waveformPeaks: List<Float>,
    val estimatedBpm: Int
)

object BeatDetector {

    /**
     * Decodes the selected [spanStartMs .. spanStartMs + spanDurationMs] window into PCM,
     * extracts 72 visual waveform peaks, and detects onset beat cut timestamps.
     */
    suspend fun analyzeSpan(
        audioFilePath: String?,
        spanStartMs: Long,
        spanDurationMs: Long,
        density: BeatDensity
    ): BeatAnalysisResult = withContext(Dispatchers.Default) {
        if (audioFilePath.isNullOrBlank()) {
            return@withContext generateFallbackBeats(spanDurationMs, density)
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(audioFilePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = fmt
                    break
                }
            }
            if (audioTrack < 0 || format == null) {
                return@withContext generateFallbackBeats(spanDurationMs, density)
            }

            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            extractor.seekTo(spanStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val endUs = (spanStartMs + spanDurationMs) * 1000L

            // Bucket RMS energies in 25ms windows
            val windowMs = 25L
            val numWindows = (spanDurationMs / windowMs).toInt().coerceAtLeast(16)
            val windowEnergy = DoubleArray(numWindows)
            val windowSamples = IntArray(numWindows)

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || sampleTimeUs > endUs) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val relMs = ((bufferInfo.presentationTimeUs / 1000L) - spanStartMs).coerceAtLeast(0L)
                        val winIdx = (relMs / windowMs).toInt()
                        if (winIdx in 0 until numWindows) {
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var sumSq = 0.0
                            var count = 0
                            while (shortBuf.hasRemaining()) {
                                val s = shortBuf.get() / 32768.0
                                sumSq += s * s
                                count++
                            }
                            if (count > 0) {
                                windowEnergy[winIdx] += sumSq
                                windowSamples[winIdx] += count / max(1, channelCount)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            val rms = DoubleArray(numWindows) { idx ->
                if (windowSamples[idx] > 0) sqrt(windowEnergy[idx] / windowSamples[idx]) else 0.0
            }

            // Build 72 normalized waveform bars
            val barCount = 72
            val maxRms = rms.maxOrNull()?.coerceAtLeast(0.001) ?: 0.001
            val peaks = List(barCount) { b ->
                val startIdx = (b * numWindows) / barCount
                val endIdx = (((b + 1) * numWindows) / barCount).coerceAtLeast(startIdx + 1).coerceAtMost(numWindows)
                var sliceMax = 0.0
                for (k in startIdx until endIdx) {
                    if (rms[k] > sliceMax) sliceMax = rms[k]
                }
                (sliceMax / maxRms).toFloat().coerceIn(0.12f, 1.0f)
            }

            // Detect spectral/energy flux peaks respecting density minIntervalMs
            val beats = mutableListOf(0L)
            var lastBeatMs = 0L
            for (i in 2 until numWindows - 1) {
                val currMs = i * windowMs
                if (currMs - lastBeatMs < density.minIntervalMs) continue
                if (spanDurationMs - currMs < 280L) break

                val localAvg = (rms[i - 2] + rms[i - 1] + rms[i] + rms[i + 1]) / 4.0
                val flux = rms[i] - rms[i - 1]
                val isPeak = rms[i] >= rms[i - 1] && rms[i] >= rms[i + 1]
                if (isPeak && rms[i] > localAvg * 1.12 && flux > maxRms * 0.04) {
                    beats.add(currMs)
                    lastBeatMs = currMs
                }
            }

            // Ensure minimum rhythmic cuts if track is ambient
            if (beats.size < 4) {
                return@withContext generateFallbackBeats(spanDurationMs, density, peaks)
            }

            val avgStepMs = (spanDurationMs.toDouble() / beats.size).coerceAtLeast(250.0)
            val estBpm = (60_000.0 / avgStepMs).roundToInt().coerceIn(72, 174)
            BeatAnalysisResult(beats, peaks, estBpm)
        } catch (_: Exception) {
            generateFallbackBeats(spanDurationMs, density)
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun generateFallbackBeats(
        spanDurationMs: Long,
        density: BeatDensity,
        existingPeaks: List<Float>? = null
    ): BeatAnalysisResult {
        val step = density.minIntervalMs
        val beats = mutableListOf<Long>()
        var t = 0L
        while (t < spanDurationMs - 250L) {
            beats.add(t)
            t += step
        }
        if (beats.isEmpty()) beats.add(0L)
        val peaks = existingPeaks ?: List(72) { i ->
            val pulse = if (i % 4 == 0) 0.92f else if (i % 2 == 0) 0.64f else 0.35f
            (pulse * (0.75f + 0.25f * abs(kotlin.math.sin(i * 0.45)).toFloat())).coerceIn(0.15f, 1.0f)
        }
        val bpm = (60_000.0 / step).roundToInt().coerceIn(80, 160)
        return BeatAnalysisResult(beats, peaks, bpm)
    }
}
