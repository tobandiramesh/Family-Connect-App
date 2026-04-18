package com.familyconnect.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TicTacToeGameState(
    val board: List<String> = List(9) { "" },
    val isXNext: Boolean = true,
    val winner: String? = null,
    val isDraw: Boolean = false
)

@Composable
fun TicTacToeGame() {
    var gameState by remember { mutableStateOf(TicTacToeGameState()) }
    val currentPlayer = if (gameState.isXNext) "X" else "O"
    val playerXSymbol = "❌"
    val playerOSymbol = "⭕"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Title
        Text(
            text = "🎮 Tic-Tac-Toe",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Game Status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE8EAF6), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (gameState.winner != null) {
                    Text(
                        text = "🎉 Winner: ${if (gameState.winner == "X") playerXSymbol else playerOSymbol}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                } else if (gameState.isDraw) {
                    Text(
                        text = "🤝 It's a Draw!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57C00)
                    )
                } else {
                    Text(
                        text = "Current Turn: ${if (currentPlayer == "X") playerXSymbol else playerOSymbol}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Game Board
        Column(
            modifier = Modifier
                .background(Color(0xFFBBBBBB), RoundedCornerShape(12.dp))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        TicTacToeCell(
                            value = gameState.board[index],
                            onClick = {
                                if (gameState.board[index].isEmpty() && gameState.winner == null && !gameState.isDraw) {
                                    val newBoard = gameState.board.toMutableList()
                                    newBoard[index] = currentPlayer
                                    
                                    val winner = calculateWinner(newBoard)
                                    val isDraw = newBoard.all { it.isNotEmpty() }
                                    
                                    gameState = gameState.copy(
                                        board = newBoard,
                                        isXNext = !gameState.isXNext,
                                        winner = winner,
                                        isDraw = isDraw && winner == null
                                    )
                                }
                            },
                            enabled = gameState.winner == null && !gameState.isDraw && gameState.board[index].isEmpty()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Game Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${playerXSymbol} Moves", fontSize = 14.sp, color = Color.Gray)
                Text("${gameState.board.count { it == "X" }}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Divider(modifier = Modifier.width(1.dp).height(40.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${playerOSymbol} Moves", fontSize = 14.sp, color = Color.Gray)
                Text("${gameState.board.count { it == "O" }}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reset Button
        Button(
            onClick = { gameState = TicTacToeGameState() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset",
                modifier = Modifier.size(20.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Game", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Instructions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📋 How to Play:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Take turns clicking empty squares", fontSize = 11.sp)
                Text("• Get 3 in a row (horizontal, vertical, diagonal) to win", fontSize = 11.sp)
                Text("• First player is always X", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TicTacToeCell(
    value: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when (value) {
            "X" -> Text("❌", fontSize = 32.sp)
            "O" -> Text("⭕", fontSize = 32.sp)
            else -> {}
        }
    }
}

private fun calculateWinner(board: List<String>): String? {
    val lines = listOf(
        // Rows
        listOf(0, 1, 2),
        listOf(3, 4, 5),
        listOf(6, 7, 8),
        // Columns
        listOf(0, 3, 6),
        listOf(1, 4, 7),
        listOf(2, 5, 8),
        // Diagonals
        listOf(0, 4, 8),
        listOf(2, 4, 6)
    )

    for (line in lines) {
        val (a, b, c) = line
        if (board[a].isNotEmpty() && board[a] == board[b] && board[a] == board[c]) {
            return board[a]
        }
    }

    return null
}
