package com.familyconnect.app.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

data class MemoryCard(val id: Int, val emoji: String, var isFlipped: Boolean = false, var isMatched: Boolean = false)

enum class MemoryDifficulty { EASY, MEDIUM, HARD }

@Composable
fun MemoryMatchGame(onBack: () -> Unit = {}) {
    var difficulty by remember { mutableStateOf(MemoryDifficulty.MEDIUM) }
    var gameStarted by remember { mutableStateOf(false) }

    if (!gameStarted) {
        MemoryDifficultySelector(onDifficultySelected = { difficulty = it; gameStarted = true }, onBack = onBack)
    } else {
        MemoryGameScreen(difficulty = difficulty, onBack = { gameStarted = false })
    }
}

@Composable
private fun MemoryDifficultySelector(onDifficultySelected: (MemoryDifficulty) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
        Text("🧠 Memory Match", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 24.dp))
        Text("Select Difficulty", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 24.dp))

        listOf(
            MemoryDifficulty.EASY to ("🟢 Easy" to "6 Pairs • 12 Cards"),
            MemoryDifficulty.MEDIUM to ("🟠 Medium" to "8 Pairs • 16 Cards"),
            MemoryDifficulty.HARD to ("🔴 Hard" to "12 Pairs • 24 Cards")
        ).forEach { (diff, labels) ->
            Button(
                onClick = { onDifficultySelected(diff) },
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (diff) {
                        MemoryDifficulty.EASY -> Color(0xFF4CAF50)
                        MemoryDifficulty.MEDIUM -> Color(0xFFFFA726)
                        MemoryDifficulty.HARD -> Color(0xFFEF5350)
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
private fun MemoryGameScreen(difficulty: MemoryDifficulty, onBack: () -> Unit) {
    val pairCount = when (difficulty) { MemoryDifficulty.EASY -> 6; MemoryDifficulty.MEDIUM -> 8; MemoryDifficulty.HARD -> 12 }
    val emojis = listOf("🍎", "🍌", "🍊", "🍇", "🍓", "🍉", "🍒", "🍑", "🥝", "🍍", "🥭", "🍐")
    val cardList = (0 until pairCount).flatMap { i -> listOf(emojis[i], emojis[i]) }.mapIndexed { idx, emoji -> MemoryCard(idx, emoji) }.shuffled()

    var cards by remember { mutableStateOf(cardList) }
    var flippedCards by remember { mutableStateOf<List<Int>>(emptyList()) }
    var moves by remember { mutableStateOf(0) }
    var matchedPairs by remember { mutableStateOf(0) }

    LaunchedEffect(flippedCards) {
        if (flippedCards.size == 2) {
            if (cards[flippedCards[0]].emoji == cards[flippedCards[1]].emoji) {
                cards = cards.mapIndexed { idx, card -> if (idx in flippedCards) card.copy(isMatched = true) else card }
                matchedPairs++
            }
            kotlinx.coroutines.delay(800)
            flippedCards = emptyList()
            moves++
        }
    }

    val isGameWon = matchedPairs == pairCount

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("🧠 Memory Match", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.width(48.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Moves", fontSize = 12.sp, color = Color.Gray); Text(moves.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Pairs", fontSize = 12.sp, color = Color.Gray); Text("$matchedPairs/$pairCount", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(cards.size) { idx ->
                MemoryCardView(card = cards[idx], isFlipped = idx in flippedCards || cards[idx].isMatched, onClick = {
                    if (idx !in flippedCards && !cards[idx].isMatched && flippedCards.size < 2) flippedCards = flippedCards + idx
                })
            }
        }

        if (isGameWon) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉 You Won!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Text("Completed in $moves moves", fontSize = 14.sp, color = Color.Gray)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) { Text("Back to Games", color = Color.White) }
            }
        }
    }
}

@Composable
private fun MemoryCardView(card: MemoryCard, isFlipped: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(if (isFlipped) Color(0xFF5C6BC0) else Color(0xFFB0BEC5))
    Box(modifier = Modifier.size(70.dp).background(bgColor, RoundedCornerShape(8.dp)).clickable(enabled = !isFlipped) { onClick() }, contentAlignment = Alignment.Center) {
        Text(if (isFlipped) card.emoji else "?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
