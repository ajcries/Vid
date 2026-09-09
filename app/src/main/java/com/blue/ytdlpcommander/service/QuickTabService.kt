package com.blue.ytdlpcommander.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.blue.ytdlpcommander.App
import com.blue.ytdlpcommander.MainActivity
import com.blue.ytdlpcommander.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * A small always-on-top tab docked to the screen edge (Messenger chat heads /
 * Shazam's floating button style). Tap it (without dragging) to pop open a
 * small panel with a URL field and Download button. Tap anywhere outside the
 * panel to collapse it back to just the tab. Drag the tab itself down to the
 * bottom of the screen and release to dismiss the tab entirely (it tints red
 * as you get close, same "drag to remove" feel as a chat head).
 */
class QuickTabService : Service() {

    private var windowManager: WindowManager? = null
    private var handleView: View? = null
    private var panelView: View? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var screenHeight = 0

    private var handleY = 300

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(metrics)
        screenHeight = metrics.heightPixels
        showHandle()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showHandle() {
        if (handleView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_tab_handle, null)
        handleView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = handleY
        }
        handleParams = params

        var downX = 0f
        var downY = 0f
        var startY = 0
        var moved = false
        val dismissZonePx = 220
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startY = params.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    if (abs(dy) > 8) moved = true
                    params.y = (startY + dy).toInt().coerceIn(0, screenHeight)
                    windowManager?.updateViewLayout(v, params)

                    val nearBottom = params.y > screenHeight - dismissZonePx
                    tintHandle(v, dismissing = nearBottom)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val nearBottom = params.y > screenHeight - dismissZonePx
                    if (moved && nearBottom) {
                        Toast.makeText(this, "Quick download tab turned off", Toast.LENGTH_SHORT).show()
                        stopSelf()
                    } else {
                        handleY = params.y
                        tintHandle(v, dismissing = false)
                        if (!moved) togglePanel()
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(view, params)
    }

    private fun tintHandle(view: View, dismissing: Boolean) {
        val bg = view.findViewById<LinearLayout>(R.id.tab_handle_root) ?: return
        bg.setBackgroundResource(if (dismissing) R.drawable.tab_handle_bg_danger else R.drawable.tab_handle_bg)
    }

    private fun togglePanel() {
        if (panelView != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        val wm = windowManager ?: return
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_tab_panel, null)
        panelView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // FLAG_NOT_TOUCH_MODAL lets touches outside this window pass through
            // to whatever's beneath (or, combined with WATCH_OUTSIDE_TOUCH, be
            // reported to us as ACTION_OUTSIDE) instead of being swallowed -
            // this is what makes "tap anywhere else to collapse" possible.
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = (handleParams?.y ?: 300) + 80
        }

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                true
            } else {
                false
            }
        }

        val urlInput = view.findViewById<EditText>(R.id.panel_url_input)
        val pasteButton = view.findViewById<Button>(R.id.panel_paste_button)
        val downloadButton = view.findViewById<Button>(R.id.panel_download_button)

        getClipboardUrlIfLooksLikeOne()?.let { urlInput.setText(it) }

        pasteButton.setOnClickListener {
            getClipboardUrlIfLooksLikeOne(requireUrlShape = false)?.let { urlInput.setText(it) }
        }

        downloadButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "Paste a link first", Toast.LENGTH_SHORT).show()
            } else {
                DownloadService.enqueue(this, url)
                Toast.makeText(this, "Added to downloads", Toast.LENGTH_SHORT).show()
                hidePanel()
            }
        }

        wm.addView(view, params)
    }

    private fun hidePanel() {
        panelView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelView = null
    }

    private fun getClipboardUrlIfLooksLikeOne(requireUrlShape: Boolean = true): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return null
        return if (!requireUrlShape || text.startsWith("http://") || text.startsWith("https://")) text else null
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, QuickTabService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_QUICK_TAB)
            .setSmallIcon(R.drawable.ic_tab_handle)
            .setContentTitle("Quick download shortcut active")
            .setContentText("Tap the floating tab to paste a link, or drag it to the bottom to turn off")
            .setContentIntent(openIntent)
            .addAction(0, "Turn off", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        hidePanel()
        handleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        handleView = null
    }

    companion object {
        const val ACTION_STOP = "com.blue.ytdlpcommander.action.STOP_QUICK_TAB"
        const val NOTIF_ID = 200

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun start(context: Context) {
            val intent = Intent(context, QuickTabService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QuickTabService::class.java))
        }
    }
}
