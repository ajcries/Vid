package com.blue.ytdlpcommander.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blue.ytdlpcommander.engine.DownloadHistoryRepository
import com.blue.ytdlpcommander.engine.HistoryEntry
import com.blue.ytdlpcommander.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        entries = withContext(Dispatchers.IO) { DownloadHistoryRepository.getAll(context) }
    }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloads yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            HistoryRow(
                entry = entry,
                onOpen = {
                    val uri = Uri.parse(entry.outputUri)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                },
                onRedownload = {
                    DownloadService.enqueue(context, entry.url, entry.mode)
                },
                onShare = {
                    val uriString = entry.outputUri
                    if (uriString != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(Uri.parse(uriString)) ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }
                },
                onDelete = {
                    entry.outputUri?.let {
                        try { context.contentResolver.delete(Uri.parse(it), null, null) } catch (_: Exception) {}
                    }
                    DownloadHistoryRepository.remove(context, entry.id)
                    refreshTrigger++
                }
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onRedownload: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var fileExists by remember(entry.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(entry.id) {
        fileExists = withContext(Dispatchers.IO) {
            val uriString = entry.outputUri ?: return@withContext false
            try {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title.ifBlank { entry.url }, maxLines = 1, fontWeight = FontWeight.Medium, fontSize = 14.sp)

                    // The clickable link: tapping it always re-downloads from the
                    // original source, which is the point if the saved file was deleted.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            entry.url,
                            maxLines = 1,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickableRedownload(onRedownload)
                        )
                    }

                    Text(
                        when (fileExists) {
                            true -> "Saved on device"
                            false -> "File no longer available - tap the link to redownload"
                            null -> "Checking…"
                        },
                        fontSize = 11.sp,
                        color = if (fileExists == false) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (fileExists == true) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Open")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                } else {
                    IconButton(onClick = onRedownload) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Redownload")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove from history")
                }
            }
        }
    }
}

private fun Modifier.clickableRedownload(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
