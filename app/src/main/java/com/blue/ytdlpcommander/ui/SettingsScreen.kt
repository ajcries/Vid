package com.blue.ytdlpcommander.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blue.ytdlpcommander.engine.Prefs
import com.blue.ytdlpcommander.service.QuickTabService

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var maxParallel by remember { mutableStateOf(Prefs.maxParallel(context)) }
    var wifiOnly by remember { mutableStateOf(Prefs.wifiOnly(context)) }
    var quickTabEnabled by remember { mutableStateOf(Prefs.quickTabEnabled(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Parallel downloads: $maxParallel", fontWeight = FontWeight.SemiBold)
        Text(
            "How many downloads can run at the same time.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = maxParallel.toFloat(),
            onValueChange = {
                maxParallel = it.toInt()
                Prefs.setMaxParallel(context, maxParallel)
            },
            valueRange = 1f..5f,
            steps = 3
        )

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi only", fontWeight = FontWeight.SemiBold)
                Text(
                    "Skip starting new downloads on mobile data.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; Prefs.setWifiOnly(context, it) })
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Floating quick-download tab", fontWeight = FontWeight.SemiBold)
                Text(
                    "A small shortcut on your screen, over any app - tap it to paste a link and download in the background.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = quickTabEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return@Switch
                    }
                    quickTabEnabled = enabled
                    Prefs.setQuickTabEnabled(context, enabled)
                    if (enabled) QuickTabService.start(context) else QuickTabService.stop(context)
                }
            )
        }
    }
}
