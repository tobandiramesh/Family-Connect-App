package com.familyconnect.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.familyconnect.app.data.model.FamilyEvent
import com.familyconnect.app.data.model.UserProfile
import com.familyconnect.app.data.model.FamilyRole
import com.familyconnect.app.data.repository.AllowedUser
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventsScreen(viewModel: FamilyViewModel) {
    val allowedUsers by viewModel.allowedUsers.collectAsState(initial = emptyList())
    val events by viewModel.events.collectAsState(initial = emptyList())
    val context = LocalContext.current
    
    // DEBUG: Show total events
    LaunchedEffect(events.size) {
        Log.d("EventsScreen", "🎉 EVENTS UPDATED: Total = ${events.size}")
    }
    
    var showCreateEvent by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }

    if (showCreateEvent) {
        EventCreationDialog(
            viewModel = viewModel,
            allowedUsers = allowedUsers.map { UserProfile(id = 0, name = it.name, mobile = it.mobile, role = FamilyRole.valueOf(it.role)) },
            onDismiss = { showCreateEvent = false }
        )
    } else {
        EventsListScreen(
            events = events,
            allowedUsers = allowedUsers,
            onCreateEvent = { showCreateEvent = true },
            onSelectCategory = { selectedCategory = it },
            onDeleteEvent = { viewModel.deleteEvent(it) },
            onMarkEventComplete = { viewModel.markEventComplete(it) }
        )
    }
}

@Composable
fun EventsListScreen(
    events: List<FamilyEvent>,
    allowedUsers: List<AllowedUser>,
    onCreateEvent: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onDeleteEvent: (FamilyEvent) -> Unit = {},
    onMarkEventComplete: (FamilyEvent) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        // Header - Clean and modern
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(14.dp, 12.dp, 14.dp, 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Family Events",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A),
                    fontSize = 22.sp
                )
                FloatingActionButton(
                    onClick = onCreateEvent,
                    modifier = Modifier.size(44.dp),
                    containerColor = Color(0xFF5C6BC0),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event", tint = Color.White, modifier = Modifier.size(24.dp))
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
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No events planned yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF666666),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Create a new event to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF999999)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(events) { event ->
                    EventCard(
                        event,
                        allowedUsers = allowedUsers,
                        onDelete = { onDeleteEvent(it) },
                        onMarkComplete = { onMarkEventComplete(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun EventCategoryFilter() {
    val categories = listOf("Birthday", "Anniversary", "Grocery", "Medicine", "Doctor", "Other")
    var selectedCategory by remember { mutableStateOf("") }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories.size) { index ->
            val isSelected = selectedCategory == categories[index]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) Color(0xFF5C6BC0) else Color(0xFFF0F0F0)
                    )
                    .clickable {
                        selectedCategory = if (isSelected) "" else categories[index]
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categories[index],
                    color = if (isSelected) Color.White else Color(0xFF1A1A1A),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun EventCard(
    event: FamilyEvent,
    allowedUsers: List<AllowedUser> = emptyList(),
    onDelete: (FamilyEvent) -> Unit = {},
    onMarkComplete: (FamilyEvent) -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val eventDate = dateFormat.format(Date(event.dateTime))
    val eventTime = timeFormat.format(Date(event.dateTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Title and Status dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1A1A),
                            fontSize = 15.sp
                        )
                        // Status indicator dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF4CAF50), shape = RoundedCornerShape(50.dp))
                        )
                    }
                    Text(
                        event.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF5C6BC0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Compact color tag (hidden from main view, smaller)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = getCategoryColor(event.colorTag),
                            shape = RoundedCornerShape(50.dp)
                        )
                )
            }

            // Date & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Date",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "$eventDate at $eventTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF666666),
                    fontSize = 12.sp
                )
                if (event.recurring) {
                    Text(
                        "Repeats",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF5C6BC0),
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                }
            }

            // Description (if exists)
            if (event.description.isNotBlank()) {
                Text(
                    event.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF888888),
                    fontSize = 13.sp,
                    maxLines = 2
                )
            }

            // Location (if exists)
            if (event.location.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        event.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF999999),
                        fontSize = 12.sp
                    )
                }
            }

            // Reminder (if set)
            if (event.reminderMinutes > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Reminder",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "Reminder: ${getReminderText(event.reminderMinutes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF999999),
                        fontSize = 11.sp
                    )
                }
            }
            
            // Action buttons - Small modern icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Complete button - small icon
                IconButton(
                    onClick = { onMarkComplete(event) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Complete",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // Delete button - small icon
                IconButton(
                    onClick = { onDelete(event) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EventCreationDialog(
    viewModel: FamilyViewModel,
    allowedUsers: List<UserProfile>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Birthday") }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedTime by remember { mutableStateOf(Pair(12, 0)) }
    var recurring by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf(1440) }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showMembersBottomSheet by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                cal.set(year, month, dayOfMonth)
                cal.set(Calendar.HOUR_OF_DAY, selectedTime.first)
                cal.set(Calendar.MINUTE, selectedTime.second)
                selectedDate = cal.timeInMillis
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
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                selectedTime = Pair(hour, minute)
                selectedDate = cal.timeInMillis
                showTimePicker = false
            },
            selectedTime.first,
            selectedTime.second,
            false
        ).show()
    }

    // Invite Members Bottom Sheet
    if (showMembersBottomSheet) {
        InviteMembersBottomSheet(
            allowedUsers = allowedUsers,
            selectedMembers = selectedMembers,
            viewModel = viewModel,
            onMembersSelected = { selectedMembers = it },
            onDismiss = { showMembersBottomSheet = false }
        )
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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Text(
                    "Create Event 🎉",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1A1A1A)
                )

                // TITLE - Auto-focus
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    placeholder = { Text("e.g., Mom's Birthday") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF5C6BC0)) }
                )

                // CATEGORY - Chips instead of dropdown
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF666666))
                CategorySelector(
                    selectedCategory = category,
                    onCategorySelected = { category = it }
                )

                // DATE & TIME - Side by side buttons
                Text("Date & Time", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF666666))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFFDDDDDD)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF5C6BC0))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(selectedDate)),
                            fontSize = 13.sp,
                            color = Color(0xFF1A1A1A)
                        )
                    }

                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFFDDDDDD)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF5C6BC0))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            String.format("%02d:%02d", selectedTime.first, selectedTime.second),
                            fontSize = 13.sp,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                }

                // REPEAT - Simple toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Repeat yearly", fontSize = 13.sp, color = Color(0xFF1A1A1A))
                    Switch(
                        checked = recurring,
                        onCheckedChange = { recurring = it },
                        modifier = Modifier.scale(0.85f)
                    )
                }

                // DESCRIPTION
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Add event details...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )

                // LOCATION
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    placeholder = { Text("e.g., Home, Hospital...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF5C6BC0)) }
                )

                // REMINDER - Simple dropdown
                ReminderDropdown(
                    selectedMinutes = reminderMinutes,
                    onReminderSelected = { reminderMinutes = it }
                )

                // INVITE MEMBERS - Button to open bottom sheet
                Text(
                    "Invite Members",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                        .clickable { showMembersBottomSheet = true }
                        .padding(12.dp),
                    fontSize = 13.sp,
                    color = Color(0xFF1A1A1A),
                    fontWeight = FontWeight.SemiBold
                )

                // Show selected members count
                if (selectedMembers.isNotEmpty()) {
                    Text(
                        "✓ ${selectedMembers.size} member${if (selectedMembers.size > 1) "s" else ""} invited",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // CREATE BUTTON
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Please enter event title", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val cal = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, selectedTime.first)
                            set(Calendar.MINUTE, selectedTime.second)
                        }

                        val invitedList = selectedMembers.map { it.trim() }.toList()
                        val event = FamilyEvent(
                            id = (Math.random() * 10000).toInt(),
                            title = title,
                            description = description,
                            location = location,
                            dateTime = cal.timeInMillis,
                            colorTag = "Blue",
                            category = category,
                            recurring = recurring,
                            reminderMinutes = reminderMinutes,
                            invitedMembers = invitedList,
                            createdBy = viewModel.currentUser?.mobile?.trim() ?: "",
                            createdAtEpochMillis = System.currentTimeMillis()
                        )

                        viewModel.addEvent(event)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5C6BC0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Event", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }

                // CANCEL BUTTON
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    border = BorderStroke(1.dp, Color(0xFFDDDDDD)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = Color(0xFF666666), fontSize = 15.sp)
                }
            }
        }
    }
}

// NEW COMPOSABLES FOR MODERN EVENT CREATION

@Composable
private fun CategorySelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf(
        "🎂 Birthday",
        "💍 Anniversary",
        "🛒 Grocery",
        "💊 Medicine",
        "⚕️ Doctor",
        "📝 Other"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { cat ->
            Surface(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCategorySelected(cat) },
                color = if (selectedCategory == cat) Color(0xFF5C6BC0) else Color(0xFFF0F0F0),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        cat,
                        fontSize = 12.sp,
                        color = if (selectedCategory == cat) Color.White else Color(0xFF1A1A1A),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderDropdown(
    selectedMinutes: Int,
    onReminderSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val reminderOptions = listOf(
        Pair(10, "10 minutes"),
        Pair(30, "30 minutes"),
        Pair(60, "1 hour"),
        Pair(1440, "1 day"),
        Pair(2880, "2 days")
    )

    val selectedLabel = reminderOptions.find { it.first == selectedMinutes }?.second ?: "1 day"

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF0F0F0))
                .clickable { expanded = true }
                .padding(12.dp),
            color = Color.Transparent
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF5C6BC0),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        selectedLabel,
                        fontSize = 13.sp,
                        color = Color(0xFF1A1A1A),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            reminderOptions.forEach { (minutes, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = {
                        onReminderSelected(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMembersBottomSheet(
    allowedUsers: List<UserProfile>,
    selectedMembers: Set<String>,
    viewModel: FamilyViewModel,
    onMembersSelected: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var members by remember { mutableStateOf(selectedMembers) }
    var searchQuery by remember { mutableStateOf("") }

    // Filter out current user (trim both for comparison)
    val currentUserMobile = viewModel.currentUser?.mobile?.trim() ?: ""
    val otherUsers = allowedUsers.filter { it.mobile.trim() != currentUserMobile }
    
    Log.d("InviteMembersBS", "Current mobile: '$currentUserMobile', Total users: ${allowedUsers.size}, Other users (filtered): ${otherUsers.size}")
    
    val filteredUsers = if (searchQuery.isEmpty()) {
        otherUsers
    } else {
        otherUsers.filter { it.name.contains(searchQuery, ignoreCase = true) || it.mobile.contains(searchQuery) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                "Invite Family Members",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search members...", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF5C6BC0)) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Members list
            if (filteredUsers.isEmpty()) {
                Text(
                    "No family members available",
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredUsers) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
                                .clickable {
                                    members = if (user.mobile in members) {
                                        members - user.mobile
                                    } else {
                                        members + user.mobile
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A1A1A)
                                )
                                Text(
                                    user.mobile,
                                    fontSize = 11.sp,
                                    color = Color(0xFF999999)
                                )
                            }
                            Checkbox(
                                checked = user.mobile in members,
                                onCheckedChange = {
                                    members = if (user.mobile in members) {
                                        members - user.mobile
                                    } else {
                                        members + user.mobile
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Done button
            Button(
                onClick = {
                    onMembersSelected(members)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Done (${members.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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
    return when {
        minutes < 60 -> "$minutes minutes"
        minutes < 1440 -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) "$hours hour${if (hours > 1) "s" else ""}"
            else "$hours hour${if (hours > 1) "s" else ""} $mins min${if (mins > 1) "s" else ""}"
        }
        else -> {
            val days = minutes / 1440
            val remainingMinutes = minutes % 1440
            val hours = remainingMinutes / 60
            val mins = remainingMinutes % 60
            
            val parts = mutableListOf<String>()
            if (days > 0) parts.add("$days day${if (days > 1) "s" else ""}")
            if (hours > 0) parts.add("$hours hour${if (hours > 1) "s" else ""}")
            if (mins > 0) parts.add("$mins min${if (mins > 1) "s" else ""}")
            
            parts.joinToString(" ")
        }
    }
}
