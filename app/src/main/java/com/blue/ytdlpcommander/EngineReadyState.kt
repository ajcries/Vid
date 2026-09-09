package com.blue.ytdlpcommander

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object EngineReadyState {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setReady(value: Boolean) {
        _ready.value = value
    }

    fun setError(message: String) {
        _error.value = message
    }
}
