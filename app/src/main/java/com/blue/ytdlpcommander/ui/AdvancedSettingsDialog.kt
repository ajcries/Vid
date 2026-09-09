package com.blue.ytdlpcommander.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blue.ytdlpcommander.engine.DownloadEngine
import com.blue.ytdlpcommander.engine.Prefs
import com.blue.ytdlpcommander.engine.UpdateStatus
import com.blue.ytdlpcommander.ui.theme.MonoFamily
import kotlinx.coroutines.launch

@Composable
fun AdvancedSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var speedLimit by remember { mutableStateOf(Prefs.speedLimitKbps(context).let { if (it == 0) "" else it.toString() }) }
    var customFlags by remember { mutableStateOf(Prefs.customFlags(context)) }
    var hasCookies by remember { mutableStateOf(Prefs.hasCookies(context)) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }

    val cookiesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                Prefs.cookiesFile(context).outputStream().use { output -> input.copyTo(output) }
            }
            Prefs.setHasCookies(context, true)
            hasCookies = true
        } catch (_: Exception) {
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u2699\uFE0F Advanced Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("\u26A1 Speed limit (KB/s, blank = unlimited)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = speedLimit,
                    onValueChange = { speedLimit = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("0") }
                )

                Spacer(Modifier.height(16.dp))
                Text("\uD83D\uDD27 Extra yt-dlp flags (space-separated)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = customFlags,
                    onValueChange = { customFlags = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("--proxy socks5://host:port", fontFamily = MonoFamily, fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 12.sp)
                )

                Spacer(Modifier.height(16.dp))
                Text("\uD83C\uDF6A Cookies file (for login-gated content)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(onClick = { cookiesPicker.launch(arrayOf("*/*")) }) {
                        Text(if (hasCookies) "Replace cookies.txt" else "Import cookies.txt")
                    }
                    if (hasCookies) {
                        Spacer(Modifier.height(0.dp).then(Modifier))
                        TextButton(onClick = {
                            Prefs.cookiesFile(context).delete()
                            Prefs.setHasCookies(context, false)
                            hasCookies = false
                        }) { Text("Clear") }
                    }
                }
                Text(
                    "Export a cookies.txt (Netscape format) from your browser using an extension, then import it here.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                Text("\uD83D\uDD04 yt-dlp engine", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            updating = true
                            updateStatusText = null
                            scope.launch {
                                val status = DownloadEngine.updateYoutubeDl(context)
                                updateStatusText = when (status) {
                                    is UpdateStatus.Updated -> "Updated to the latest yt-dlp \u2705"
                                    is UpdateStatus.UpToDate -> "Already up to date"
                                    is UpdateStatus.Failed -> "Update failed: ${status.message}"
                                }
                                updating = false
                            }
                        },
                        enabled = !updating
                    ) {
                        Text("Update yt-dlp")
                    }
                    if (updating) {
                        Spacer(Modifier.height(0.dp))
                        CircularProgressIndicator(modifier = Modifier.height(16.dp).then(Modifier), strokeWidth = 2.dp)
                    }
                }
                updateStatusText?.let {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.setSpeedLimitKbps(context, speedLimit.toIntOrNull() ?: 0)
                Prefs.setCustomFlags(context, customFlags)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
