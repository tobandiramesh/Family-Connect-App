package com.familyconnect.app.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DebugLogManager {
    private const val MAX_LOGS = 50
    private const val TAG = "DebugLogManager"
    
    private val _logsFlow = MutableStateFlow<List<DebugLog>>(emptyList())
    val logsFlow: StateFlow<List<DebugLog>> = _logsFlow.asStateFlow()
    
    data class DebugLog(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val isError: Boolean = false
    ) {
        val timeString: String
            get() {
                val dateFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                return dateFormat.format(java.util.Date(timestamp))
            }
    }
    
    fun log(tag: String, message: String, isError: Boolean = false) {
        try {
            val newLog = DebugLog(
                tag = tag,
                message = message,
                isError = isError
            )
            
            val currentLogs = _logsFlow.value.toMutableList()
            currentLogs.add(newLog)
            
            // Keep only last MAX_LOGS
            if (currentLogs.size > MAX_LOGS) {
                currentLogs.removeAt(0)
            }
            
            _logsFlow.value = currentLogs
            
            // Also log to Android logcat
            if (isError) {
                Log.e(tag, message)
            } else {
                Log.d(tag, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding log: ${e.message}")
        }
    }
    
    fun clear() {
        _logsFlow.value = emptyList()
    }
}
