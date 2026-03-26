package com.familyconnect.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familyconnect.app.data.model.FamilyRole
import com.familyconnect.app.ui.theme.FamilyConnectTheme

private enum class HomeTab {
    DASHBOARD,
    CALENDAR,
    TASKS,
    CHAT,
    MEDIA,
    NOTES,
    SETTINGS
}

@Composable
fun FamilyConnectRoot(viewModel: FamilyViewModel) {
    val darkMode by viewModel.darkMode.collectAsState(initial = false)
    FamilyConnectTheme(darkTheme = darkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (viewModel.currentUser == null) {
                AuthScreen(viewModel = viewModel)
            } else {
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun AuthScreen(viewModel: FamilyViewModel) {
    val users by viewModel.users.collectAsState(initial = emptyList())
    var showAdminSetup by remember { mutableStateOf(false) }
    var adminMobile by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    val adminUnlocked = viewModel.isAdminSetupAuthorized(adminMobile, adminPin)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Bandi Family Connect", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Login with a stored family mobile number")

        OutlinedTextField(
            value = viewModel.loginMobile,
            onValueChange = { viewModel.loginMobile = it.filter(Char::isDigit) },
            label = { Text("Mobile Number") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = { viewModel.login() }, modifier = Modifier.fillMaxWidth()) {
            Text("Login")
        }

        viewModel.loginError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Allowed login policy", fontWeight = FontWeight.Bold)
                Text("Only mobile numbers already stored in the app are allowed to log in.")
                Text("New allowed numbers can be added after login from Settings by an admin user.")
            }
        }

        TextButton(onClick = { showAdminSetup = !showAdminSetup }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdminSetup) "Hide Admin Setup" else "Open Admin Setup")
        }

        if (showAdminSetup) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Admin Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Enter an existing admin mobile number and admin PIN to manage allowed login numbers before sign-in.")

                    OutlinedTextField(
                        value = adminMobile,
                        onValueChange = { adminMobile = it.filter(Char::isDigit) },
                        label = { Text("Admin Mobile Number") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { adminPin = it.filter(Char::isDigit) },
                        label = { Text("Admin Setup PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (adminUnlocked) {
                        OutlinedTextField(
                            value = viewModel.registerName,
                            onValueChange = { viewModel.registerName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = viewModel.registerMobile,
                            onValueChange = { viewModel.registerMobile = it.filter(Char::isDigit) },
                            label = { Text("Allowed Mobile Number") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FamilyRole.entries.forEach { role ->
                                FilterChip(
                                    selected = viewModel.registerRole == role,
                                    onClick = { viewModel.registerRole = role },
                                    label = { Text(role.name) }
                                )
                            }
                        }

                        Button(onClick = { viewModel.register() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Store Allowed Number")
                        }

                        viewModel.registerMessage?.let {
                            Text(it)
                        }

                        Text("Stored Mobile Numbers", style = MaterialTheme.typography.titleMedium)
                        users.forEach { user ->
                            Text("${user.name} (${user.role.name}) - ${user.mobile}")
                        }
                    } else {
                        Text(
                            "Use an existing admin mobile number such as 9999999999 and the current setup PIN to unlock setup.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreen(viewModel: FamilyViewModel) {
    val user = viewModel.currentUser ?: return
    var selectedTab by remember { mutableStateOf(HomeTab.DASHBOARD) }
    val canEdit = user.role != FamilyRole.CHILD

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome ${user.name} (${user.role.name})") },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.DASHBOARD,
                    onClick = { selectedTab = HomeTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.CALENDAR,
                    onClick = { selectedTab = HomeTab.CALENDAR },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.CHAT,
                    onClick = { selectedTab = HomeTab.CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.MEDIA,
                    onClick = { selectedTab = HomeTab.MEDIA },
                    icon = { Icon(Icons.Default.PermMedia, contentDescription = "Media") },
                    label = { Text("Media") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.SETTINGS,
                    onClick = { selectedTab = HomeTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                HomeTab.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onOpenCalendar = { selectedTab = HomeTab.CALENDAR },
                    onOpenTasks = { selectedTab = HomeTab.TASKS },
                    onOpenChat = { selectedTab = HomeTab.CHAT },
                    onOpenMedia = { selectedTab = HomeTab.MEDIA },
                    onOpenNotes = { selectedTab = HomeTab.NOTES },
                    onOpenSettings = { selectedTab = HomeTab.SETTINGS }
                )
                HomeTab.CALENDAR -> CalendarScreen(viewModel, canEdit = canEdit)
                HomeTab.TASKS -> TasksScreen(viewModel, canEdit = canEdit)
                HomeTab.CHAT -> ChatScreen(viewModel)
                HomeTab.MEDIA -> MediaScreen(viewModel)
                HomeTab.NOTES -> NotesScreen(viewModel)
                HomeTab.SETTINGS -> SettingsScreen(viewModel, user.role)
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    viewModel: FamilyViewModel,
    onOpenCalendar: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val events by viewModel.events.collectAsState(initial = emptyList())
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val unreadCount = viewModel.unreadMessageCount()
    val latestMessage = viewModel.latestMessage()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Family Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Choose an icon to open chats, events, photos, or settings.")

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile("Chats", Icons.Default.Chat, unreadCount.takeIf { it > 0 }?.toString(), onOpenChat, Modifier.weight(1f))
            DashboardTile("Events", Icons.Default.DateRange, events.size.toString(), onOpenCalendar, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile("Photos", Icons.Default.PermMedia, null, onOpenMedia, Modifier.weight(1f))
            DashboardTile("Tasks", Icons.Default.CheckCircle, tasks.count { !it.completed }.toString(), onOpenTasks, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile("Notes", Icons.Default.Note, notes.size.toString(), onOpenNotes, Modifier.weight(1f))
            DashboardTile("Settings", Icons.Default.Settings, null, onOpenSettings, Modifier.weight(1f))
        }

        if (messages.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Mail, contentDescription = null)
                        Text("Messages after login", fontWeight = FontWeight.Bold)
                    }
                    Text("Unread messages: $unreadCount")
                    latestMessage?.let {
                        Text("Latest: ${it.sender} - ${it.body}")
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Text("Active events", fontWeight = FontWeight.Bold)
                    }
                    events.take(3).forEach { event ->
                        Text("${event.title} | ${event.dateTime} | expires in ${viewModel.eventDaysLeft(event)} day(s)")
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Text("No active events", fontWeight = FontWeight.Bold)
                    }
                    Text("Created events remain visible for 3 days and then expire automatically.")
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(viewModel: FamilyViewModel, canEdit: Boolean) {
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var assignedTo by remember { mutableStateOf("CHILD") }
    var dueDate by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Task & Chore Management", style = MaterialTheme.typography.titleLarge)
        }

        if (canEdit) {
            item {
                OutlinedTextField(title, { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(assignedTo, { assignedTo = it }, label = { Text("Assign to role/user") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reward, { reward = it.filter(Char::isDigit) }, label = { Text("Reward points (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        if (title.isNotBlank() && dueDate.isNotBlank()) {
                            viewModel.addTask(title, assignedTo, dueDate, reward.toIntOrNull())
                            title = ""
                            dueDate = ""
                            reward = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Task") }
            }
        }

        items(tasks) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.title, fontWeight = FontWeight.Bold)
                    Text("Assigned to: ${task.assignedTo}")
                    Text("Due: ${task.dueDate}")
                    Text("Status: ${if (task.completed) "Completed" else "Pending"}")
                    task.rewardPoints?.let { Text("Reward: $it points") }
                    TextButton(onClick = { viewModel.toggleTask(task.id) }) {
                        Text(if (task.completed) "Mark Pending" else "Mark Complete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            if (badge != null) {
                Text(badge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CalendarScreen(viewModel: FamilyViewModel, canEdit: Boolean) {
    val events by viewModel.events.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") }
    var colorTag by remember { mutableStateOf("Blue") }
    var recurring by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf("30") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Shared Family Calendar", style = MaterialTheme.typography.titleLarge)
        }

        if (canEdit) {
            item {
                OutlinedTextField(title, { title = it }, label = { Text("Event title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dateTime, { dateTime = it }, label = { Text("Date & Time (YYYY-MM-DD HH:mm)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(colorTag, { colorTag = it }, label = { Text("Color tag") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reminder, { reminder = it.filter(Char::isDigit) }, label = { Text("Reminder minutes") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recurring")
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                }
                Button(
                    onClick = {
                        if (title.isNotBlank() && dateTime.isNotBlank()) {
                            viewModel.addEvent(title, dateTime, colorTag, recurring, reminder.toIntOrNull() ?: 30)
                            title = ""
                            dateTime = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Event") }
            }
        }

        items(events) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.title, fontWeight = FontWeight.Bold)
                    Text("When: ${event.dateTime}")
                    Text("Color: ${event.colorTag}")
                    Text("Recurring: ${if (event.recurring) "Yes" else "No"}")
                    Text("Reminder: ${event.reminderMinutes} mins")
                    Text("Expires in: ${viewModel.eventDaysLeft(event)} day(s)")
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(viewModel: FamilyViewModel) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    var target by remember { mutableStateOf("Group") }
    var body by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Messaging/Chat", style = MaterialTheme.typography.titleLarge)
            Text("(Firebase chat coming soon - configure google-services.json)")
        }
        item {
            OutlinedTextField(target, { target = it }, label = { Text("Target (Group or name)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (body.isNotBlank()) {
                        viewModel.sendMessage(target, body, null)
                        body = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send") }
        }

        items(messages) { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${message.sender} -> ${message.target}", fontWeight = FontWeight.Bold)
                    Text(message.body)
                    Text("Time: ${message.timestamp}")
                }
            }
        }
    }
}

@Composable
private fun MediaScreen(viewModel: FamilyViewModel) {
    val media by viewModel.media.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Photo") }
    var uri by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Photo & Media Sharing", style = MaterialTheme.typography.titleLarge)
        }
        item {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(type, { type = it }, label = { Text("Type (Photo/Video)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(uri, { uri = it }, label = { Text("Media URI") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (title.isNotBlank() && uri.isNotBlank()) {
                        viewModel.addMedia(title, type, uri)
                        title = ""
                        uri = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Upload") }
        }

        items(media) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text("Type: ${item.mediaType}")
                    Text("By: ${item.uploadedBy}")
                    Text("URI: ${item.uri}")
                    Text("At: ${item.timestamp}")
                }
            }
        }
    }
}

@Composable
private fun NotesScreen(viewModel: FamilyViewModel) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Shared Notes & Lists", style = MaterialTheme.typography.titleLarge)
        }

        item {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { content = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        viewModel.addNote(title, content)
                        title = ""
                        content = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Note") }
        }

        items(notes) { note ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(note.title, fontWeight = FontWeight.Bold)
                    Text(note.content)
                    Text("Edited by ${note.editedBy} at ${note.editedAt}")
                }
            }
        }
    }
}


@Composable
private fun SettingsScreen(viewModel: FamilyViewModel, role: FamilyRole) {
    val darkMode by viewModel.darkMode.collectAsState(initial = false)
    val language by viewModel.language.collectAsState(initial = "en")
    val largeText by viewModel.largeText.collectAsState(initial = false)
    val adminSetupPin by viewModel.adminSetupPin.collectAsState(initial = "2468")
    val users by viewModel.users.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dark mode")
            Switch(checked = darkMode, onCheckedChange = { viewModel.setDarkMode(it) })
        }

        Text("Language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = language == "en", onClick = { viewModel.setLanguage("en") }, label = { Text("English") })
            FilterChip(selected = language == "es", onClick = { viewModel.setLanguage("es") }, label = { Text("Spanish") })
            FilterChip(selected = language == "hi", onClick = { viewModel.setLanguage("hi") }, label = { Text("Hindi") })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Accessibility: Large text")
            Switch(checked = largeText, onCheckedChange = { viewModel.setLargeText(it) })
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Text("Role: ${role.name}", fontWeight = FontWeight.Bold)
                }
                Text("Notifications are enabled for events, tasks, and messages.")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, contentDescription = null)
                    Text("Allowed Mobile Numbers", fontWeight = FontWeight.Bold)
                }
                users.forEach { user ->
                    Text("${user.name} (${user.role.name}) - ${user.mobile}")
                }
            }
        }

        if (role == FamilyRole.ADMIN) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Allowed User", fontWeight = FontWeight.Bold)
                    Text("Current pre-login admin setup PIN: $adminSetupPin")
                    OutlinedTextField(
                        value = viewModel.registerName,
                        onValueChange = { viewModel.registerName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = viewModel.registerMobile,
                        onValueChange = { viewModel.registerMobile = it.filter(Char::isDigit) },
                        label = { Text("Mobile Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FamilyRole.entries.forEach { entry ->
                            FilterChip(
                                selected = viewModel.registerRole == entry,
                                onClick = { viewModel.registerRole = entry },
                                label = { Text(entry.name) }
                            )
                        }
                    }
                    Button(onClick = { viewModel.register() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Store Allowed Number")
                    }
                    viewModel.registerMessage?.let { Text(it) }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Change Admin Setup PIN", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = viewModel.newAdminSetupPin,
                        onValueChange = { viewModel.newAdminSetupPin = it.filter(Char::isDigit) },
                        label = { Text("New PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = viewModel.confirmAdminSetupPin,
                        onValueChange = { viewModel.confirmAdminSetupPin = it.filter(Char::isDigit) },
                        label = { Text("Confirm New PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { viewModel.updateAdminSetupPin() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Update Setup PIN")
                    }
                    viewModel.adminPinMessage?.let { Text(it) }
                }
            }
        }
    }
}
