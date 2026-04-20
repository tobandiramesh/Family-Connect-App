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
fun SnookerGame(onBack: () -> Unit = {}) {
    var gameStarted by remember { mutableStateOf(false) }
    var currentPlayer by remember { mutableStateOf(1) }
    var player1Score by remember { mutableStateOf(0) }
    var player2Score by remember { mutableStateOf(0) }
    var redBallsRemaining by remember { mutableStateOf(15) }
    var coloredBallsRemaining by remember { mutableStateOf(6) }

    if (!gameStarted) {
        SnookerStartScreen(onStartGame = { gameStarted = true }, onBack = onBack)
    } else {
        SnookerGameScreen(
            currentPlayer = currentPlayer,
            player1Score = player1Score,
            player2Score = player2Score,
            redBallsRemaining = redBallsRemaining,
            coloredBallsRemaining = coloredBallsRemaining,
            onPotBall = { ballValue ->
                if (currentPlayer == 1) {
                    player1Score += ballValue
                } else {
                    player2Score += ballValue
                }
                if (ballValue == 1) {
                    redBallsRemaining = (redBallsRemaining - 1).coerceAtLeast(0)
                }
            },
            onMissShot = { currentPlayer = if (currentPlayer == 1) 2 else 1 },
            onNewGame = { 
                gameStarted = true
                currentPlayer = 1
                player1Score = 0
                player2Score = 0
                redBallsRemaining = 15
                coloredBallsRemaining = 6
            },
            onBack = {
                gameStarted = false
                onBack()
            }
        )
    }
}

@Composable
private fun SnookerStartScreen(onStartGame: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
        Text("🎱 Snooker", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 24.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("How to Play:", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                Text("• Pot red balls (1 pt each)\n• Pot colored balls for bonus\n• Miss = Turn switches\n• Highest score wins!\n• Red: 1pt, Yellow: 2pt, Green: 3pt\n  Brown: 4pt, Blue: 5pt, Pink: 6pt, Black: 7pt", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
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
private fun SnookerGameScreen(
    currentPlayer: Int,
    player1Score: Int,
    player2Score: Int,
    redBallsRemaining: Int,
    coloredBallsRemaining: Int,
    onPotBall: (Int) -> Unit,
    onMissShot: () -> Unit,
    onNewGame: () -> Unit,
    onBack: () -> Unit
) {
    val gameOver = redBallsRemaining == 0 && coloredBallsRemaining == 0

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("🎱 Snooker", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f).background(if (currentPlayer == 1) Color(0xFFE8F5E9) else Color.White, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(Color.Transparent)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Player 1", fontSize = 12.sp, color = Color.Gray)
                    Text(player1Score.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
            }
            Card(
                modifier = Modifier.weight(1f).background(if (currentPlayer == 2) Color(0xFFE8F5E9) else Color.White, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(Color.Transparent)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Player 2", fontSize = 12.sp, color = Color.Gray)
                    Text(player2Score.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().background(Color(0xFF1B5E20), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(Color.Transparent)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pool Table", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Box(modifier = Modifier.weight(1f).height(80.dp).background(Color(0xFF2E7D32), RoundedCornerShape(8.dp)))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Red Balls: $redBallsRemaining", fontSize = 12.sp, color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                Text("Colored Balls: $coloredBallsRemaining", fontSize = 12.sp, color = Color(0xFFFFD600), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("🎯 Player $currentPlayer's Turn", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (currentPlayer == 1) Color(0xFF4CAF50) else Color(0xFFEF5350))

        Spacer(modifier = Modifier.height(16.dp))

        if (!gameOver) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onPotBall(1) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(Color(0xFFEF5350)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Pot Red (1 pt)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Button(
                    onClick = { onPotBall(Random.nextInt(2, 8)) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(Color(0xFFFFD600)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Pot Colored (2-7 pts)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Button(
                    onClick = onMissShot,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(Color(0xFF90A4AE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Miss Shot", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        if (gameOver) {
            Card(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(Color.Transparent)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉 Game Over!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("Player ${if (player1Score > player2Score) 1 else 2} Wins!", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
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
