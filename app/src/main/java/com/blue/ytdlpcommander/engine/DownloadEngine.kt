package com.blue.ytdlpcommander.engine

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class DownloadResult(
    val success: Boolean,
    val outputFilePath: String?,
    val title: String?,
    val errorMessage: String?
)

data class FormatInfo(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val note: String,
    val approxSizeMb: Double?
)

data class AnalyzeResult(
    val success: Boolean,
    val title: String?,
    val durationSeconds: Long?,
    val uploader: String?,
    val formats: List<FormatInfo>,
    val errorMessage: String?
)

sealed class UpdateStatus {
    data class UpToDate(val version: String?) : UpdateStatus()
    data class Updated(val version: String?) : UpdateStatus()
    data class Failed(val message: String) : UpdateStatus()
}

object DownloadEngine {

    /**
     * Runs one download to completion (blocking under the hood - call from a
     * background coroutine). [onProgress] is invoked repeatedly with
     * (percent 0-100, etaSeconds or -1 if unknown).
     *
     * Uses yt-dlp's `--print` hooks to reliably learn the title and the
     * final output path (the latter via `after_move:filepath`, which
     * reflects any extension change from post-processing) rather than
     * trying to regex-parse progress lines.
     */
    suspend fun runDownload(
        processId: String,
        url: String,
        outputDir: File,
        mode: DownloadMode,
        quality: QualityPreset,
        audioBitrate: AudioBitrate,
        formatOverride: String?,
        speedLimitKbps: Int,
        customFlags: String,
        cookiesFile: File?,
        onProgress: (percent: Float, etaSeconds: Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!outputDir.exists()) outputDir.mkdirs()

        val request = YoutubeDLRequest(url)
        request.addOption("-o", "${outputDir.absolutePath}/%(title).150B [%(id)s].%(ext)s")
        request.addOption("--no-playlist")
        request.addOption("--print", "%(title)s")
        request.addOption("--print", "after_move:filepath")

        applyCommonOptions(request, speedLimitKbps, customFlags, cookiesFile)

        when {
            formatOverride != null -> request.addOption("-f", formatOverride)
            mode == DownloadMode.AUDIO -> {
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", audioBitrate.kbps + "K")
            }
            mode == DownloadMode.SUBTITLES -> {
                request.addOption("--skip-download")
                request.addOption("--write-subs")
                request.addOption("--write-auto-subs")
                request.addOption("--sub-langs", "all")
                request.addOption("--convert-subs", "srt")
            }
            else -> request.addOption("-f", quality.formatArg)
        }

        try {
            val response = YoutubeDL.getInstance().execute(request, processId) { percent, etaInSeconds, _ ->
                onProgress(percent, etaInSeconds)
            }

            val printedLines = response.out
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("[") && !it.startsWith("WARNING") }
                .toList()

            val title = printedLines.firstOrNull()
            val filePath = printedLines.lastOrNull()?.takeIf { File(it).exists() }
                ?: findNewestFileFallback(outputDir)

            DownloadResult(
                success = filePath != null,
                outputFilePath = filePath,
                title = title,
                errorMessage = if (filePath == null) "Couldn't determine the output file - see log" else null
            )
        } catch (e: Exception) {
            DownloadResult(success = false, outputFilePath = null, title = null, errorMessage = e.message ?: "Download failed")
        }
    }

    /**
     * "Analyze" / "Formats" mode: fetches metadata and the list of available
     * formats without downloading anything, via yt-dlp's `--dump-json`.
     */
    suspend fun analyze(
        url: String,
        customFlags: String,
        cookiesFile: File?
    ): AnalyzeResult = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--skip-download")
        request.addOption("--dump-json")
        applyCommonOptions(request, speedLimitKbps = 0, customFlags = customFlags, cookiesFile = cookiesFile)

        try {
            val response = YoutubeDL.getInstance().execute(request, "analyze-${System.currentTimeMillis()}") { _, _, _ -> }
            // --dump-json prints one JSON object per line (per video); we only
            // handle single-video analysis here, so take the first line.
            val jsonLine = response.out.lineSequence().firstOrNull { it.trim().startsWith("{") }
                ?: return@withContext AnalyzeResult(false, null, null, null, emptyList(), "No metadata returned")

            val json = JSONObject(jsonLine)
            val title = json.optString("title", null)
            val duration = if (json.has("duration")) json.optDouble("duration").toLong() else null
            val uploader = json.optString("uploader", null)

            val formatsArray = json.optJSONArray("formats")
            val formats = mutableListOf<FormatInfo>()
            if (formatsArray != null) {
                for (i in 0 until formatsArray.length()) {
                    val f = formatsArray.getJSONObject(i)
                    val height = f.optInt("height", -1)
                    val resolution = when {
                        height > 0 -> "${height}p"
                        f.optString("vcodec", "none") == "none" -> "audio"
                        else -> f.optString("resolution", "?")
                    }
                    val sizeBytes = if (f.has("filesize")) f.optDouble("filesize")
                        else if (f.has("filesize_approx")) f.optDouble("filesize_approx") else null
                    formats.add(
                        FormatInfo(
                            formatId = f.optString("format_id", ""),
                            ext = f.optString("ext", ""),
                            resolution = resolution,
                            note = f.optString("format_note", ""),
                            approxSizeMb = sizeBytes?.let { it / (1024 * 1024) }
                        )
                    )
                }
            }

            AnalyzeResult(true, title, duration, uploader, formats, null)
        } catch (e: Exception) {
            AnalyzeResult(false, null, null, null, emptyList(), e.message ?: "Analysis failed")
        }
    }

    /**
     * Self-updates the bundled yt-dlp to the latest release. This is a real
     * feature of the youtubedl-android library (it re-downloads the yt-dlp
     * Python package from its GitHub releases), not something built here
     * from scratch.
     */
    suspend fun updateYoutubeDl(context: android.content.Context): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            when (status?.name) {
                "ALREADY_UP_TO_DATE" -> UpdateStatus.UpToDate(null)
                "DONE" -> UpdateStatus.Updated(null)
                else -> UpdateStatus.UpToDate(status?.name)
            }
        } catch (e: Exception) {
            UpdateStatus.Failed(e.message ?: "Update failed")
        }
    }

    fun cancel(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
        }
    }

    private fun applyCommonOptions(request: YoutubeDLRequest, speedLimitKbps: Int, customFlags: String, cookiesFile: File?) {
        if (speedLimitKbps > 0) {
            request.addOption("--limit-rate", "${speedLimitKbps}K")
        }
        if (cookiesFile != null && cookiesFile.exists()) {
            request.addOption("--cookies", cookiesFile.absolutePath)
        }
        // Naive whitespace split - good enough for simple flags like
        // "--proxy socks5://host:port"; flags needing quoted spaces inside a
        // single argument aren't supported by this simple parser.
        customFlags.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.forEach { token ->
            request.addOption(token)
        }
    }

    /** Fallback if --print somehow didn't yield a usable path: pick the most recently modified file in the output dir. */
    private fun findNewestFileFallback(outputDir: File): String? =
        outputDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }?.absolutePath
}
