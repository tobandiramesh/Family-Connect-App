package com.familyconnect.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@Composable
fun LudoGame(onBack: () -> Unit = {}) {
    var gameStarted by remember { mutableStateOf(false) }
    var currentPlayer by remember { mutableStateOf(1) }
    var player1Position by remember { mutableStateOf(0) }
    var player2Position by remember { mutableStateOf(0) }
    var player3Position by remember { mutableStateOf(0) }
    var player4Position by remember { mutableStateOf(0) }
    var diceValue by remember { mutableStateOf(0) }

    if (!gameStarted) {
        LudoStartScreen(onStartGame = { gameStarted = true }, onBack = onBack)
    } else {
        LudoGameScreen(
            currentPlayer = currentPlayer,
            player1Position = player1Position,
            player2Position = player2Position,
            player3Position = player3Position,
            player4Position = player4Position,
            diceValue = diceValue,
            onRollDice = { rollValue ->
                diceValue = rollValue
                when (currentPlayer) {
                    1 -> player1Position = (player1Position + rollValue).coerceAtMost(52)
                    2 -> player2Position = (player2Position + rollValue).coerceAtMost(52)
                    3 -> player3Position = (player3Position + rollValue).coerceAtMost(52)
                    4 -> player4Position = (player4Position + rollValue).coerceAtMost(52)
                }
                currentPlayer = if (rollValue == 6) currentPlayer else (currentPlayer % 4) + 1
            },
            onNewGame = { 
                gameStarted = true
                currentPlayer = 1
                player1Position = 0
                player2Position = 0
                player3Position = 0
                player4Position = 0
                diceValue = 0
            },
            onBack = {
                gameStarted = false
                onBack()
            }
        )
    }
}

@Composable
private fun LudoStartScreen(onStartGame: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
        Text("🎲 Ludo", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 24.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("How to Play:", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                Text("• Roll the dice to move\n• Roll a 6 to roll again\n• Move your piece to position 52\n• First to reach home wins!\n• 4 players take turns", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
            }
        }
        Button(
            onClick = onStartGame,
            modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Start Game", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun LudoGameScreen(
    currentPlayer: Int,
    player1Position: Int,
    player2Position: Int,
    player3Position: Int,
    player4Position: Int,
    diceValue: Int,
    onRollDice: (Int) -> Unit,
    onNewGame: () -> Unit,
    onBack: () -> Unit
) {
    val positions = listOf(player1Position, player2Position, player3Position, player4Position)
    val playerColors = listOf(Color(0xFF4CAF50), Color(0xFFEF5350), Color(0xFFFFD600), Color(0xFF2196F3))
    val playerEmojis = listOf("🟢", "🔴", "🟡", "🔵")
    val gameWon = positions.any { it >= 52 }
    val winner = positions.indexOfFirst { it >= 52 } + 1

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("🎲 Ludo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) { playerIndex ->
                Card(
                    modifier = Modifier.weight(1f).background(if (currentPlayer == playerIndex + 1) Color(0xFFE8F5E9) else Color.White, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(Color.Transparent)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(playerEmojis[playerIndex], fontSize = 14.sp)
                        Text("P${playerIndex + 1}", fontSize = 10.sp, color = Color.Gray)
                        Text(positions[playerIndex].toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = playerColors[playerIndex])
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5DEB3), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(Color.Transparent)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Board Track (0-52)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val maxPos = positions.maxOrNull() ?: 0
                    val progressWidth = if (maxPos > 0) (maxPos.toFloat() / 52f) else 0.1f
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(progressWidth)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$maxPos/52", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Last Roll: $diceValue 🎲", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("🎯 Player $currentPlayer's Turn ${playerEmojis[currentPlayer - 1]}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = playerColors[currentPlayer - 1])

        Spacer(modifier = Modifier.height(16.dp))

        if (!gameWon) {
            Button(
                onClick = { onRollDice(Random.nextInt(1, 7)) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("🎲 Roll Dice", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (gameWon) {
            Card(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(Color.Transparent)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉 Game Over!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("${playerEmojis[winner - 1]} Player $winner Wins!", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFF90A4AE)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Game", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
