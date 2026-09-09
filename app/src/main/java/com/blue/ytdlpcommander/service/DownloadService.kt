package com.blue.ytdlpcommander.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.blue.ytdlpcommander.App
import com.blue.ytdlpcommander.MainActivity
import com.blue.ytdlpcommander.R
import com.blue.ytdlpcommander.engine.ActivityLog
import com.blue.ytdlpcommander.engine.DownloadEngine
import com.blue.ytdlpcommander.engine.DownloadHistoryRepository
import com.blue.ytdlpcommander.engine.DownloadJob
import com.blue.ytdlpcommander.engine.DownloadMode
import com.blue.ytdlpcommander.engine.DownloadState
import com.blue.ytdlpcommander.engine.DownloadStatus
import com.blue.ytdlpcommander.engine.HistoryEntry
import com.blue.ytdlpcommander.engine.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var semaphore = Semaphore(2)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: DownloadMode.VIDEO.name
                val mode = try { DownloadMode.valueOf(modeName) } catch (_: Exception) { DownloadMode.VIDEO }
                val formatOverride = intent.getStringExtra(EXTRA_FORMAT_OVERRIDE)
                enqueue(url, mode, formatOverride)
            }
            ACTION_CANCEL -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return START_NOT_STICKY
                cancelJob(jobId)
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueue(url: String, mode: DownloadMode, formatOverride: String?) {
        semaphore = Semaphore(Prefs.maxParallel(this).coerceIn(1, 5))

        val job = DownloadJob(url = url, mode = mode, formatOverride = formatOverride, status = DownloadStatus.QUEUED)
        DownloadState.enqueue(job)
        ensureForeground()
        acquireWakeLockIfNeeded()
        ActivityLog.add("${mode.emoji} Queued: $url")

        serviceScope.launch {
            semaphore.withPermit {
                runJob(job)
            }
            maybeStopService()
        }
    }

    private suspend fun runJob(initialJob: DownloadJob) {
        DownloadState.update(initialJob.id) { it.copy(status = DownloadStatus.FETCHING_INFO) }
        postJobNotification(DownloadState.jobs.value.first { it.id == initialJob.id })

        // yt-dlp always writes here first; a custom SAF save folder (if set)
        // gets a copy afterwards, since yt-dlp itself can't write directly
        // into an arbitrary SAF tree.
        val stagingDir = File(cacheDir, "staging").also { it.mkdirs() }
        val cookiesFile = Prefs.cookiesFile(this).takeIf { Prefs.hasCookies(this) && it.exists() }

        val result = DownloadEngine.runDownload(
            processId = initialJob.id,
            url = initialJob.url,
            outputDir = stagingDir,
            mode = initialJob.mode,
            quality = Prefs.quality(this),
            audioBitrate = Prefs.audioBitrate(this),
            formatOverride = initialJob.formatOverride,
            speedLimitKbps = Prefs.speedLimitKbps(this),
            customFlags = Prefs.customFlags(this),
            cookiesFile = cookiesFile,
            onProgress = { percent, eta ->
                DownloadState.update(initialJob.id) {
                    it.copy(status = DownloadStatus.RUNNING, progressPercent = percent, etaSeconds = eta)
                }
                postJobNotification(DownloadState.jobs.value.first { it.id == initialJob.id })
            }
        )

        val finishedJob = DownloadState.jobs.value.firstOrNull { it.id == initialJob.id } ?: initialJob

        if (result.success && result.outputFilePath != null) {
            val file = File(result.outputFilePath)
            val savedUri = saveToDestination(file)
            val title = result.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension

            DownloadHistoryRepository.add(
                this, HistoryEntry(
                    id = finishedJob.id,
                    url = finishedJob.url,
                    title = title,
                    outputUri = savedUri?.toString(),
                    success = savedUri != null,
                    timestamp = System.currentTimeMillis(),
                    mode = finishedJob.mode
                )
            )
            if (savedUri != null) Prefs.setLastDownloadUri(this, savedUri.toString())
            ActivityLog.add(
                if (savedUri != null) "\u2705 Done: $title" else "\u26A0\uFE0F Saved locally but couldn't file it away: $title"
            )
            postCompletionNotification(title, success = savedUri != null, uri = savedUri)
        } else {
            DownloadHistoryRepository.add(
                this, HistoryEntry(
                    id = finishedJob.id,
                    url = finishedJob.url,
                    title = finishedJob.url,
                    outputUri = null,
                    success = false,
                    timestamp = System.currentTimeMillis(),
                    mode = finishedJob.mode
                )
            )
            ActivityLog.add("\u274C Failed: ${result.errorMessage ?: finishedJob.url}")
            postCompletionNotification(finishedJob.url, success = false, uri = null)
        }

        cancelJobNotification(finishedJob.id)
        DownloadState.remove(finishedJob.id)
    }

    private fun cancelJob(jobId: String) {
        DownloadEngine.cancel(jobId)
        DownloadState.update(jobId) { it.copy(status = DownloadStatus.CANCELED) }
        cancelJobNotification(jobId)
        DownloadState.remove(jobId)
        ActivityLog.add("Canceled")
        maybeStopService()
    }

    /**
     * Saves the finished file to wherever the user wants it: a custom SAF
     * folder if one is configured in Settings, otherwise the appropriate
     * public MediaStore collection (Movies/Music/Downloads) based on
     * extension - both are visible outside the app to other apps/file managers.
     */
    private fun saveToDestination(file: File): Uri? {
        val customFolder = Prefs.saveFolderUri(this)
        val result = if (customFolder != null) {
            saveToSafFolder(file, Uri.parse(customFolder)) ?: insertIntoMediaStore(file)
        } else {
            insertIntoMediaStore(file)
        }
        file.delete()
        return result
    }

    private fun saveToSafFolder(file: File, treeUri: Uri): Uri? {
        return try {
            val tree = DocumentFile.fromTreeUri(this, treeUri) ?: return null
            val mime = mimeForExtension(file.extension.lowercase())
            val doc = tree.createFile(mime, file.name) ?: return null
            contentResolver.openOutputStream(doc.uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            doc.uri
        } catch (_: Exception) {
            null
        }
    }

    private fun mimeForExtension(ext: String): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "opus" -> "audio/opus"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        else -> "application/octet-stream"
    }

    /** Copies the finished file into the right public MediaStore collection based on its extension. */
    private fun insertIntoMediaStore(file: File): Uri? {
        val ext = file.extension.lowercase()
        val (collection, mime, relativePath) = when (ext) {
            "mp3", "m4a", "aac", "opus" -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mimeForExtension(ext), "Music/YTDLPCommander"
            )
            "mp4", "mkv", "webm", "mov" -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mimeForExtension(ext), "Movies/YTDLPCommander"
            )
            else -> Triple(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, mimeForExtension(ext), "Download/YTDLPCommander"
            )
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = contentResolver.insert(collection, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    // ---- notifications ----

    private fun ensureForeground() {
        startForeground(AGGREGATE_NOTIF_ID, buildAggregateNotification())
    }

    private fun buildAggregateNotification(): Notification {
        val activeCount = DownloadState.jobs.value.size
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle("Downloading")
            .setContentText(if (activeCount > 1) "$activeCount downloads in progress" else "1 download in progress")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun postJobNotification(job: DownloadJob) {
        val mgr = getSystemService(NotificationManager::class.java)
        val progress = job.progressPercent.toInt().coerceIn(0, 100)
        val etaText = if (job.etaSeconds >= 0) " • ETA ${job.etaSeconds}s" else ""
        val notification = NotificationCompat.Builder(this, App.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle("${job.mode.emoji} " + job.title.ifBlank { job.url })
            .setContentText("$progress%$etaText")
            .setProgress(100, progress, job.status == DownloadStatus.FETCHING_INFO)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        mgr.notify(job.id.hashCode(), notification)
        mgr.notify(AGGREGATE_NOTIF_ID, buildAggregateNotification())
    }

    private fun cancelJobNotification(jobId: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.cancel(jobId.hashCode())
    }

    private fun postCompletionNotification(title: String, success: Boolean, uri: Uri?) {
        val mgr = getSystemService(NotificationManager::class.java)
        val contentIntent = if (success && uri != null) {
            PendingIntent.getActivity(
                this, uri.hashCode(),
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(this, App.CHANNEL_DOWNLOADS)
            .setSmallIcon(if (success) R.drawable.ic_notification_done else R.drawable.ic_notification_download)
            .setContentTitle(if (success) "Download complete" else "Download failed")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        mgr.notify((title + System.currentTimeMillis()).hashCode(), notification)
    }

    private fun acquireWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YtDlpCommander:download").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // safety cap: 30 minutes
        }
    }

    private fun maybeStopService() {
        if (DownloadState.activeCount() == 0) {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        const val ACTION_ENQUEUE = "com.blue.ytdlpcommander.action.ENQUEUE"
        const val ACTION_CANCEL = "com.blue.ytdlpcommander.action.CANCEL"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_FORMAT_OVERRIDE = "extra_format_override"
        const val AGGREGATE_NOTIF_ID = 100

        fun enqueue(context: android.content.Context, url: String, mode: DownloadMode = DownloadMode.VIDEO, formatOverride: String? = null) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_MODE, mode.name)
                if (formatOverride != null) putExtra(EXTRA_FORMAT_OVERRIDE, formatOverride)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: android.content.Context, jobId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_JOB_ID, jobId)
            }
            context.startService(intent)
        }
    }
}
