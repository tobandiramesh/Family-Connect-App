package com.familyconnect.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

data class TwentyQuestion(val answer: String, val category: String, val hint: String)

@Composable
fun TwentyQuestionsGame(onBack: () -> Unit = {}) {
    val questions = listOf(
        TwentyQuestion("Elephant", "Animal", "Large mammal with long trunk"),
        TwentyQuestion("Pizza", "Food", "Italian dish with cheese and toppings"),
        TwentyQuestion("Moon", "Space", "Earth's natural satellite"),
        TwentyQuestion("Bicycle", "Transport", "Two-wheeled vehicle"),
        TwentyQuestion("Lighthouse", "Building", "Guides ships with light"),
        TwentyQuestion("Rainbow", "Nature", "Appears after rain"),
        TwentyQuestion("Diamond", "Gem", "Hardest natural mineral"),
        TwentyQuestion("Volcano", "Geology", "Mountain that erupts"),
        TwentyQuestion("Clock", "Object", "Measures time"),
        TwentyQuestion("Penguin", "Bird", "Lives in Antarctic region")
    )

    var currentAnswer by remember { mutableStateOf(questions.random()) }
    var questionsAsked by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var playerWon by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    var userGuess by remember { mutableStateOf("") }
    var responses by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)).padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("❓ 20 Questions", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.width(48.dp))
        }

        if (!gameStarted) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("❓ 20 Questions", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 24.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("How to Play:", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                        Text("1. I will think of something\n2. You have 20 questions (yes/no only)\n3. Try to guess what it is\n4. Win before running out of questions!", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
                    }
                }
                Button(
                    onClick = { gameStarted = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Start Game", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Questions", fontSize = 12.sp, color = Color.Gray)
                    Text("$questionsAsked/20", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Category", fontSize = 12.sp, color = Color.Gray)
                    Text(currentAnswer.category, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Status", fontSize = 12.sp, color = Color.Gray)
                    Text(if (gameOver) "Game Over" else "Playing", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (gameOver) Color.Red else Color.Green)
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AI is thinking of:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    Text("🤫 Something secret", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C6BC0))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(responses.size) { idx ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                        Text(responses[idx], modifier = Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
                    }
                }
            }

            if (!gameOver) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = userGuess,
                    onValueChange = { userGuess = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask a yes/no question...") },
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val response = generateAIResponse(userGuess, currentAnswer.answer)
                            responses = responses + ("Q: $userGuess\nA: $response")
                            questionsAsked++
                            userGuess = ""
                            if (questionsAsked >= 20) gameOver = true
                        },
                        modifier = Modifier.weight(1f),
                        enabled = userGuess.isNotBlank()
                    ) {
                        Text("Ask", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (userGuess.trim().uppercase() == currentAnswer.answer.uppercase()) {
                                playerWon = true
                                responses = responses + ("🎉 Correct! It was ${currentAnswer.answer}!")
                            } else {
                                responses = responses + ("❌ Wrong! It was ${currentAnswer.answer}")
                            }
                            gameOver = true
                            userGuess = ""
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))
                    ) {
                        Text("Guess", color = Color.White)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp).background(
                        if (playerWon) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        RoundedCornerShape(12.dp)
                    ),
                    colors = CardDefaults.cardColors(Color.Transparent)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (playerWon) "🎉 You Won!" else "😢 Game Over", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (playerWon) Color(0xFF2E7D32) else Color(0xFFC62828))
                        Text("The answer was: ${currentAnswer.answer}", fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
                        Text("Questions used: $questionsAsked/20", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                        Button(
                            onClick = {
                                gameStarted = false
                                gameOver = false
                                playerWon = false
                                questionsAsked = 0
                                responses = emptyList()
                                userGuess = ""
                                currentAnswer = questions.random()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFF5C6BC0))
                        ) {
                            Text("Play Again", color = Color.White)
                        }
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(Color(0xFF90A4AE))) {
                            Text("Back to Games", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun generateAIResponse(question: String, answer: String): String {
    val lowerQ = question.lowercase()
    val lowerA = answer.lowercase()

    return when {
        lowerQ.contains("is") && (lowerQ.contains(lowerA) || lowerQ.contains(answer)) -> "Yes ✓"
        lowerQ.contains("is") && lowerQ.contains("animal") && (lowerA in listOf("elephant", "penguin")) -> "Yes ✓"
        lowerQ.contains("is") && lowerQ.contains("food") && lowerA == "pizza" -> "Yes ✓"
        lowerQ.contains("live") || lowerQ.contains("habitat") -> if (lowerA == "penguin") "Antarctic" else "Various places"
        lowerQ.contains("color") -> "Multiple colors" 
        lowerQ.contains("size") || lowerQ.contains("big") -> if (lowerA == "elephant") "Yes, very large" else "Varies"
        lowerQ.contains("eat") || lowerQ.contains("food") -> "It depends on what it is"
        else -> if (Random.nextBoolean()) "Yes ✓" else "No ✗"
    }
}
