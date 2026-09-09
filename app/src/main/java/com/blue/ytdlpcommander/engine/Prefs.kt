package com.blue.ytdlpcommander.engine

import android.content.Context

enum class QualityPreset(val label: String, val formatArg: String) {
    BEST("Best available", "bv*+ba/b"),
    Q1080("1080p", "bv*[height<=1080]+ba/b[height<=1080]"),
    Q720("720p", "bv*[height<=720]+ba/b[height<=720]"),
    Q480("480p", "bv*[height<=480]+ba/b[height<=480]"),
    AUDIO_ONLY("Audio only (MP3)", "ba/b")
}

enum class AudioBitrate(val label: String, val kbps: String) {
    K128("128 kbps", "128"),
    K192("192 kbps", "192"),
    K320("320 kbps", "320")
}

object Prefs {
    private const val NAME = "ytdlp_prefs"
    private const val KEY_MAX_PARALLEL = "max_parallel"
    private const val KEY_QUALITY = "quality"
    private const val KEY_AUDIO_BITRATE = "audio_bitrate"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_QUICK_TAB_ENABLED = "quick_tab_enabled"
    private const val KEY_SPEED_LIMIT_KBPS = "speed_limit_kbps"
    private const val KEY_CUSTOM_FLAGS = "custom_flags"
    private const val KEY_HAS_COOKIES = "has_cookies"
    private const val KEY_SAVE_FOLDER_URI = "save_folder_uri"
    private const val KEY_LAST_DOWNLOAD_URI = "last_download_uri"

    fun maxParallel(context: Context): Int = prefs(context).getInt(KEY_MAX_PARALLEL, 2)
    fun setMaxParallel(context: Context, value: Int) = prefs(context).edit().putInt(KEY_MAX_PARALLEL, value).apply()

    fun quality(context: Context): QualityPreset {
        val name = prefs(context).getString(KEY_QUALITY, QualityPreset.BEST.name)
        return try {
            QualityPreset.valueOf(name ?: QualityPreset.BEST.name)
        } catch (_: Exception) {
            QualityPreset.BEST
        }
    }
    fun setQuality(context: Context, preset: QualityPreset) =
        prefs(context).edit().putString(KEY_QUALITY, preset.name).apply()

    fun audioBitrate(context: Context): AudioBitrate {
        val name = prefs(context).getString(KEY_AUDIO_BITRATE, AudioBitrate.K192.name)
        return try {
            AudioBitrate.valueOf(name ?: AudioBitrate.K192.name)
        } catch (_: Exception) {
            AudioBitrate.K192
        }
    }
    fun setAudioBitrate(context: Context, value: AudioBitrate) =
        prefs(context).edit().putString(KEY_AUDIO_BITRATE, value.name).apply()

    fun wifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnly(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    fun quickTabEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_QUICK_TAB_ENABLED, false)
    fun setQuickTabEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_QUICK_TAB_ENABLED, value).apply()

    /** 0 = unlimited. */
    fun speedLimitKbps(context: Context): Int = prefs(context).getInt(KEY_SPEED_LIMIT_KBPS, 0)
    fun setSpeedLimitKbps(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_SPEED_LIMIT_KBPS, value.coerceAtLeast(0)).apply()

    /** Raw space-separated extra yt-dlp CLI flags, applied to every download. */
    fun customFlags(context: Context): String = prefs(context).getString(KEY_CUSTOM_FLAGS, "") ?: ""
    fun setCustomFlags(context: Context, value: String) =
        prefs(context).edit().putString(KEY_CUSTOM_FLAGS, value).apply()

    /** Cookies are always copied to a fixed internal path when imported - this just tracks whether one exists. */
    fun hasCookies(context: Context): Boolean = prefs(context).getBoolean(KEY_HAS_COOKIES, false)
    fun setHasCookies(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_HAS_COOKIES, value).apply()
    fun cookiesFile(context: Context) = java.io.File(context.filesDir, "cookies.txt")

    /** SAF tree URI string for a user-chosen save folder, or null to use the default MediaStore locations. */
    fun saveFolderUri(context: Context): String? = prefs(context).getString(KEY_SAVE_FOLDER_URI, null)
    fun setSaveFolderUri(context: Context, uriString: String?) =
        prefs(context).edit().putString(KEY_SAVE_FOLDER_URI, uriString).apply()

    fun lastDownloadUri(context: Context): String? = prefs(context).getString(KEY_LAST_DOWNLOAD_URI, null)
    fun setLastDownloadUri(context: Context, uriString: String?) =
        prefs(context).edit().putString(KEY_LAST_DOWNLOAD_URI, uriString).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
