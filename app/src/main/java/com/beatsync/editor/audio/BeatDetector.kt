package com.beatsync.editor.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.beatsync.editor.model.BeatAnalysisResult
import com.beatsync.editor.model.BeatDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class BeatDetector(private val context: Context) {

    /**
     * Menganalisis rentang lagu [startMs, endMs] untuk mendeteksi hentakan bass/kick drum
     * dan menghasilkan daftar timestamp potongan video (dalam milidetik relatif terhadap awal klip).
     */
    suspend fun analyzeBeats(
        audioUri: Uri,
        startMs: Long,
        endMs: Long,
        density: BeatDensity
    ): BeatAnalysisResult = withContext(Dispatchers.Default) {
        val spanDurationMs = (endMs - startMs).coerceAtLeast(2000L)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, audioUri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                return@withContext fallbackEvenBeats(spanDurationMs, density)
            }

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            // 1st-order IIR Low-Pass Filter (~140 Hz) untuk mengisolasi frekuensi kick/bass
            val cutoffHz = 140.0
            val rc = 1.0 / (2.0 * PI * cutoffHz)
            val dt = 1.0 / sampleRate
            val alpha = (dt / (rc + dt)).toFloat()
            var lowPassState = 0f

            // Hitung energi RMS per jendela 20 ms
            val samplesPerWindow = (sampleRate * 0.020).toInt().coerceAtLeast(1)
            var currentWindowSumSq = 0.0
            var currentWindowSamples = 0
            val windowEnergies = ArrayList<Float>((spanDurationMs / 20L).toInt() + 16)

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputDone = false
            var isOutputDone = false
            val endUs = endMs * 1000L

            while (!isOutputDone) {
                if (!isInputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = extractor.sampleTime

                        if (sampleSize < 0 || sampleTimeUs > endUs) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                        while (shortBuffer.remaining() >= channelCount) {
                            var monoSample = 0f
                            for (c in 0 until channelCount) {
                                monoSample += shortBuffer.get() / 32768f
                            }
                            monoSample /= channelCount

                            // Terapkan filter bass
                            lowPassState += alpha * (monoSample - lowPassState)
                            currentWindowSumSq += (lowPassState * lowPassState).toDouble()
                            currentWindowSamples++

                            if (currentWindowSamples >= samplesPerWindow) {
                                val rms = sqrt(currentWindowSumSq / currentWindowSamples).toFloat()
                                windowEnergies.add(rms)
                                currentWindowSumSq = 0.0
                                currentWindowSamples = 0
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputDone = true
                    }
                }
            }

            if (windowEnergies.size < 20) {
                return@withContext fallbackEvenBeats(spanDurationMs, density)
            }

            // Adaptive Peak Detection
            val rawBeatTimesMs = mutableListOf<Long>()
            val historyRadius = 22 // ~440 ms radius rata-rata lokal
            val minGapMs = 340L    // maksimal ~176 BPM agar tidak double-trigger

            for (i in windowEnergies.indices) {
                val startIdx = max(0, i - historyRadius)
                val endIdx = min(windowEnergies.lastIndex, i + historyRadius)
                var sum = 0f
                for (j in startIdx..endIdx) {
                    sum += windowEnergies[j]
                }
                val localAvg = sum / (endIdx - startIdx + 1)
                val current = windowEnergies[i]
                val prev = if (i > 0) windowEnergies[i - 1] else 0f
                val next = if (i < windowEnergies.lastIndex) windowEnergies[i + 1] else 0f

                val isLocalPeak = current >= prev && current > next
                val isAboveThreshold = current > localAvg * 1.38f && current > 0.015f

                if (isLocalPeak && isAboveThreshold) {
                    val timeMs = i * 20L
                    if (rawBeatTimesMs.isEmpty() || (timeMs - rawBeatTimesMs.last()) >= minGapMs) {
                        rawBeatTimesMs.add(timeMs)
                    }
                }
            }

            if (rawBeatTimesMs.size < 4) {
                return@withContext fallbackEvenBeats(spanDurationMs, density)
            }

            // Estimasi BPM dari median interval antar beat mentah
            val intervals = rawBeatTimesMs.zipWithNext { a, b -> b - a }.sorted()
            val medianInterval = intervals[intervals.size / 2].coerceIn(300L, 1200L)
            val estimatedBpm = (60_000.0 / medianInterval).roundToInt().coerceIn(65, 175)

            // Terapkan BeatDensity (Chill = tiap 4 beat, Dynamic = tiap 2 beat, Hype = tiap 1 beat)
            val cutPoints = mutableListOf(0L)
            val minClipDurationMs = when (density) {
                BeatDensity.CHILL -> 1600L
                BeatDensity.DYNAMIC -> 850L
                BeatDensity.HYPE -> 450L
            }

            for (idx in rawBeatTimesMs.indices step density.beatStep) {
                val beatMs = rawBeatTimesMs[idx]
                if (beatMs - cutPoints.last() >= minClipDurationMs && (spanDurationMs - beatMs) >= 450L) {
                    cutPoints.add(beatMs)
                }
            }
            cutPoints.add(spanDurationMs)

            // Normalisasi waveform ke 60 bar untuk tampilan UI
            val waveform = downsampleWaveform(windowEnergies, 60)

            BeatAnalysisResult(
                beatTimestampsMs = cutPoints,
                estimatedBpm = estimatedBpm,
                waveformPeaks = waveform
            )
        } catch (_: Exception) {
            fallbackEvenBeats(spanDurationMs, density)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun fallbackEvenBeats(spanDurationMs: Long, density: BeatDensity): BeatAnalysisResult {
        val stepMs = when (density) {
            BeatDensity.CHILL -> 2500L
            BeatDensity.DYNAMIC -> 1300L
            BeatDensity.HYPE -> 750L
        }
        val cuts = mutableListOf(0L)
        var t = stepMs
        while (t < spanDurationMs - 450L) {
            cuts.add(t)
            t += stepMs
        }
        cuts.add(spanDurationMs)

        val dummyWave = List(60) { i ->
            if (i % 4 == 0) 0.9f else if (i % 2 == 0) 0.55f else 0.3f
        }
        return BeatAnalysisResult(
            beatTimestampsMs = cuts,
            estimatedBpm = 120,
            waveformPeaks = dummyWave
        )
    }

    private fun downsampleWaveform(energies: List<Float>, targetBars: Int): List<Float> {
        if (energies.isEmpty()) return List(targetBars) { 0.3f }
        val maxEnergy = energies.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
        val chunkSize = max(1, energies.size / targetBars)
        return List(targetBars) { barIdx ->
            val start = (barIdx * chunkSize).coerceAtMost(energies.lastIndex)
            val end = ((barIdx + 1) * chunkSize).coerceAtMost(energies.size)
            var peak = 0f
            for (i in start until end) {
                if (energies[i] > peak) peak = energies[i]
            }
            (peak / maxEnergy).coerceIn(0.12f, 1.0f)
        }
    }
}
