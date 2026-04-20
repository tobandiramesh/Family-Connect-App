package com.familyconnect.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

data class WordPuzzle(val category: String, val answer: String, val hint: String, val scrambled: String)

enum class WordDifficulty { EASY, MEDIUM, HARD }

// Comprehensive word pool for dynamic puzzle generation
private fun getWordPool(): List<WordPuzzle> {
    return listOf(
        // EASY (5-6 letters)
        WordPuzzle("Fruit", "APPLE", "Red fruit", "PEPLA"),
        WordPuzzle("Fruit", "MANGO", "Yellow tropical fruit", "GOMNA"),
        WordPuzzle("Fruit", "GRAPE", "Purple bunch", "PAGRE"),
        WordPuzzle("Fruit", "LEMON", "Sour yellow citrus", "NOMEL"),
        WordPuzzle("Animal", "TIGER", "Striped big cat", "RETIG"),
        WordPuzzle("Animal", "HORSE", "Majestic steed", "SHERO"),
        WordPuzzle("Animal", "SNAKE", "Slithering reptile", "KNASE"),
        WordPuzzle("Animal", "EAGLE", "Large flying bird", "GEALE"),
        WordPuzzle("Country", "JAPAN", "Land of cherry blossoms", "NAPAJ"),
        WordPuzzle("Country", "CHILE", "Long South American nation", "ILCHE"),
        WordPuzzle("Country", "SPAIN", "Mediterranean nation", "PANIS"),
        WordPuzzle("Food", "BREAD", "Bakery staple", "DEBAR"),
        WordPuzzle("Food", "SUGAR", "Sweet substance", "RAGUS"),
        WordPuzzle("Food", "SALT", "Seasoning", "TALS"),
        
        // MEDIUM (7-8 letters)
        WordPuzzle("Country", "FRANCE", "Eiffel Tower location", "CARNEF"),
        WordPuzzle("Country", "IRELAND", "Emerald Isle", "DANLEIR"),
        WordPuzzle("Country", "POLAND", "Central European nation", "NOADLP"),
        WordPuzzle("Country", "GREECE", "Mediterranean islands", "CEREGE"),
        WordPuzzle("Food", "PIZZA", "Italian dish", "ZIPPA"),
        WordPuzzle("Food", "SANDWICH", "Between two breads", "DWICHNAS"),
        WordPuzzle("Food", "DESSERT", "Sweet course", "SERTSED"),
        WordPuzzle("Food", "CHOCOLATE", "Cocoa treat", "TOCHOLAC"),
        WordPuzzle("Sport", "TENNIS", "Racket sport", "SINNET"),
        WordPuzzle("Sport", "BADMINTON", "Shuttlecock game", "NONBATTDIM"),
        WordPuzzle("Sport", "CRICKET", "Bat and ball", "CRTICEK"),
        WordPuzzle("Sport", "SWIMMING", "Water sport", "GWIMNIM"),
        WordPuzzle("Music", "GUITAR", "String instrument", "RITUAG"),
        WordPuzzle("Music", "PIANO", "Keyboard instrument", "NAIPO"),
        WordPuzzle("Music", "TRUMPET", "Brass instrument", "PRUMTET"),
        WordPuzzle("Music", "VIOLIN", "Stringed instrument", "NOLIVI"),
        WordPuzzle("Weather", "THUNDER", "After lightning", "DERTUN"),
        WordPuzzle("Weather", "RAINBOW", "After rain", "BOOWINRA"),
        WordPuzzle("Weather", "CYCLONE", "Spinning storm", "CLONEYC"),
        WordPuzzle("Plant", "FLOWER", "Colorful bloom", "ROWFLE"),
        WordPuzzle("Plant", "CACTUS", "Desert plant", "TUSSCA"),
        WordPuzzle("Plant", "BAMBOO", "Tall grass plant", "BOOBAM"),
        WordPuzzle("Color", "ORANGE", "Citrus color", "NAGGORE"),
        WordPuzzle("Color", "PURPLE", "Royal color", "REPULP"),
        WordPuzzle("Color", "YELLOW", "Sunny color", "WOLEYLLY"),
        WordPuzzle("Body", "STOMACH", "Digestion organ", "CHOMATSO"),
        WordPuzzle("Body", "HEART", "Pumping organ", "TRAEH"),
        WordPuzzle("Body", "KIDNEY", "Filtering organ", "DYENIK"),
        
        // HARD (9-10 letters)
        WordPuzzle("Country", "AUSTRALIA", "Down under", "AILAUSTRS"),
        WordPuzzle("Country", "ARGENTINA", "South American nation", "AIGENTNAR"),
        WordPuzzle("Country", "SINGAPORE", "Lion city", "ERASGNIPO"),
        WordPuzzle("Country", "MADAGASCAR", "Island nation", "DRAGASACIM"),
        WordPuzzle("Occupation", "ARCHITECT", "Building designer", "CARCTHITE"),
        WordPuzzle("Occupation", "CARPENTER", "Woodworker", "REPNETERC"),
        WordPuzzle("Occupation", "ASTRONOMER", "Star observer", "REMONASTRO"),
        WordPuzzle("Occupation", "JOURNALIST", "News reporter", "ISTLJOURAN"),
        WordPuzzle("Continent", "ANTARCTICA", "Frozen continent", "CANTICTRAA"),
        WordPuzzle("Continent", "CARIBBEAN", "Island sea region", "RAIBENABBC"),
        WordPuzzle("Science", "HYDROGEN", "Lightest element", "NEGYDORH"),
        WordPuzzle("Science", "TELESCOPE", "Astronomy tool", "COPEELSTET"),
        WordPuzzle("Science", "ECOSYSTEM", "Living environment", "MESOCYSTE"),
        WordPuzzle("Emotion", "HAPPINESS", "State of joy", "SNISSPAPE"),
        WordPuzzle("Emotion", "CONFUSED", "Bewildered state", "DEFCONOUS"),
        WordPuzzle("Emotion", "SURPRISED", "Shocked feeling", "DISURPRSE"),
        WordPuzzle("Technology", "COMPUTER", "Electronic device", "COMPUTERE"),
        WordPuzzle("Technology", "INTERNET", "Global network", "RETINETEN"),
        WordPuzzle("Technology", "ALGORITHM", "Step-by-step procedure", "ITHMALGOR"),
        WordPuzzle("Nature", "WATERFALL", "Cascading water", "TAWERFALL"),
        WordPuzzle("Nature", "MOUNTAIN", "Large elevation", "NTAUNIOMM"),
        WordPuzzle("Nature", "VOLCANO", "Erupting mountain", "VOLCANNO"),
        WordPuzzle("History", "PHARAOH", "Egyptian ruler", "AOHRAPHA"),
        WordPuzzle("History", "MEDIEVAL", "Middle ages", "MEDALLIVE"),
        WordPuzzle("Geography", "EQUATOR", "Earth's middle", "ROTAOQUE"),
        WordPuzzle("Geography", "LATITUDE", "North-south position", "DUTTILAE")
    )
}

// Function to generate random puzzles based on difficulty
private fun generateDynamicPuzzles(difficulty: WordDifficulty, count: Int = 5): List<WordPuzzle> {
    val allPuzzles = getWordPool()
    
    val filteredPuzzles = when (difficulty) {
        WordDifficulty.EASY -> allPuzzles.filter { it.answer.length in 4..6 }
        WordDifficulty.MEDIUM -> allPuzzles.filter { it.answer.length in 7..8 }
        WordDifficulty.HARD -> allPuzzles.filter { it.answer.length >= 9 }
    }
    
    return filteredPuzzles.shuffled().take(count)
}

@Composable
fun WordPuzzleGame(onBack: () -> Unit = {}) {
    var difficulty by remember { mutableStateOf(WordDifficulty.MEDIUM) }
    var gameStarted by remember { mutableStateOf(false) }

    if (!gameStarted) {
        WordDifficultySelector(onDifficultySelected = { difficulty = it; gameStarted = true }, onBack = onBack)
    } else {
        WordGameScreen(difficulty = difficulty, onBack = { gameStarted = false })
    }
}

@Composable
private fun WordDifficultySelector(onDifficultySelected: (WordDifficulty) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
        Text("🔤 Word Puzzle", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 24.dp))
        Text("Select Difficulty", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 24.dp))

        listOf(
            WordDifficulty.EASY to ("🟢 Easy" to "5-6 letter words"),
            WordDifficulty.MEDIUM to ("🟠 Medium" to "7-8 letter words"),
            WordDifficulty.HARD to ("🔴 Hard" to "9-10 letter words")
        ).forEach { (diff, labels) ->
            Button(
                onClick = { onDifficultySelected(diff) },
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (diff) {
                        WordDifficulty.EASY -> Color(0xFF4CAF50)
                        WordDifficulty.MEDIUM -> Color(0xFFFFA726)
                        WordDifficulty.HARD -> Color(0xFFEF5350)
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
private fun WordGameScreen(difficulty: WordDifficulty, onBack: () -> Unit) {
    val wordPuzzles = remember { generateDynamicPuzzles(difficulty, count = 5) }

    var currentIndex by remember { mutableStateOf(0) }
    var userAnswer by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    var showHint by remember { mutableStateOf(false) }
    var usedHints by remember { mutableStateOf(0) }
    val currentPuzzle = wordPuzzles.getOrNull(currentIndex) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("🔤 Word Puzzle", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${currentIndex + 1}/${wordPuzzles.size}", fontSize = 14.sp, color = Color.Gray)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Category", fontSize = 12.sp, color = Color.Gray); Text(currentPuzzle.category, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Score", fontSize = 12.sp, color = Color.Gray); Text(score.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Unscramble:", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
                Text(currentPuzzle.scrambled, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C6BC0), modifier = Modifier.padding(12.dp))
            }
        }

        if (showHint) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xFFFFF9C4), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(Color.Transparent)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hint:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57C00))
                    Text(currentPuzzle.hint, fontSize = 14.sp, color = Color(0xFFE65100))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it.uppercase() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your answer...") },
            label = { Text("Type your answer") },
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showHint = !showHint }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFFFFA726))) {
                Text("💡 Hint", color = Color.White)
            }
            Button(
                onClick = {
                    if (userAnswer.trim().uppercase() == currentPuzzle.answer) {
                        score += (10 - usedHints)
                        if (currentIndex < wordPuzzles.size - 1) {
                            currentIndex++
                            userAnswer = ""
                            showHint = false
                            usedHints = 0
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))
            ) {
                Text("Submit", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (currentIndex == wordPuzzles.size - 1 && userAnswer.trim().uppercase() == currentPuzzle.answer) {
            Card(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(Color.Transparent)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉 Puzzle Complete!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("Final Score: $score", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) {
                        Text("Back to Games", color = Color.White)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
