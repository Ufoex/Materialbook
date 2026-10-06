package com.eepiemi.materialbook.utils.jsBridge

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.eepiemi.materialbook.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import org.json.JSONArray
import org.json.JSONObject

class DownloadBridge(private val context: Context) {
    @JavascriptInterface
    @Suppress("unused")
    fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    @Suppress("unused")
    fun downloadUrl(raw: String, mimeType: String) {
        Thread {
            runCatching {
                val candidates = parseCandidateUrls(raw)

                var lastError: IOException? = null
                var saved = false

                // 1) Progressive (muxed) URLs are the ideal target: they
                // already contain image and sound.
                for (url in candidates.progressive) {
                    try {
                        downloadAndSave(url, mimeType, requireVideo = true, requireAudio = true)
                        saved = true
                        break
                    } catch (e: IOException) {
                        lastError = e
                    }
                }

                // 2) Reels on mobile are streamed as separate DASH fragments
                // (a video track and an audio track); no muxed file is fetched.
                // Download both fragments and merge them with a muxer so the
                // saved file has image AND sound.
                if (!saved && candidates.video.isNotEmpty() && candidates.audio.isNotEmpty()) {
                    try {
                        downloadAndMux(
                            candidates.video.first(),
                            candidates.audio.first(),
                            mimeType
                        )
                        saved = true
                    } catch (e: IOException) {
                        lastError = e
                    }
                }

                // 3) Unknown URLs: no classification info, try them as-is.
                for (url in candidates.unknown) {
                    try {
                        downloadAndSave(url, mimeType, requireVideo = true, requireAudio = false)
                        saved = true
                        break
                    } catch (e: IOException) {
                        lastError = e
                    }
                }

                // 4) Fallback: a single video-only fragment (image but no
                // sound) is still better than nothing.
                if (!saved) {
                    for (url in candidates.video) {
                        try {
                            downloadAndSave(url, mimeType, requireVideo = true, requireAudio = false)
                            saved = true
                            break
                        } catch (e: IOException) {
                            lastError = e
                        }
                    }
                }

                if (!saved) {
                    throw lastError ?: IOException("No valid URL")
                }

                val isVideo = mimeType.startsWith("video/")
                val messageRes = if (isVideo) {
                    R.string.saved_to_movies
                } else {
                    R.string.saved_to_downloads
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.getString(messageRes),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.getString(R.string.failed_to_save_file),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    // The JS side sends a JSON object grouping candidate URLs by kind:
    //   { "progressive": [...], "unknown": [...], "video": [...], "audio": [...] }
    // Reels are streamed as separate DASH fragments, so the audio-only ones
    // are kept (they are merged with a video fragment by downloadAndMux).
    // A single URL or a plain JSON array (legacy payloads) are accepted too.
    private fun parseCandidateUrls(raw: String): CandidateUrls {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return CandidateUrls()
        if (trimmed.startsWith("{")) {
            return runCatching {
                val obj = JSONObject(trimmed)
                CandidateUrls(
                    progressive = toList(obj.optJSONArray("progressive")),
                    unknown = toList(obj.optJSONArray("unknown")),
                    video = toList(obj.optJSONArray("video")),
                    audio = toList(obj.optJSONArray("audio"))
                )
            }.getOrDefault(CandidateUrls())
        }
        if (trimmed.startsWith("[")) {
            val urls = runCatching {
                val array = JSONArray(trimmed)
                (0 until array.length()).mapNotNull { i ->
                    array.optString(i).takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
            return CandidateUrls(progressive = urls)
        }
        return CandidateUrls(progressive = listOf(trimmed))
    }

    private data class CandidateUrls(
        val progressive: List<String> = emptyList(),
        val unknown: List<String> = emptyList(),
        val video: List<String> = emptyList(),
        val audio: List<String> = emptyList()
    )

    private fun toList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optString(i).takeIf { it.isNotBlank() }
        }
    }

    // Download a single candidate to a temp file, check its tracks against the
    // requirements, then save it into the media library. Returns after the
    // file has been saved.
    private fun downloadAndSave(
        initialUrl: String,
        mimeType: String,
        requireVideo: Boolean,
        requireAudio: Boolean
    ) {
        val tempFile = downloadToTemp(initialUrl, mimeType)
        try {
            if (requireVideo && !hasVideoTrack(tempFile)) {
                throw IOException("Response is audio only")
            }
            if (requireAudio && !hasAudioTrack(tempFile)) {
                throw IOException("Response has no audio track")
            }

            tempFile.inputStream().use { input ->
                if (mimeType.startsWith("video/")) {
                    saveVideo(input, tempFile.name, "video/mp4")
                } else {
                    saveDownload(input, tempFile.name, mimeType)
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    // Download a video fragment and an audio fragment, then merge them into a
    // single file with both tracks (image AND sound) using a MediaMuxer.
    private fun downloadAndMux(
        videoUrl: String,
        audioUrl: String,
        mimeType: String
    ) {
        val videoFile = downloadToTemp(videoUrl, "video/mp4")
        val audioFile = downloadToTemp(audioUrl, "audio/mp4")
        val outFile = File(context.cacheDir, "${System.currentTimeMillis()}_mux.mp4")
        try {
            if (!hasVideoTrack(videoFile)) throw IOException("Video fragment has no video track")
            if (!hasAudioTrack(audioFile)) throw IOException("Audio fragment has no audio track")
            mux(videoFile, audioFile, outFile)
            val savedName = "${System.currentTimeMillis()}.mp4"
            outFile.inputStream().use { input ->
                saveVideo(input, savedName, "video/mp4")
            }
        } finally {
            videoFile.delete()
            audioFile.delete()
            outFile.delete()
        }
    }

    // Download a URL to a temp file, following redirects and rejecting non-media
    // responses (an expired signed URL is answered with an HTML page).
    private fun downloadToTemp(initialUrl: String, mimeType: String): File {
        if (!initialUrl.startsWith("http://") && !initialUrl.startsWith("https://")) {
            throw IOException("Invalid URL")
        }

        var currentUrl = initialUrl
        var connection: HttpURLConnection? = null

        for (hop in 0 until 5) {
            connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36"
                )
                val cookie = CookieManager.getInstance().getCookie(currentUrl)
                if (!cookie.isNullOrEmpty()) {
                    setRequestProperty("Cookie", cookie)
                }
            }
            connection.connect()

            if (connection.responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrEmpty()) throw IOException("Redirect without location")
                currentUrl = location
                continue
            }

            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }

            val contentType = connection.contentType ?: ""
            if (contentType.startsWith("text/") || contentType.contains("html")) {
                throw IOException("Response is not media content")
            }

            val fileName = "${System.currentTimeMillis()}.${MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"}"
            val tempFile = File(context.cacheDir, fileName)
            try {
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                connection.disconnect()
            }
            return tempFile
        }

        throw IOException("Too many redirects")
    }

    // True when the file contains an actual video track. Reels are streamed
    // as separate DASH fragments: the audio-only fragment must never be saved
    // as a video (it plays with sound but no image).
    private fun hasVideoTrack(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (e: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun hasAudioTrack(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (e: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    // Merge a video fragment and an audio fragment into a single MP4 with both
    // tracks using MediaMuxer. Samples are interleaved in presentation-time
    // order so the result plays correctly.
    private fun mux(videoFile: File, audioFile: File, outFile: File) {
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val videoTrack = findTrack(videoExtractor, wantVideo = true)
            val audioTrack = findTrack(audioExtractor, wantVideo = false)
            if (videoTrack < 0 || audioTrack < 0) {
                throw IOException("No compatible tracks to merge")
            }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            val videoIndex = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val audioIndex = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()

            val videoBuffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val audioBuffer = ByteBuffer.allocate(256 * 1024)
            val videoInfo = MediaCodec.BufferInfo()
            val audioInfo = MediaCodec.BufferInfo()

            var videoHas = readSample(videoExtractor, videoBuffer, videoInfo)
            var audioHas = readSample(audioExtractor, audioBuffer, audioInfo)

            while (videoHas || audioHas) {
                val videoFirst = videoHas && (!audioHas || videoInfo.presentationTimeUs <= audioInfo.presentationTimeUs)
                if (videoFirst) {
                    muxer.writeSampleData(videoIndex, videoBuffer, videoInfo)
                    videoHas = readSample(videoExtractor, videoBuffer, videoInfo)
                } else {
                    muxer.writeSampleData(audioIndex, audioBuffer, audioInfo)
                    audioHas = readSample(audioExtractor, audioBuffer, audioInfo)
                }
            }

            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, wantVideo: Boolean): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            val isVideo = mime.startsWith("video/")
            if (isVideo == wantVideo) return i
        }
        return -1
    }

    private fun readSample(
        extractor: MediaExtractor,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo
    ): Boolean {
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return false
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
        extractor.advance()
        return true
    }

    // Videos are written into the Movies media collection so they show up in
    // the gallery, not just in the file manager.
    private fun saveVideo(input: InputStream, fileName: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Materialbook"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (uri == null) throw IOException("Failed to create file")

            resolver.openOutputStream(uri)?.use { outputStream ->
                input.copyTo(outputStream)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } else {
            val videosDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES + "/Materialbook"
            )
            if (!videosDir.exists()) videosDir.mkdirs()
            val file = File(videosDir, fileName)
            file.outputStream().use { outputStream ->
                input.copyTo(outputStream)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType),
                null
            )
        }
    }

    private fun saveDownload(input: InputStream, fileName: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (uri == null) throw IOException("Failed to create file")

            resolver.openOutputStream(uri)?.use { outputStream ->
                input.copyTo(outputStream)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            File(downloadsDir, fileName).outputStream().use { outputStream ->
                input.copyTo(outputStream)
            }
        }
    }

    @JavascriptInterface
    @Suppress("unused")
    fun downloadBase64File(base64Data: String, mimeType: String) {
        runCatching {
            if (!base64Data.contains(",")) {
                Toast.makeText(
                    context,
                    context.getString(R.string.download_failed_invalid_data),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val data = Base64.decode(base64Data.split(",")[1], Base64.DEFAULT)

            // Determine if it's an image or video
            val isImage = mimeType.startsWith("image/")
            val isVideo = mimeType.startsWith("video/")

            val (finalData, finalMimeType, extension) = when {
                isImage -> {
                    // Convert images to PNG for maximum compatibility
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bitmap != null) {
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        Triple(outputStream.toByteArray(), "image/png", "png")
                    } else {
                        // If bitmap decoding fails, use original data
                        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
                        Triple(data, mimeType, ext)
                    }
                }
                isVideo -> {
                    // Keep videos as-is
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
                    Triple(data, mimeType, ext)
                }
                else -> {
                    // Unknown type, keep as-is
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
                    Triple(data, mimeType, ext)
                }
            }

            val fileName = "${System.currentTimeMillis()}.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
                val relativePath = if (isVideo) {
                    Environment.DIRECTORY_MOVIES + "/Materialbook"
                } else {
                    Environment.DIRECTORY_DOWNLOADS
                }
                val pendingColumn = if (isVideo) {
                    MediaStore.Video.Media.IS_PENDING
                } else {
                    MediaStore.Downloads.IS_PENDING
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, finalMimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                    put(pendingColumn, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(finalData)
                    }
                    contentValues.clear()
                    contentValues.put(pendingColumn, 0)
                    resolver.update(uri, contentValues, null, null)

                    Toast.makeText(
                        context,
                        context.getString(
                            if (isVideo) R.string.saved_to_movies else R.string.saved_to_downloads
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val dirName = if (isVideo) {
                    Environment.DIRECTORY_MOVIES + "/Materialbook"
                } else {
                    Environment.DIRECTORY_DOWNLOADS
                }
                val mediaDir = Environment.getExternalStoragePublicDirectory(dirName)
                if (!mediaDir.exists()) mediaDir.mkdirs()
                val file = File(mediaDir, fileName)

                FileOutputStream(file).use { it.write(finalData) }
                if (isVideo) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf(finalMimeType),
                        null
                    )
                }
                Toast.makeText(
                    context,
                    context.getString(
                        if (isVideo) R.string.saved_to_movies else R.string.saved_to_downloads
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.failed_to_save_file),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}