package com.blue.ytdlpcommander.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.blue.ytdlpcommander.R
import com.blue.ytdlpcommander.engine.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The tile that appears in the notification shade's quick settings drop-down. */
class QuickTabTileService : TileService() {

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        job = CoroutineScope(Dispatchers.Main).launch {
            QuickTabService.isRunning.collect { updateTile() }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
        job = null
    }

    override fun onClick() {
        super.onClick()
        if (QuickTabService.isRunning.value) {
            QuickTabService.stop(this)
            Prefs.setQuickTabEnabled(this, false)
            updateTile()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapseCompat(intent)
            return
        }

        QuickTabService.start(this)
        Prefs.setQuickTabEnabled(this, true)
        updateTile()
    }

    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = QuickTabService.isRunning.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Quick Download"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tab_handle)
        tile.updateTile()
    }
}
