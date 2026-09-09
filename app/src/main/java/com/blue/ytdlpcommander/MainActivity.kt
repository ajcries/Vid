package com.blue.ytdlpcommander

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.blue.ytdlpcommander.engine.Prefs
import com.blue.ytdlpcommander.service.QuickTabService
import com.blue.ytdlpcommander.ui.DownloadsScreen
import com.blue.ytdlpcommander.ui.HistoryScreen
import com.blue.ytdlpcommander.ui.HomeScreen
import com.blue.ytdlpcommander.ui.SettingsScreen
import com.blue.ytdlpcommander.ui.theme.YtDlpCommanderTheme

private enum class Section { HOME, DOWNLOADS, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // If the user previously enabled the quick tab and the overlay
        // permission is (still) granted, make sure it's actually running -
        // it may have been stopped by the system since the last launch.
        if (Prefs.quickTabEnabled(this) && Settings.canDrawOverlays(this)) {
            QuickTabService.start(this)
        }

        setContent {
            YtDlpCommanderTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var section by remember { mutableStateOf(Section.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == Section.HOME,
                    onClick = { section = Section.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = section == Section.DOWNLOADS,
                    onClick = { section = Section.DOWNLOADS },
                    icon = { Icon(Icons.Filled.CloudDownload, contentDescription = "Downloads") },
                    label = { Text("Downloads") }
                )
                NavigationBarItem(
                    selected = section == Section.HISTORY,
                    onClick = { section = Section.HISTORY },
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = section == Section.SETTINGS,
                    onClick = { section = Section.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize()) {
            when (section) {
                Section.HOME -> HomeScreen(onOpenHistory = { section = Section.HISTORY })
                Section.DOWNLOADS -> DownloadsScreen()
                Section.HISTORY -> HistoryScreen()
                Section.SETTINGS -> SettingsScreen()
            }
        }
    }
}
