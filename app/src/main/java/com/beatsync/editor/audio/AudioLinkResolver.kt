package com.beatsync.editor.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.beatsync.editor.model.AudioTrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AudioLinkResolver(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.projectsegfau.lt",
        "https://pipedapi.in.projectsegfau.lt"
    )

    suspend fun resolveFromLocalUri(uri: Uri, displayName: String = "Audio Lokal"): Result<AudioTrackInfo> =
        withContext(Dispatchers.IO) {
            try {
                val duration = extractDurationMs(uri)
                if (duration <= 1000L) {
                    return@withContext Result.failure(IllegalArgumentException("Durasi file audio terlalu pendek atau tidak terbaca."))
                }
                Result.success(
                    AudioTrackInfo(
                        title = displayName,
                        sourceLabel = "File Lokal",
                        localUri = uri,
                        totalDurationMs = duration
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun resolveFromLink(rawUrl: String): Result<AudioTrackInfo> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Masukkan link YouTube atau Spotify terlebih dahulu."))
        }

        try {
            when {
                url.contains("spotify.com", ignoreCase = true) -> {
                    val query = fetchSpotifyTrackQuery(url)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Gagal membaca judul lagu dari link Spotify. Coba gunakan link YouTube atau upload MP3 lokal.")
                        )
                    val videoId = searchPipedVideoId(query)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Lagu '$query' tidak ditemukan di server audio publik. Silakan coba link YouTube langsung atau file MP3.")
                        )
                    downloadPipedAudioStream(videoId, query, "Spotify -> Audio")
                }

                url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) -> {
                    val videoId = extractYouTubeVideoId(url)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Format link YouTube tidak valid.")
                        )
                    downloadPipedAudioStream(videoId, "YouTube Track ($videoId)", "YouTube Link")
                }

                else -> {
                    // Anggap sebagai kata kunci pencarian lagu
                    val videoId = searchPipedVideoId(url)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Tidak dapat menemukan audio untuk '$url'. Coba tempel link YouTube atau pilih file MP3 lokal.")
                        )
                    downloadPipedAudioStream(videoId, url, "Pencarian Lagu")
                }
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Koneksi ekstraksi audio gagal (${e.message ?: "Network Error"}). Gunakan tombol 'Pilih MP3 / Audio Lokal' sebagai alternatif instan.")
            )
        }
    }

    private fun fetchSpotifyTrackQuery(spotifyUrl: String): String? {
        return try {
            val encoded = URLEncoder.encode(spotifyUrl, "UTF-8")
            val request = Request.Builder()
                .url("https://open.spotify.com/oembed?url=$encoded")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val title = json.optString("title").trim()
                if (title.isNotEmpty()) "$title audio" else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val patterns = listOf(
            "(?:v=|/)([0-9A-Za-z_-]{11}).*",
            "youtu\\.be/([0-9A-Za-z_-]{11})",
            "shorts/([0-9A-Za-z_-]{11})"
        )
        for (p in patterns) {
            val matcher = Pattern.compile(p).matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun searchPipedVideoId(query: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        for (baseUrl in pipedInstances) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/search?q=$encoded&filter=music_songs")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val items: JSONArray = json.optJSONArray("items") ?: return@use
                    if (items.length() > 0) {
                        val firstUrl = items.getJSONObject(0).optString("url")
                        val id = firstUrl.substringAfter("v=", "").take(11)
                        if (id.length == 11) return id
                    }
                }
            } catch (_: Exception) {
                // Coba instance berikutnya
            }
        }
        return null
    }

    private fun downloadPipedAudioStream(
        videoId: String,
        fallbackTitle: String,
        sourceLabel: String
    ): Result<AudioTrackInfo> {
        for (baseUrl in pipedInstances) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/streams/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val title = json.optString("title").ifBlank { fallbackTitle }
                    val audioStreams = json.optJSONArray("audioStreams") ?: return@use

                    var bestStreamUrl: String? = null
                    var bestBitrate = -1
                    for (i in 0 until audioStreams.length()) {
                        val stream = audioStreams.getJSONObject(i)
                        val mimeType = stream.optString("mimeType")
                        val bitrate = stream.optInt("bitrate", 0)
                        val streamUrl = stream.optString("url")
                        if (streamUrl.isNotBlank() && (mimeType.contains("mp4") || mimeType.contains("m4a") || bestStreamUrl == null)) {
                            if (bitrate > bestBitrate) {
                                bestBitrate = bitrate
                                bestStreamUrl = streamUrl
                            }
                        }
                    }

                    if (bestStreamUrl != null) {
                        val cacheDir = File(context.cacheDir, "tracks").apply { mkdirs() }
                        val outFile = File(cacheDir, "track_${System.currentTimeMillis()}.m4a")

                        val audioReq = Request.Builder()
                            .url(bestStreamUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()

                        client.newCall(audioReq).execute().use { audioResp ->
                            if (!audioResp.isSuccessful) return@use
                            val bytes = audioResp.body?.bytes() ?: return@use
                            if (bytes.size < 10_000) return@use
                            outFile.writeBytes(bytes)
                        }

                        val uri = Uri.fromFile(outFile)
                        val duration = extractDurationMs(uri)
                        if (duration > 1000L) {
                            return Result.success(
                                AudioTrackInfo(
                                    title = title,
                                    sourceLabel = sourceLabel,
                                    localUri = uri,
                                    totalDurationMs = duration
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Coba mirror instance berikutnya
            }
        }

        return Result.failure(
            IllegalStateException("Server ekstraksi audio sedang sibuk atau dibatasi jaringan. Silakan pilih file MP3/M4A dari memori HP.")
        )
    }

    private fun extractDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }
}
