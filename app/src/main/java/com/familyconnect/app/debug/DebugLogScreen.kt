package com.familyconnect.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DebugLogScreen(onClose: () -> Unit) {
    val logs by DebugLogManager.logsFlow.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F1F1F))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "📊 DEBUG LOGS (${logs.size} entries)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Logs Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "🔵 Waiting for logs/events...",
                        color = Color.Green,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    Column {
                        logs.forEach { debugLog ->
                            val color = when {
                                debugLog.isError -> Color(0xFFFF6B6B)  // Bright red for errors
                                debugLog.message.contains("✅") -> Color.Green
                                debugLog.message.contains("❌") -> Color.Red
                                debugLog.message.contains("🔴") -> Color(0xFFFF9999)
                                debugLog.message.contains("⚠️") -> Color.Yellow
                                debugLog.message.contains("🚀") -> Color.Cyan
                                debugLog.message.contains("📱") -> Color(0xFF00FFFF)
                                else -> Color(0xFF00FF00)  // Default greenish
                            }
                            
                            Text(
                                "[${debugLog.timeString}] ${debugLog.tag}: ${debugLog.message}",
                                color = color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
