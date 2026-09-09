package com.blue.ytdlpcommander.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class DownloadStatus { QUEUED, FETCHING_INFO, RUNNING, DONE, FAILED, CANCELED }

data class DownloadJob(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "",
    val mode: DownloadMode = DownloadMode.VIDEO,
    val formatOverride: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Float = 0f,
    val etaSeconds: Long = -1,
    val outputUri: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Shared, in-memory list of jobs currently queued or in progress (not yet
 * moved to history). Every screen and the service itself read/write through
 * this single object, same pattern as the screen recorder's RecordingState.
 */
object DownloadState {
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs

    fun enqueue(job: DownloadJob) {
        _jobs.update { it + job }
    }

    fun update(id: String, transform: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun remove(id: String) {
        _jobs.update { list -> list.filterNot { it.id == id } }
    }

    fun activeCount(): Int = _jobs.value.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.FETCHING_INFO }
}
