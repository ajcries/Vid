package com.blue.ytdlpcommander

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {

    val appScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Extracting the bundled python/yt-dlp/ffmpeg binaries can take a
        // couple of seconds on first run - do it off the main thread so the
        // app doesn't stutter on launch. DownloadEngine checks readiness
        // before starting a job, so a download requested before this
        // finishes just waits briefly rather than failing.
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
                EngineReadyState.setReady(true)
            } catch (e: Exception) {
                EngineReadyState.setError(e.message ?: "Failed to initialize download engine")
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows download progress and completion"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_QUICK_TAB, "Quick download tab", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the floating quick-download shortcut available"
            }
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads_channel"
        const val CHANNEL_QUICK_TAB = "quick_tab_channel"
    }
}
