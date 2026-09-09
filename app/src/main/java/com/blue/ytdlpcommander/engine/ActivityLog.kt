package com.blue.ytdlpcommander.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActivityLog {
    private const val MAX_LINES = 200
    private val _lines = MutableStateFlow<List<String>>(listOf("\u26A1 YT-DLP Commander ready."))
    val lines: StateFlow<List<String>> = _lines

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(line: String) {
        val stamped = "[${timeFormat.format(Date())}] $line"
        _lines.update { (it + stamped).takeLast(MAX_LINES) }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
