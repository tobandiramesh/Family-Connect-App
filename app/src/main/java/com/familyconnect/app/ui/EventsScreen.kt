package com.familyconnect.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.util.Log
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
    
    // Get creator name from phone number
    val creatorName = allowedUsers.find { it.mobile.trim() == event.createdBy.trim() }?.name ?: event.createdBy

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
                    Text(
                        "👤 by $creatorName",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF888888)
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
            
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onMarkComplete(event) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Complete", fontSize = 12.sp, color = Color.White)
                }
                Button(
                    onClick = { onDelete(event) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFe53935)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun EventCreationDialog(
    viewModel: FamilyViewModel,
    allowedUsers: List<UserProfile>,  // Back to allowedUsers (converted from Firebase AllowedUser)
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
    
    // Collapsible section states
    var expandEventInfo by remember { mutableStateOf(true) }
    var expandEventDetails by remember { mutableStateOf(false) }
    var expandInviteMembers by remember { mutableStateOf(false) }

    // Update calendar whenever selectedDate changes
    LaunchedEffect(selectedDate) {
        // Calendar is referenced by the pickers, no need to update explicitly
    }

    if (showDatePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                calendar.set(Calendar.HOUR_OF_DAY, selectedTime.first)
                calendar.set(Calendar.MINUTE, selectedTime.second)
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
                val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                selectedTime = Pair(hour, minute)
                selectedDate = calendar.timeInMillis
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

                // ===== SECTION 1: EVENT INFO =====
                CollapsibleSection(
                    title = "📌 Event Info",
                    expanded = expandEventInfo,
                    onToggle = { expandEventInfo = it }
                ) {
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category
                    var showCategoryDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            readOnly = false,
                            trailingIcon = { 
                                Icon(
                                    Icons.Default.ArrowDropDown, 
                                    contentDescription = null,
                                    modifier = Modifier.clickable { showCategoryDropdown = !showCategoryDropdown }
                                ) 
                            }
                        )
                        DropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth(0.95f)
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

                    Spacer(modifier = Modifier.height(12.dp))

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
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { Icon(Icons.Default.Edit, contentDescription = "Tap to change", modifier = Modifier.size(18.dp).clickable { showDatePicker = true }) }
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
                            leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { Icon(Icons.Default.Edit, contentDescription = "Tap to change", modifier = Modifier.size(18.dp).clickable { showTimePicker = true }) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Recurring
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🔁 Repeats every year")
                        Switch(checked = recurring, onCheckedChange = { recurring = it })
                    }
                }

                // ===== SECTION 2: EVENT DETAILS =====
                CollapsibleSection(
                    title = "📝 Event Details",
                    expanded = expandEventDetails,
                    onToggle = { expandEventDetails = it }
                ) {
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

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location (optional)") },
                        placeholder = { Text("e.g., Home, Hospital...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Color tag
                    Text("🎨 Event Color", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reminder
                    var reminderDays by remember { mutableStateOf(1) }
                    var reminderHours by remember { mutableStateOf(0) }
                    var reminderMinutesValue by remember { mutableStateOf(0) }
                    
                    // Calculate total minutes from days, hours, minutes
                    LaunchedEffect(reminderDays, reminderHours, reminderMinutesValue) {
                        reminderMinutes = reminderDays * 1440 + reminderHours * 60 + reminderMinutesValue
                    }
                    
                    Text("Reminder (before event)", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Days
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Days", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            OutlinedTextField(
                                value = reminderDays.toString(),
                                onValueChange = { value ->
                                    value.toIntOrNull()?.let { if (it >= 0) reminderDays = it }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                        
                        // Hours
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hours", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            OutlinedTextField(
                                value = reminderHours.toString(),
                                onValueChange = { value ->
                                    value.toIntOrNull()?.let { if (it in 0..23) reminderHours = it }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                        
                        // Minutes
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Minutes", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            OutlinedTextField(
                                value = reminderMinutesValue.toString(),
                                onValueChange = { value ->
                                    value.toIntOrNull()?.let { if (it in 0..59) reminderMinutesValue = it }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                    Text(
                        "Total: ${getReminderText(reminderMinutes)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // ===== SECTION 3: INVITE FAMILY MEMBERS =====
                CollapsibleSection(
                    title = "👨‍👩‍👧 Invite Family Members",
                    expanded = expandInviteMembers,
                    onToggle = { expandInviteMembers = it }
                ) {
                    // Filter out current user so they can't invite themselves
                    val otherUsers = allowedUsers.filter { it.mobile != viewModel.currentUser?.mobile }
                    
                    if (otherUsers.isEmpty()) {
                        Text(
                            "No family members available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            otherUsers.forEach { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${user.name} (${user.mobile})", style = MaterialTheme.typography.labelMedium)
                                    val isSelected = user.mobile.trim() in selectedMembers
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                selectedMembers = selectedMembers + user.mobile.trim()
                                            } else {
                                                selectedMembers = selectedMembers - user.mobile.trim()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Button
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            return@Button
                        }

                        val calendar = Calendar.getInstance().apply {
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
                            dateTime = calendar.timeInMillis,
                            colorTag = colorTag,
                            category = category,
                            recurring = recurring,
                            reminderMinutes = reminderMinutes,
                            invitedMembers = invitedList,  // Use the list we created above with trim
                            createdBy = viewModel.currentUser?.mobile?.trim() ?: "",  // TRIM creator mobile
                            createdAtEpochMillis = System.currentTimeMillis()
                        )

                        viewModel.addEvent(event)
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

@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!expanded) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // Content
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
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
