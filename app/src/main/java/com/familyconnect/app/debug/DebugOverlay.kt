package com.familyconnect.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DebugOverlay() {
    val logs by DebugLogManager.logsFlow.collectAsState()
    var isExpanded by remember { mutableStateOf(true) }
    
    if (logs.isEmpty()) {
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        if (isExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.4f)
                    .align(Alignment.BottomEnd)
                    .border(1.dp, Color.Green),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔍 DEBUG LOGS",
                            color = Color.Green,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { DebugLogManager.clear() },
                                modifier = Modifier
                                    .height(24.dp)
                                    .padding(0.dp),
                                contentPadding = PaddingValues(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("Clear", fontSize = 10.sp, color = Color.White)
                            }
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    // Logs
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState(), reverseScrolling = true)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        logs.forEach { log ->
                            val logColor = if (log.isError) Color.Red else Color.Green
                            Text(
                                text = "[${log.timeString}] ${log.tag}: ${log.message}",
                                color = logColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Collapsed button
            Button(
                onClick = { isExpanded = true },
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomEnd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Green.copy(alpha = 0.8f)
                )
            ) {
                Text("Logs (${logs.size})", color = Color.Black, fontSize = 10.sp)
            }
        }
    }
}
