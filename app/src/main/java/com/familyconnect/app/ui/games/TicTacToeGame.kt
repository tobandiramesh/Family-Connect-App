package com.familyconnect.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

enum class Player { X, O, NONE }
enum class Difficulty { EASY, MEDIUM, HARD }
enum class GameMode { HUMAN_VS_HUMAN, HUMAN_VS_AI }

data class GameState(
    val board: List<Player> = List(9) { Player.NONE },
    val currentPlayer: Player = Player.X,
    val winner: Player? = null,
    val isDraw: Boolean = false,
    val gameMode: GameMode = GameMode.HUMAN_VS_HUMAN,
    val difficulty: Difficulty = Difficulty.HARD
)

@Composable
fun TicTacToeGame(onBack: () -> Unit = {}) {
    var gameState by remember { mutableStateOf(GameState()) }
    var showMenu by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showMenu) {
            MenuScreen(
                onStartGame = { mode, diff ->
                    gameState = GameState(gameMode = mode, difficulty = diff)
                    showMenu = false
                },
                onBackToGames = onBack
            )
        } else {
            GameScreen(
                state = gameState,
                onMove = { index ->
                    if (gameState.board[index] == Player.NONE && gameState.winner == null && !gameState.isDraw) {
                        gameState = updateState(gameState, index)
                        if (gameState.gameMode == GameMode.HUMAN_VS_AI && gameState.winner == null && !gameState.isDraw) {
                            val aiIndex = getAiMove(gameState)
                            if (aiIndex != -1) gameState = updateState(gameState, aiIndex)
                        }
                    }
                },
                onReset = { showMenu = true }
            )
        }
    }
}

@Composable
fun MenuScreen(onStartGame: (GameMode, Difficulty) -> Unit, onBackToGames: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBackToGames, modifier = Modifier.widthIn(max = 80.dp)) { Text("Back") }
            Spacer(modifier = Modifier.weight(1f))
        }
        Text("Tic Tac Toe", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { onStartGame(GameMode.HUMAN_VS_HUMAN, Difficulty.EASY) }, modifier = Modifier.fillMaxWidth()) {
            Text("Human vs Human")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Vs AI Difficulty:", fontWeight = FontWeight.SemiBold)
        Row {
            Button(onClick = { onStartGame(GameMode.HUMAN_VS_AI, Difficulty.EASY) }) { Text("Easy") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onStartGame(GameMode.HUMAN_VS_AI, Difficulty.MEDIUM) }) { Text("Med") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onStartGame(GameMode.HUMAN_VS_AI, Difficulty.HARD) }) { Text("Hard") }
        }
    }
}

@Composable
fun GameScreen(state: GameState, onMove: (Int) -> Unit, onReset: () -> Unit) {
    Text(text = if (state.winner != null) "Winner: ${state.winner.name}" else if (state.isDraw) "Draw!" else "Turn: ${state.currentPlayer.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (state.winner != null) Color.Green else Color.Black)
    Spacer(modifier = Modifier.height(24.dp))
    Box(modifier = Modifier.size(300.dp).background(Color.LightGray, RoundedCornerShape(8.dp))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = 5f
            drawLine(Color.Black, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), sw)
            drawLine(Color.Black, Offset(2 * size.width / 3, 0f), Offset(2 * size.width / 3, size.height), sw)
            drawLine(Color.Black, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), sw)
            drawLine(Color.Black, Offset(0f, 2 * size.height / 3), Offset(size.width, 2 * size.height / 3), sw)
        }
        Column {
            for (i in 0..2) {
                Row {
                    for (j in 0..2) {
                        val idx = i * 3 + j
                        Box(modifier = Modifier.size(100.dp).clickable { onMove(idx) }, contentAlignment = Alignment.Center) {
                            Text(text = if (state.board[idx] == Player.NONE) "" else state.board[idx].name, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (state.board[idx] == Player.X) Color.Blue else Color.Red)
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onReset) { Text("Back to Menu") }
}

fun updateState(state: GameState, index: Int): GameState {
    val nb = state.board.toMutableList().also { it[index] = state.currentPlayer }
    val w = checkWinner(nb)
    return state.copy(board = nb, currentPlayer = if (state.currentPlayer == Player.X) Player.O else Player.X, winner = w, isDraw = w == null && nb.none { it == Player.NONE })
}

fun checkWinner(b: List<Player>): Player? {
    val p = listOf(listOf(0,1,2),listOf(3,4,5),listOf(6,7,8),listOf(0,3,6),listOf(1,4,7),listOf(2,5,8),listOf(0,4,8),listOf(2,4,6))
    for (i in p) if (b[i[0]] != Player.NONE && b[i[0]] == b[i[1]] && b[i[0]] == b[i[2]]) return b[i[0]]
    return null
}

fun getAiMove(state: GameState): Int {
    val m = state.board.indices.filter { state.board[it] == Player.NONE }
    if (m.isEmpty()) return -1
    return when (state.difficulty) {
        Difficulty.EASY -> m.random()
        Difficulty.MEDIUM -> if (Random.nextFloat() < 0.5f) minimax(state.board, Player.O).index else m.random()
        Difficulty.HARD -> minimax(state.board, Player.O).index
    }
}

data class Move(val index: Int, val score: Int)

fun minimax(b: List<Player>, p: Player): Move {
    val m = b.indices.filter { b[it] == Player.NONE }
    val w = checkWinner(b)
    if (w == Player.X) return Move(-1, -10)
    if (w == Player.O) return Move(-1, 10)
    if (m.isEmpty()) return Move(-1, 0)
    val opponent = if (p == Player.O) Player.X else Player.O
    val res = m.map { id ->
        val nb = b.toMutableList().also { it[id] = p }
        Move(id, minimax(nb, opponent).score)
    }
    return if (p == Player.O) res.maxByOrNull { it.score }!! else res.minByOrNull { it.score }!!
}
