package com.blue.ytdlpcommander.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blue.ytdlpcommander.engine.DownloadState
import com.blue.ytdlpcommander.engine.DownloadStatus
import com.blue.ytdlpcommander.service.DownloadService

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val jobs by DownloadState.jobs.collectAsState()

    if (jobs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing downloading right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(jobs, key = { it.id }) { job ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${job.mode.emoji} " + job.title.ifBlank { job.url }, maxLines = 1, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(statusLabel(job.status, job.etaSeconds), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { DownloadService.cancel(context, job.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    }
                    Spacer()
                    LinearProgressIndicator(
                        progress = (job.progressPercent / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = 4.dp))
}

private fun statusLabel(status: DownloadStatus, etaSeconds: Long): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.FETCHING_INFO -> "Fetching info…"
    DownloadStatus.RUNNING -> if (etaSeconds >= 0) "Downloading • ETA ${etaSeconds}s" else "Downloading…"
    DownloadStatus.DONE -> "Done"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELED -> "Canceled"
}
