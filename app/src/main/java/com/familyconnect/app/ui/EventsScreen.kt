package com.familyconnect.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.familyconnect.app.data.model.FamilyEvent
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventsScreen(viewModel: FamilyViewModel) {
    val allowedUsers by viewModel.allowedUsers.collectAsState(initial = emptyList())
    val events by viewModel.events.collectAsState(initial = emptyList())
    var showCreateEvent by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }

    if (showCreateEvent) {
        EventCreationDialog(
            viewModel = viewModel,
            allowedUsers = allowedUsers,
            onDismiss = { showCreateEvent = false }
        )
    } else {
        EventsListScreen(
            events = events,
            onCreateEvent = { showCreateEvent = true },
            onSelectCategory = { selectedCategory = it }
        )
    }
}

@Composable
fun EventsListScreen(
    events: List<FamilyEvent>,
    onCreateEvent: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "🎉 Family Events",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Button(
                    onClick = onCreateEvent,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Filter chips
            EventCategoryFilter()
        }

        // Events list
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EventNote,
                        contentDescription = "No Events",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No events planned yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Create a new event to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(events) { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
fun EventCategoryFilter() {
    val categories = listOf("Birthday 🎂", "Anniversary 💕", "Grocery 🛒", "Medicine 💊", "Doctor 👨‍⚕️", "Other 📌")
    var selectedCategory by remember { mutableStateOf("") }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(categories.size) { index ->
            FilterChip(
                selected = selectedCategory == categories[index],
                onClick = { selectedCategory = if (selectedCategory == categories[index]) "" else categories[index] },
                label = { Text(categories[index], fontSize = 11.sp) },
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
fun EventCard(event: FamilyEvent) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val eventDate = dateFormat.format(Date(event.dateTime))
    val eventTime = timeFormat.format(Date(event.dateTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title and Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        event.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Color tag
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = getCategoryColor(event.colorTag),
                            shape = RoundedCornerShape(50.dp)
                        )
                )
            }

            // Date & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Date",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "$eventDate at $eventTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF666666)
                )
                if (event.recurring) {
                    Text(
                        "🔁 Repeats",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Description
            if (event.description.isNotBlank()) {
                Text(
                    event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888),
                    maxLines = 2
                )
            }

            // Location
            if (event.location.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        event.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF999999)
                    )
                }
            }

            // Reminder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Reminder",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Reminder: ${getReminderText(event.reminderMinutes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}

@Composable
fun EventCreationDialog(
    viewModel: FamilyViewModel,
    allowedUsers: List<com.familyconnect.app.data.repository.AllowedUser>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Birthday") }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedTime by remember { mutableStateOf(Pair(12, 0)) }  // hour, minute
    var recurring by remember { mutableStateOf(false) }
    var colorTag by remember { mutableStateOf("Blue") }
    var reminderMinutes by remember { mutableStateOf(1440) }  // 1 day default
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }

    if (showDatePicker) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDate = calendar.timeInMillis
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    if (showTimePicker) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                selectedTime = Pair(hour, minute)
                showTimePicker = false
            },
            selectedTime.first,
            selectedTime.second,
            false
        ).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    "Create Event 🎉",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Card 1 - Basics
                Text(
                    "📌 Event Basics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    placeholder = { Text("e.g., Mom's Birthday") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )

                // Category
                var showCategoryDropdown by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        label = { Text("Category") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryDropdown = !showCategoryDropdown },
                        shape = RoundedCornerShape(8.dp),
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        listOf("Birthday", "Anniversary", "Grocery", "Medicine", "Doctor", "Other").forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                // Date & Time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(selectedDate)),
                        onValueChange = {},
                        label = { Text("Date") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true },
                        readOnly = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )

                    OutlinedTextField(
                        value = String.format("%02d:%02d", selectedTime.first, selectedTime.second),
                        onValueChange = {},
                        label = { Text("Time") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showTimePicker = true },
                        readOnly = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                }

                // Recurring
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🔁 Repeats every year")
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Card 2 - People
                Text(
                    "👨‍👩‍👧 Invite Family Members",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allowedUsers.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${user.name} (${user.mobile})", style = MaterialTheme.typography.labelMedium)
                            Checkbox(
                                checked = user.mobile in selectedMembers,
                                onCheckedChange = {
                                    selectedMembers = if (it) {
                                        selectedMembers + user.mobile
                                    } else {
                                        selectedMembers - user.mobile
                                    }
                                }
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Card 3 - Details
                Text(
                    "📝 Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Add event details...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    placeholder = { Text("e.g., Home, Hospital...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Card 4 - Extras
                Text(
                    "🎨 Extras",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Color tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Red", "Blue", "Green", "Yellow", "Purple", "Orange").forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = getCategoryColor(color),
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .clickable { colorTag = color }
                                .then(
                                    if (colorTag == color) {
                                        Modifier.border(3.dp, Color.Black, RoundedCornerShape(50.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }

                // Reminder
                var showReminderDropdown by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = getReminderText(reminderMinutes),
                        onValueChange = {},
                        label = { Text("Reminder") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReminderDropdown = !showReminderDropdown },
                        readOnly = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(
                        expanded = showReminderDropdown,
                        onDismissRequest = { showReminderDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        listOf(
                            15 to "15 minutes before",
                            60 to "1 hour before",
                            240 to "4 hours before",
                            1440 to "1 day before",
                            10080 to "1 week before"
                        ).forEach { (minutes, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    reminderMinutes = minutes
                                    showReminderDropdown = false
                                }
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Bottom Button
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Please enter event title", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, selectedTime.first)
                            set(Calendar.MINUTE, selectedTime.second)
                        }

                        val event = FamilyEvent(
                            id = (Math.random() * 10000).toInt(),
                            title = title,
                            description = description,
                            location = location,
                            dateTime = calendar.timeInMillis,
                            colorTag = colorTag,
                            category = category,
                            recurring = recurring,
                            reminderMinutes = reminderMinutes,
                            invitedMembers = selectedMembers.toList(),
                            createdBy = viewModel.currentUser?.mobile ?: "",
                            createdAtEpochMillis = System.currentTimeMillis()
                        )

                        viewModel.addEvent(event)
                        Toast.makeText(context, "Event created! ✅", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Event", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

fun getCategoryColor(color: String): Color {
    return when (color) {
        "Red" -> Color(0xFFEF5350)
        "Blue" -> Color(0xFF42A5F5)
        "Green" -> Color(0xFF66BB6A)
        "Yellow" -> Color(0xFFFFEE58)
        "Purple" -> Color(0xFFAB47BC)
        "Orange" -> Color(0xFFFF7043)
        else -> Color(0xFF90CAF9)
    }
}

fun getReminderText(minutes: Int): String {
    return when (minutes) {
        15 -> "15 minutes before"
        60 -> "1 hour before"
        240 -> "4 hours before"
        1440 -> "1 day before"
        10080 -> "1 week before"
        else -> "1 day before"
    }
}
