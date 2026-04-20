package com.familyconnect.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GameItem(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val status: String = "Available"
)

@Composable
fun GamesScreen() {
    var selectedGame by remember { mutableStateOf<String?>(null) }

    when (selectedGame) {
        "tictactoe" -> TicTacToeGame(onBack = { selectedGame = null })
        "memory_match" -> MemoryMatchGame(onBack = { selectedGame = null })
        "word_puzzle" -> WordPuzzleGame(onBack = { selectedGame = null })
        "chess" -> ChessGame(onBack = { selectedGame = null })
        else -> GameLibraryScreen(onGameSelected = { selectedGame = it })
    }
}

@Composable
private fun GameLibraryScreen(onGameSelected: (String) -> Unit) {
    val games = listOf(
        GameItem(
            id = "tictactoe",
            name = "Tic-Tac-Toe",
            emoji = "⭕",
            description = "Classic 3x3 grid game",
            status = "Available"
        ),
        GameItem(
            id = "memory_match",
            name = "Memory Match",
            emoji = "🧠",
            description = "Find matching pairs",
            status = "Available"
        ),
        GameItem(
            id = "word_puzzle",
            name = "Word Puzzle",
            emoji = "🔤",
            description = "Solve word challenges",
            status = "Available"
        ),
        GameItem(
            id = "chess",
            name = "Chess",
            emoji = "♟",
            description = "Strategic board game",
            status = "Available"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(16.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎮 Family Games",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Have fun with your family",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Featured Game Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(Color(0xFF5C6BC0), Color(0xFF3949AB))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onGameSelected("tictactoe") }
                .padding(0.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(Color(0xFF5C6BC0), Color(0xFF3949AB))
                        )
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⭕ Featured Game", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tic-Tac-Toe", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Play the classic game with your family", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onGameSelected("tictactoe") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text("Play Now", color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // All Games Grid
        Text(
            text = "All Games",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(games.size) { index ->
                GameCard(
                    game = games[index],
                    onGameClick = { onGameSelected(games[index].id) }
                )
            }
        }
    }
}

@Composable
private fun GameCard(game: GameItem, onGameClick: () -> Unit) {
    val isComingSoon = game.status == "Coming Soon"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isComingSoon) { onGameClick() }
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isComingSoon) Color(0xFFF5F5F5) else Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isComingSoon) 0.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = game.emoji,
                fontSize = 36.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = game.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isComingSoon) Color.Gray else Color.Black
                )
                Text(
                    text = game.description,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (isComingSoon) {
                Surface(
                    modifier = Modifier
                        .background(Color(0xFFFFEAB3), RoundedCornerShape(6.dp))
                        .padding(4.dp, 2.dp),
                    color = Color.Transparent
                ) {
                    Text(
                        text = "Coming Soon",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57C00)
                    )
                }
            } else {
                Button(
                    onClick = onGameClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Play", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}
