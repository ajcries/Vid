package com.blue.ytdlpcommander.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blue.ytdlpcommander.EngineReadyState
import com.blue.ytdlpcommander.engine.ActivityLog
import com.blue.ytdlpcommander.engine.AnalyzeResult
import com.blue.ytdlpcommander.engine.AudioBitrate
import com.blue.ytdlpcommander.engine.DownloadEngine
import com.blue.ytdlpcommander.engine.DownloadMode
import com.blue.ytdlpcommander.engine.DownloadState
import com.blue.ytdlpcommander.engine.Prefs
import com.blue.ytdlpcommander.engine.QualityPreset
import com.blue.ytdlpcommander.service.DownloadService
import com.blue.ytdlpcommander.ui.theme.BorderSubtle
import com.blue.ytdlpcommander.ui.theme.MonoFamily
import com.blue.ytdlpcommander.ui.theme.NeonGreen
import com.blue.ytdlpcommander.ui.theme.SurfaceCardAlt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineReady by EngineReadyState.ready.collectAsState()
    val activeJobs by DownloadState.jobs.collectAsState()
    val logLines by ActivityLog.lines.collectAsState()

    var url by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(DownloadMode.VIDEO) }
    var quality by remember { mutableStateOf(Prefs.quality(context)) }
    var audioBitrate by remember { mutableStateOf(Prefs.audioBitrate(context)) }
    var saveFolder by remember { mutableStateOf(Prefs.saveFolderUri(context)) }
    var showAdvanced by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var analyzeResult by remember { mutableStateOf<AnalyzeResult?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        Prefs.setSaveFolderUri(context, uri.toString())
        saveFolder = uri.toString()
        ActivityLog.add("\uD83D\uDCC1 Save folder changed")
    }

    if (showAdvanced) {
        AdvancedSettingsDialog(onDismiss = { showAdvanced = false })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u26A1", fontSize = 26.sp)
                Spacer(Modifier.padding(4.dp))
                Column {
                    Text("YT-DLP Commander", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Multi-platform downloader", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard {
                SectionLabel("URL")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; analyzeResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("YouTube \u00B7 TikTok \u00B7 Instagram \u00B7 +1000 sites\u2026", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 13.sp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("\uD83D\uDCCB Paste") { readClipboard(context)?.let { url = it } }
                    PillButton("\uD83D\uDD0D Formats", enabled = url.isNotBlank() && !analyzing) {
                        analyzing = true
                        analyzeResult = null
                        ActivityLog.add("Analyzing link\u2026")
                        scope.launch {
                            val result = DownloadEngine.analyze(
                                url.trim(),
                                Prefs.customFlags(context),
                                Prefs.cookiesFile(context).takeIf { Prefs.hasCookies(context) }
                            )
                            analyzeResult = result
                            analyzing = false
                            ActivityLog.add(if (result.success) "\u2139\uFE0F Found ${result.formats.size} formats" else "\u274C Analyze failed: ${result.errorMessage}")
                        }
                    }
                    PillButton("\u2715 Clear", enabled = url.isNotBlank()) { url = ""; analyzeResult = null }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard {
                SectionLabel("MODE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadMode.entries.forEach { m ->
                        ModeChip(m, selected = mode == m) { mode = m; analyzeResult = null }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (mode == DownloadMode.VIDEO) {
                SectionCard {
                    SectionLabel("QUALITY")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val shortLabels = mapOf(
                            QualityPreset.BEST to "Best",
                            QualityPreset.Q1080 to "1080p",
                            QualityPreset.Q720 to "720p",
                            QualityPreset.Q480 to "480p"
                        )
                        listOf(QualityPreset.BEST, QualityPreset.Q1080, QualityPreset.Q720, QualityPreset.Q480).forEach { q ->
                            PillButton(shortLabels[q] ?: q.label, selected = quality == q) { quality = q; Prefs.setQuality(context, q) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            } else if (mode == DownloadMode.AUDIO) {
                SectionCard {
                    SectionLabel("BITRATE")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioBitrate.entries.forEach { b ->
                            PillButton(b.label, selected = audioBitrate == b) { audioBitrate = b; Prefs.setAudioBitrate(context, b) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            analyzeResult?.let { result ->
                SectionCard {
                    SectionLabel("ANALYSIS")
                    if (!result.success) {
                        Text(result.errorMessage ?: "Failed", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else {
                        Text(result.title ?: "Untitled", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        result.durationSeconds?.let {
                            Text("Duration: ${it / 60}:${(it % 60).toString().padStart(2, '0')}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Tap a format to download it directly:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        result.formats.filter { it.formatId.isNotBlank() }.take(12).forEach { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        DownloadService.enqueue(context, url.trim(), DownloadMode.VIDEO, f.formatId)
                                        ActivityLog.add("Queued specific format ${f.formatId}")
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${f.resolution} \u00B7 ${f.ext}", fontFamily = MonoFamily, fontSize = 12.sp)
                                Text(
                                    f.approxSizeMb?.let { "%.0f MB".format(it) } ?: f.note,
                                    fontFamily = MonoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            SectionCard {
                SectionLabel("SAVE TO")
                Text(
                    saveFolder?.let { Uri.parse(it).path ?: it } ?: "Auto (Movies / Music / Downloads)",
                    fontFamily = MonoFamily, fontSize = 11.sp, color = NeonGreen
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("\uD83D\uDCC1 Change") { folderPicker.launch(null) }
                    if (saveFolder != null) {
                        PillButton("Use default") { Prefs.setSaveFolderUri(context, null); saveFolder = null }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        DownloadService.enqueue(context, url.trim(), mode)
                        url = ""
                        analyzeResult = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = url.isNotBlank() && engineReady
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(if (engineReady) "DOWNLOAD" else "PREPARING ENGINE\u2026", fontWeight = FontWeight.Bold)
            }

            if (!engineReady) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "First-time setup is extracting the download engine - a few seconds, once.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (activeJobs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("IN PROGRESS")
                activeJobs.take(3).forEach { job ->
                    Text(
                        "${job.mode.emoji} ${job.title.ifBlank { job.url }} \u2014 ${job.progressPercent.toInt()}%",
                        fontSize = 12.sp, fontFamily = MonoFamily,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UtilityButton(Icons.Filled.History, "History", onOpenHistory)
                UtilityButton(Icons.Filled.Share, "Share") {
                    val lastUri = Prefs.lastDownloadUri(context)
                    if (lastUri != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(lastUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share download"))
                    }
                }
                UtilityButton(Icons.Filled.Settings, "Adv.") { showAdvanced = true }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("LOG")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 180.dp)
                    .background(SurfaceCardAlt, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(logLines.size) {
                    if (logLines.isNotEmpty()) listState.animateScrollToItem(logLines.size - 1)
                }
                LazyColumn(state = listState) {
                    items(logLines) { line ->
                        Text(line, fontFamily = MonoFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun PillButton(label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else SurfaceCardAlt
    val fg = if (selected) Color.White else if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) bg else BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModeChip(mode: DownloadMode, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else SurfaceCardAlt
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            "${mode.emoji} ${mode.label}",
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun UtilityButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(SurfaceCardAlt, RoundedCornerShape(10.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, modifier = Modifier.height(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(3.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
