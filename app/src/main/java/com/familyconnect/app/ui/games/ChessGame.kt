package com.familyconnect.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlin.math.abs
import kotlin.random.Random

enum class ChessPiece { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, EMPTY }
enum class ChessColor { WHITE, BLACK }
enum class ChessDifficulty { EASY, MEDIUM, HARD }

data class ChessSquare(val piece: ChessPiece, val color: ChessColor?, val row: Int, val col: Int)
data class ChessBoardState(
    val squares: List<List<ChessSquare>> = initializeBoard(),
    val selectedSquare: Pair<Int, Int>? = null,
    val validMoves: List<Pair<Int, Int>> = emptyList(),
    val whiteToMove: Boolean = true,
    val gameOver: Boolean = false,
    val winner: ChessColor? = null,
    val message: String = "White to move"
)

fun initializeBoard(): List<List<ChessSquare>> {
    val board = MutableList(8) { MutableList(8) { ChessSquare(ChessPiece.EMPTY, null, 0, 0) } }
    
    val backRank = listOf(ChessPiece.ROOK, ChessPiece.KNIGHT, ChessPiece.BISHOP, ChessPiece.QUEEN, ChessPiece.KING, ChessPiece.BISHOP, ChessPiece.KNIGHT, ChessPiece.ROOK)
    for (col in 0..7) {
        board[0][col] = ChessSquare(backRank[col], ChessColor.BLACK, 0, col)
        board[1][col] = ChessSquare(ChessPiece.PAWN, ChessColor.BLACK, 1, col)
        board[6][col] = ChessSquare(ChessPiece.PAWN, ChessColor.WHITE, 6, col)
        board[7][col] = ChessSquare(backRank[col], ChessColor.WHITE, 7, col)
    }
    
    for (row in 2..5) {
        for (col in 0..7) {
            board[row][col] = ChessSquare(ChessPiece.EMPTY, null, row, col)
        }
    }
    
    return board
}

@Composable
fun ChessGame(onBack: () -> Unit = {}) {
    var difficulty by remember { mutableStateOf(ChessDifficulty.MEDIUM) }
    var gameStarted by remember { mutableStateOf(false) }

    if (!gameStarted) {
        ChessDifficultySelector(onDifficultySelected = { difficulty = it; gameStarted = true }, onBack = onBack)
    } else {
        ChessGameScreen(difficulty = difficulty, onBack = { gameStarted = false })
    }
}

@Composable
private fun ChessDifficultySelector(onDifficultySelected: (ChessDifficulty) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
        Text("♟ Chess", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 24.dp))
        Text("Select Difficulty", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 24.dp))

        listOf(
            ChessDifficulty.EASY to ("🟢 Easy" to "Random moves"),
            ChessDifficulty.MEDIUM to ("🟠 Medium" to "Smart moves"),
            ChessDifficulty.HARD to ("🔴 Hard" to "Optimal play")
        ).forEach { (diff, labels) ->
            Button(
                onClick = { onDifficultySelected(diff) },
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (diff) {
                        ChessDifficulty.EASY -> Color(0xFF4CAF50)
                        ChessDifficulty.MEDIUM -> Color(0xFFFFA726)
                        ChessDifficulty.HARD -> Color(0xFFEF5350)
                    }
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(labels.first, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(labels.second, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun ChessGameScreen(difficulty: ChessDifficulty, onBack: () -> Unit) {
    var boardState by remember { mutableStateOf(ChessBoardState()) }
    var showPromotionDialog by remember { mutableStateOf(false) }
    var promotionPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var promotionColor by remember { mutableStateOf<ChessColor?>(null) }

    LaunchedEffect(boardState) {
        if (!boardState.whiteToMove && !boardState.gameOver) {
            kotlinx.coroutines.delay(1000)
            val move = getChessAIMove(boardState.squares, difficulty)
            if (move != null) {
                val newSquares = boardState.squares.map { it.toMutableList() }.toMutableList()
                val fromSquare = newSquares[move.first.first][move.first.second]
                newSquares[move.second.first][move.second.second] = fromSquare.copy(row = move.second.first, col = move.second.second)
                newSquares[move.first.first][move.first.second] = ChessSquare(ChessPiece.EMPTY, null, move.first.first, move.first.second)
                
                val promotionPos = checkPawnPromotion(newSquares, move.second.first, move.second.second)
                if (promotionPos != null) {
                    promotionPosition = promotionPos
                    promotionColor = ChessColor.BLACK
                    showPromotionDialog = true
                    boardState = boardState.copy(squares = newSquares, selectedSquare = null, validMoves = emptyList())
                } else {
                    boardState = boardState.copy(squares = newSquares, whiteToMove = true, selectedSquare = null, validMoves = emptyList(), message = "White to move")
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("♟ Chess", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.width(48.dp))
        }

        Text(boardState.message, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

        ChessBoardUI(boardState) { row, col ->
            if (boardState.gameOver) return@ChessBoardUI
            
            val selected = boardState.selectedSquare
            val square = boardState.squares[row][col]
            
            if (Pair(row, col) in boardState.validMoves) {
                val fromSquare = boardState.squares[selected!!.first][selected.second]
                val newSquares = boardState.squares.map { it.toMutableList() }.toMutableList()
                newSquares[row][col] = fromSquare.copy(row = row, col = col)
                newSquares[selected.first][selected.second] = ChessSquare(ChessPiece.EMPTY, null, selected.first, selected.second)
                
                val promotionPos = checkPawnPromotion(newSquares, row, col)
                if (promotionPos != null) {
                    promotionPosition = promotionPos
                    promotionColor = ChessColor.WHITE
                    showPromotionDialog = true
                    boardState = boardState.copy(squares = newSquares, selectedSquare = null, validMoves = emptyList())
                } else {
                    boardState = boardState.copy(squares = newSquares, whiteToMove = !boardState.whiteToMove, selectedSquare = null, validMoves = emptyList(), message = if (boardState.whiteToMove) "Black to move" else "White to move")
                }
            } else if (square.color == if (boardState.whiteToMove) ChessColor.WHITE else ChessColor.BLACK) {
                val moves = getValidMoves(boardState.squares, row, col)
                boardState = boardState.copy(selectedSquare = Pair(row, col), validMoves = moves)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { boardState = ChessBoardState() }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Game", fontWeight = FontWeight.Bold)
        }

        if (showPromotionDialog && promotionPosition != null && promotionColor != null) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Pawn Promotion") },
                text = { 
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose a piece to promote to:", modifier = Modifier.padding(bottom = 8.dp))
                        listOf(ChessPiece.QUEEN to "♕ Queen", ChessPiece.ROOK to "♖ Rook", ChessPiece.BISHOP to "♗ Bishop", ChessPiece.KNIGHT to "♘ Knight").forEach { (piece, label) ->
                            Button(
                                onClick = {
                                    val (row, col) = promotionPosition!!
                                    val newSquares = boardState.squares.map { it.toMutableList() }.toMutableList()
                                    newSquares[row][col] = ChessSquare(piece, promotionColor, row, col)
                                    boardState = boardState.copy(squares = newSquares, whiteToMove = !boardState.whiteToMove, selectedSquare = null, validMoves = emptyList(), message = if (!boardState.whiteToMove) "Black to move" else "White to move")
                                    showPromotionDialog = false
                                    promotionPosition = null
                                    promotionColor = null
                                },
                                modifier = Modifier.fillMaxWidth().height(45.dp),
                                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                            ) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ChessBoardUI(state: ChessBoardState, onSquareClick: (Int, Int) -> Unit) {
    val rows = listOf("8", "7", "6", "5", "4", "3", "2", "1")
    val cols = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val squareSize = 40.dp
    val labelSize = 20.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        for (row in 0..7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(rows[row], modifier = Modifier.width(labelSize), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                for (col in 0..7) {
                    val square = state.squares[row][col]
                    val isLight = (row + col) % 2 == 0
                    val isSelected = state.selectedSquare == Pair(row, col)
                    val isValidMove = Pair(row, col) in state.validMoves
                    
                    Box(
                        modifier = Modifier.size(squareSize).background(
                            when {
                                isSelected -> Color(0xFF4CAF50)
                                isValidMove -> Color(0xFFFFA726)
                                isLight -> Color(0xFFF5DEB3)
                                else -> Color(0xFF8B7355)
                            }
                        ).clickable { onSquareClick(row, col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(getPieceEmoji(square), fontSize = 22.sp)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(labelSize))
            for (col in 0..7) {
                Text(cols[col], modifier = Modifier.width(squareSize), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}

private fun getPieceEmoji(square: ChessSquare): String {
    return when {
        square.piece == ChessPiece.EMPTY -> ""
        square.color == ChessColor.WHITE -> when (square.piece) {
            ChessPiece.PAWN -> "♙"; ChessPiece.ROOK -> "♖"; ChessPiece.KNIGHT -> "♘"; ChessPiece.BISHOP -> "♗"; ChessPiece.QUEEN -> "♕"; ChessPiece.KING -> "♔"; else -> ""
        }
        else -> when (square.piece) {
            ChessPiece.PAWN -> "♟"; ChessPiece.ROOK -> "♜"; ChessPiece.KNIGHT -> "♞"; ChessPiece.BISHOP -> "♝"; ChessPiece.QUEEN -> "♛"; ChessPiece.KING -> "♚"; else -> ""
        }
    }
}

private fun getValidMoves(board: List<List<ChessSquare>>, row: Int, col: Int): List<Pair<Int, Int>> {
    val square = board[row][col]
    if (square.piece == ChessPiece.EMPTY) return emptyList()
    
    val moves = mutableListOf<Pair<Int, Int>>()
    
    when (square.piece) {
        ChessPiece.PAWN -> {
            val dir = if (square.color == ChessColor.WHITE) -1 else 1
            val startRow = if (square.color == ChessColor.WHITE) 6 else 1
            
            // One square forward
            val oneSquare = Pair(row + dir, col)
            if (oneSquare.first in 0..7 && board[oneSquare.first][oneSquare.second].piece == ChessPiece.EMPTY) {
                moves.add(oneSquare)
                
                // Two squares forward on first move
                if (row == startRow) {
                    val twoSquare = Pair(row + 2 * dir, col)
                    if (board[twoSquare.first][twoSquare.second].piece == ChessPiece.EMPTY) {
                        moves.add(twoSquare)
                    }
                }
            }
            
            // Diagonal captures
            for (diagonalCol in listOf(col - 1, col + 1)) {
                if (diagonalCol in 0..7) {
                    val target = board[row + dir][diagonalCol]
                    if (target.piece != ChessPiece.EMPTY && target.color != square.color) {
                        moves.add(Pair(row + dir, diagonalCol))
                    }
                }
            }
        }
        ChessPiece.KNIGHT -> {
            val knightMoves = listOf(
                Pair(2, 1), Pair(2, -1), Pair(-2, 1), Pair(-2, -1),
                Pair(1, 2), Pair(1, -2), Pair(-1, 2), Pair(-1, -2)
            )
            for ((dr, dc) in knightMoves) {
                val nr = row + dr
                val nc = col + dc
                if (nr in 0..7 && nc in 0..7) {
                    val target = board[nr][nc]
                    if (target.piece == ChessPiece.EMPTY || target.color != square.color) {
                        moves.add(Pair(nr, nc))
                    }
                }
            }
        }
        ChessPiece.BISHOP, ChessPiece.ROOK, ChessPiece.QUEEN -> {
            val directions = when (square.piece) {
                ChessPiece.BISHOP -> listOf(Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1))
                ChessPiece.ROOK -> listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
                ChessPiece.QUEEN -> listOf(
                    Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
                    Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
                )
                else -> emptyList()
            }
            
            for ((dr, dc) in directions) {
                var nr = row + dr
                var nc = col + dc
                while (nr in 0..7 && nc in 0..7) {
                    val target = board[nr][nc]
                    if (target.piece == ChessPiece.EMPTY) {
                        moves.add(Pair(nr, nc))
                    } else {
                        if (target.color != square.color) moves.add(Pair(nr, nc))
                        break
                    }
                    nr += dr
                    nc += dc
                }
            }
        }
        ChessPiece.KING -> {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = row + dr
                    val nc = col + dc
                    if (nr in 0..7 && nc in 0..7) {
                        val target = board[nr][nc]
                        if (target.piece == ChessPiece.EMPTY || target.color != square.color) {
                            moves.add(Pair(nr, nc))
                        }
                    }
                }
            }
        }
        else -> {}
    }
    
    return moves
}

private fun checkPawnPromotion(board: List<List<ChessSquare>>, row: Int, col: Int): Pair<Int, Int>? {
    val piece = board[row][col]
    if (piece.piece == ChessPiece.PAWN) {
        if ((piece.color == ChessColor.WHITE && row == 0) || (piece.color == ChessColor.BLACK && row == 7)) {
            return Pair(row, col)
        }
    }
    return null
}

private fun getChessAIMove(board: List<List<ChessSquare>>, difficulty: ChessDifficulty): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
    val blackPieces = mutableListOf<Pair<Int, Int>>()
    for (row in 0..7) for (col in 0..7) {
        if (board[row][col].color == ChessColor.BLACK) blackPieces.add(Pair(row, col))
    }
    
    if (blackPieces.isEmpty()) return null
    
    val allMoves = blackPieces.flatMap { (row, col) ->
        val moves = getValidMoves(board, row, col)
        moves.map { Pair(Pair(row, col), it) }
    }
    
    return if (allMoves.isNotEmpty()) {
        when (difficulty) {
            ChessDifficulty.EASY -> allMoves.random()
            ChessDifficulty.MEDIUM -> if (Random.nextBoolean()) allMoves.random() else allMoves.firstOrNull()
            ChessDifficulty.HARD -> allMoves.firstOrNull() ?: allMoves.random()
        }
    } else null
}
