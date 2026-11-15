package com.beatsync.cut.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

data class ResolvedAudioResult(
    val title: String,
    val localFilePath: String,
    val durationMs: Long
)

class OkHttpNewPipeDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: NpRequest): NpResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (key, values) ->
            values.forEach { value -> reqBuilder.addHeader(key, value) }
        }
        if (headers["User-Agent"].isNullOrEmpty()) {
            reqBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
        }

        if (httpMethod.equals("POST", ignoreCase = true)) {
            val body = (dataToSend ?: ByteArray(0)).toRequestBody(null)
            reqBuilder.post(body)
        } else {
            reqBuilder.get()
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            val responseHeaders = mutableMapOf<String, List<String>>()
            for (name in resp.headers.names()) {
                responseHeaders[name] = resp.headers.values(name)
            }
            return NpResponse(
                resp.code,
                resp.message,
                responseHeaders,
                responseBody,
                resp.request.url.toString()
            )
        }
    }
}

class AudioLinkResolver(private val context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val audioCacheDir = File(context.cacheDir, "resolved_audio").apply { mkdirs() }

    init {
        try {
            NewPipe.init(OkHttpNewPipeDownloader(httpClient))
        } catch (_: Exception) {
        }
    }

    /**
     * Layer 1 & 2: Resolves YouTube URLs directly on-device via NewPipeExtractor,
     * or resolves Spotify track URLs via oEmbed metadata + on-device YouTube search.
     */
    suspend fun resolveFromUrl(
        rawUrl: String,
        onStatus: (String) -> Unit
    ): Result<ResolvedAudioResult> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = rawUrl.trim()
            if (cleanUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Link lagu masih kosong"))
            }

            val targetYoutubeUrl = if (cleanUrl.contains("spotify.com", ignoreCase = true)) {
                onStatus("Mengambil info lagu Spotify...")
                val query = fetchSpotifyTrackTitle(cleanUrl)
                    ?: return@withContext Result.failure(Exception("Gagal membaca metadata link Spotify"))
                onStatus("Mencari audio "$query" di device...")
                searchYoutubeFirstUrl(query)
                    ?: return@withContext Result.failure(Exception("Lagu "$query" tidak ditemukan"))
            } else {
                cleanUrl
            }

            onStatus("Mengekstrak stream audio langsung di HP...")
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetYoutubeUrl)
            val audioStreams = streamInfo.audioStreams
                .filter { !it.content.isNullOrBlank() }
                .sortedByDescending { it.averageBitrate }

            val bestAudio = audioStreams.firstOrNull()
                ?: return@withContext Result.failure(Exception("Stream audio tidak tersedia untuk link ini"))

            val outFile = File(audioCacheDir, "yt_${System.currentTimeMillis()}.m4a")
            onStatus("Mengunduh audio (${bestAudio.averageBitrate} kbps)...")

            val downloadReq = Request.Builder()
                .url(bestAudio.content)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            httpClient.newCall(downloadReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal mengunduh stream (${resp.code})"))
                }
                val body = resp.body ?: return@withContext Result.failure(Exception("Stream kosong"))
                FileOutputStream(outFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }
            }

            val durationMs = if (streamInfo.duration > 0) streamInfo.duration * 1000L else probeDurationMs(outFile.absolutePath)
            Result.success(
                ResolvedAudioResult(
                    title = streamInfo.name ?: "YouTube Audio Track",
                    localFilePath = outFile.absolutePath,
                    durationMs = durationMs.coerceAtLeast(5000L)
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Gagal ambil link: ${e.message ?: "Coba pakai fitur Extract dari Video"}"))
        }
    }

    /**
     * Layer 3A: Extracts audio track from any local video / screen-record (100% offline).
     */
    suspend fun extractAudioFromVideoUri(
        videoUri: Uri,
        displayName: String,
        onStatus: (String) -> Unit
    ): Result<ResolvedAudioResult> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            onStatus("Mengekstrak audio dari video...")
            extractor.setDataSource(context, videoUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                return@withContext Result.failure(Exception("Video ini tidak memiliki trek suara"))
            }

            extractor.selectTrack(audioTrackIndex)
            val outFile = File(audioCacheDir, "extracted_${System.currentTimeMillis()}.m4a")
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val durationMs = probeDurationMs(outFile.absolutePath)
            Result.success(
                ResolvedAudioResult(
                    title = "Audio: $displayName",
                    localFilePath = outFile.absolutePath,
                    durationMs = durationMs.coerceAtLeast(5000L)
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Gagal ekstrak audio: ${e.message}"))
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Layer 3B: Copies a local MP3/M4A/WAV file into app cache for reliable Media3 playback & analysis.
     */
    suspend fun importLocalAudioUri(uri: Uri, displayName: String): Result<ResolvedAudioResult> =
        withContext(Dispatchers.IO) {
            try {
                val ext = displayName.substringAfterLast('.', "m4a")
                val outFile = File(audioCacheDir, "local_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(Exception("Tidak bisa membaca file audio"))

                val durationMs = probeDurationMs(outFile.absolutePath)
                Result.success(
                    ResolvedAudioResult(
                        title = displayName,
                        localFilePath = outFile.absolutePath,
                        durationMs = durationMs.coerceAtLeast(5000L)
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun fetchSpotifyTrackTitle(spotifyUrl: String): String? {
        return try {
            val encoded = URLEncoder.encode(spotifyUrl, "UTF-8")
            val req = Request.Builder()
                .url("https://open.spotify.com/oembed?url=$encoded")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val title = json.optString("title", "")
                val author = json.optString("author_name", "")
                listOf(title, author).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun searchYoutubeFirstUrl(query: String): String? {
        return try {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()
            val items = extractor.initialPage.items
            val firstVideo = items.filterIsInstance<StreamInfoItem>().firstOrNull()
            firstVideo?.url
        } catch (_: Exception) {
            null
        }
    }

    private fun probeDurationMs(path: String): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    return format.getLong(MediaFormat.KEY_DURATION) / 1000L
                }
            }
            30_000L
        } catch (_: Exception) {
            30_000L
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
