package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.LocalMemory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MemoryViewModel = viewModel()
                
                // Seed data automatically on first run to keep the dashboard packed and gorgeous
                LaunchedEffect(Unit) {
                    viewModel.seedSampleDataIfEmpty()
                }

                // WindowInsets padding safe drawing support
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    MainNavigationHub(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Sub-nav screen tags
enum class VaultScreen {
    DASHBOARD,
    CREATOR,
    TIMELINE,
    ACADEMIC,
    FAMILY,
    SECURITY
}

@Composable
fun MainNavigationHub(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    val isKidsMode by viewModel.isKidsMode.collectAsState()
    val isScreenLocked by viewModel.isScreenLocked.collectAsState()

    // Animation transition when switching modes
    AnimatedContent(
        targetState = isKidsMode,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
        },
        label = "mode_transition"
    ) { kidsActive ->
        if (kidsActive) {
            KidsModeWorkspace(viewModel = viewModel, modifier = modifier)
        } else {
            if (isScreenLocked) {
                SecurityLockGate(viewModel = viewModel, modifier = modifier)
            } else {
                StandardVaultWorkspace(viewModel = viewModel, modifier = modifier)
            }
        }
    }
}

// ==========================================
// 1. STANDARD WORKSPACE (PRO ADULT DESIGN)
// ==========================================
@Composable
fun StandardVaultWorkspace(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(VaultScreen.DASHBOARD) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))) // Deep Cosmic Slate
    ) {
        // App header bar
        ProfileHeaderSection(viewModel = viewModel)

        // Horizontal visual tabs row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TabChip(
                    text = "Dashboard",
                    icon = Icons.Default.Home,
                    isSelected = currentScreen == VaultScreen.DASHBOARD,
                    onClick = { currentScreen = VaultScreen.DASHBOARD }
                )
            }
            item {
                TabChip(
                    text = "Journal Lab",
                    icon = Icons.Default.Edit,
                    isSelected = currentScreen == VaultScreen.CREATOR,
                    onClick = { currentScreen = VaultScreen.CREATOR }
                )
            }
            item {
                TabChip(
                    text = "Timeline",
                    icon = Icons.Default.List,
                    isSelected = currentScreen == VaultScreen.TIMELINE,
                    onClick = { currentScreen = VaultScreen.TIMELINE }
                )
            }
            item {
                TabChip(
                    text = "Academic & Travel",
                    icon = Icons.Default.Star,
                    isSelected = currentScreen == VaultScreen.ACADEMIC,
                    onClick = { currentScreen = VaultScreen.ACADEMIC }
                )
            }
            item {
                TabChip(
                    text = "Family Vault",
                    icon = Icons.Default.Share,
                    isSelected = currentScreen == VaultScreen.FAMILY,
                    onClick = { currentScreen = VaultScreen.FAMILY }
                )
            }
            item {
                TabChip(
                    text = "Locker",
                    icon = Icons.Default.Lock,
                    isSelected = currentScreen == VaultScreen.SECURITY,
                    onClick = { currentScreen = VaultScreen.SECURITY }
                )
            }
        }

        // Action view mapping
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (currentScreen) {
                VaultScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel, onNavigateToCreator = { currentScreen = VaultScreen.CREATOR })
                VaultScreen.CREATOR -> CreatorScreen(viewModel = viewModel)
                VaultScreen.TIMELINE -> TimelineScreen(viewModel = viewModel)
                VaultScreen.ACADEMIC -> AcademicTravelScreen(viewModel = viewModel)
                VaultScreen.FAMILY -> FamilyVaultScreen(viewModel = viewModel)
                VaultScreen.SECURITY -> SecurityVaultScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ProfileHeaderSection(viewModel: MemoryViewModel) {
    val guestUser by viewModel.guestUser.collectAsState()
    val isGoogleSignedIn by viewModel.isGoogleSignedIn.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = guestUser.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = guestUser,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    Text(
                        text = if (isGoogleSignedIn) "Synced to Cloud" else "Local / Offline First Active",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Sign-In Simulate Toggle
                IconButton(
                    onClick = {
                        if (isGoogleSignedIn) {
                            viewModel.googleSignOutSimulate()
                        } else {
                            viewModel.googleSignInSimulate()
                        }
                    },
                    modifier = Modifier.background(Color(0xFF334155), CircleShape).size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isGoogleSignedIn) Icons.Default.Person else Icons.Default.PlayArrow,
                        contentDescription = "Sign In Toggle",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Lock Screen Quickly
                IconButton(
                    onClick = { viewModel.lockScreen() },
                    modifier = Modifier.background(Color(0xFF334155), CircleShape).size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock private session",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Launch Kids Mode Interactive Transition
                Button(
                    onClick = { viewModel.toggleKidsMode() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Kids Mode",
                        tint = Color(0xFF78350F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kids", color = Color(0xFF78350F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TabChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

// ==========================================
// 2. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(
    viewModel: MemoryViewModel,
    onNavigateToCreator: () -> Unit
) {
    val memories by viewModel.allMemories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isRecordingPaused by viewModel.isRecordingPaused.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()

    var showSlideshow by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Visual Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(32.dp))
            ) {
                // Generated Hero Art Background
                Image(
                    painter = painterResource(id = com.example.R.drawable.img_hero_banner_1781895824534),
                    contentDescription = "Cosmic Memory Vault Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Overlay gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                startY = 100f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = "LifeVault AI",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        style = TextStyle(letterSpacing = 1.sp)
                    )
                    Text(
                        text = "The world's most secure local-first AI memory assistant",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                }

                // AI Highlight Recap Play button (uses Star icon for visual polish)
                Button(
                    onClick = { showSlideshow = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Highlight Film", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }
            }
        }

        // Natural Language AI Search
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Search memories with Local AI...", color = Color(0xFF64748B)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("natural_ai_search"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedLabelColor = Color(0xFF3B82F6),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = Color(0xFF3B82F6)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear search", tint = Color(0xFFEF4444))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Voice First Microphone Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Press & Speak to Journal",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Your narration will transcribe, analyze emotions & summarize instantly offline",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isRecording) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .clickable { viewModel.pauseRecording() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Star,
                                    contentDescription = "Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "00:${String.format("%02d", recordingDuration)}",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = if (isRecordingPaused) "Paused" else "Recording Live...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = { viewModel.stopAndSaveVoiceRecording("Voice Memoir", "CALM") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.cancelRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("mic_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Voice Entry", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }

        // On This Day (Google Photos Style Flashbacks)
        val flashbacks = memories.filter { memory ->
            // Simulating anniversary comparison (matching day indices or randomly showcasing some historical tags)
            memory.id % 2L == 0L || memory.tagsAsString.contains("Cosmic")
        }
        if (flashbacks.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flashbacks: On This Day",
                            color = Color(0xFF60A5FA),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24))
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(flashbacks) { memory ->
                            FlashbackCard(memory = memory)
                        }
                    }
                }
            }
        }

        // Mood Analytics Visual Graph
        val happyCount = memories.count { it.mood == "HAPPY" }
        val sadCount = memories.count { it.mood == "SAD" }
        val excitedCount = memories.count { it.mood == "EXCITED" }
        val calmCount = memories.count { it.mood == "CALM" }
        val totalCounts = memories.size.coerceAtLeast(1)

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Mood Analytics & Insights",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    MoodProgressBar(moodName = "😊 Happy / Excited", count = happyCount + excitedCount, total = totalCounts, barColor = Color(0xFF10B981))
                    MoodProgressBar(moodName = "😴 Tired / Calm", count = calmCount, total = totalCounts, barColor = Color(0xFF3B82F6))
                    MoodProgressBar(moodName = "😢 Sad / Gloomy", count = sadCount, total = totalCounts, barColor = Color(0xFFEF4444))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your emotional frequency centers mostly on peace and focus.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }

        // Recent memories list matched with Search Query
        val filteredMemories = memories.filter {
            searchQuery.isBlank() ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true) ||
            it.tagsAsString.contains(searchQuery, ignoreCase = true) ||
            it.peopleAsString.contains(searchQuery, ignoreCase = true)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "Search Results (${filteredMemories.size})" else "Recent Vault Entries",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (searchQuery.isBlank()) {
                    Button(
                        onClick = onNavigateToCreator,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+ Add New", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (filteredMemories.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Memories Found", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Seed mock data at the profile top-right or add a new journal entry to start!", color = Color(0xFF94A3B8), fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(filteredMemories) { memory ->
                MemoryItemRow(memory = memory, viewModel = viewModel)
            }
        }
    }

    if (showSlideshow) {
        SlideshowDialog(memories = memories) { showSlideshow = false }
    }
}

@Composable
fun FlashbackCard(memory: LocalMemory) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .height(130.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Exactly 1 Year Ago",
                    color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = memory.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = memory.content,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${memory.mood} • ${memory.location.ifBlank { "Anywhere" }}",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun MoodProgressBar(moodName: String, count: Int, total: Int, barColor: Color) {
    val progressFraction = count.toFloat() / total.toFloat()

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = moodName, color = Color(0xFFE2E8F0), fontSize = 12.sp)
            Text(text = "$count entries (${(progressFraction * 100).toInt()}%)", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = barColor,
            trackColor = Color(0xFF334155)
        )
    }
}

// ==========================================
// 3. MEMORY ITEM ROW
// ==========================================
@Composable
fun MemoryItemRow(
    memory: LocalMemory,
    viewModel: MemoryViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("memory_item_card_${memory.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (memory.type) {
                        "DREAM" -> Color(0xFF8B5CF6)
                        "GRATITUDE" -> Color(0xFF10B981)
                        "ACADEMIC" -> Color(0xFF3B82F6)
                        "CAPSULE" -> Color(0xFFFBBF24)
                        "VOICE" -> Color(0xFFEF4444)
                        "DRAWING" -> Color(0xFFEC4899)
                        else -> Color(0xFF6366F1)
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = memory.type, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = memory.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { viewModel.togglePin(memory) }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Pin entry",
                            tint = if (memory.isPinned) Color(0xFFFBBF24) else Color(0xFF475569)
                        )
                    }

                    IconButton(onClick = { viewModel.deleteMemory(memory) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (memory.isCapsuleLocked) "🔐 Time Capsule Locked! Date scheduled: Unlocking soon" else memory.content,
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (memory.isCapsuleLocked) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This memory will open at the specified capsule date.",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Expanded detail segment for stories of childhood/chapters and tag lists
            if (expanded && !memory.isCapsuleLocked) {
                Spacer(modifier = Modifier.height(12.dp))

                if (memory.tags.isNotEmpty()) {
                    Text(text = "Tags", color = Color(0xFF64748B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        memory.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(text = "#$tag", color = Color(0xFF60A5FA), fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (memory.people.isNotEmpty()) {
                    Text(text = "People Mentioned", color = Color(0xFF64748B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        memory.people.forEach { person ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF334155), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = "👤 $person", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (memory.aiSummary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI Story & Emotional Insight", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = memory.aiSummary, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.generateAIStory(memory, "CHILDREN_STORY") },
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Draft AI Children's Story Segment", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val dateFormat = SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault())
            Text(
                text = dateFormat.format(Date(memory.timestamp)) + if (memory.location.isNotEmpty()) " • 📍 ${memory.location}" else "",
                color = Color(0xFF475569),
                fontSize = 11.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==========================================
// 4. CREATOR SCREEN (MULTIPLE JOURNAL TYPES)
// ==========================================
@Composable
fun CreatorScreen(viewModel: MemoryViewModel) {
    var type by remember { mutableStateOf("JOURNAL") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("HAPPY") }
    var location by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("") }
    var travelTripName by remember { mutableStateOf("") }
    var capsuleUnlockInDays by remember { mutableStateOf(30) }
    var isPinned by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "New Memory Lab",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }

        // Segment selection row utilizing item block
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("JOURNAL", "VOICE", "DREAM", "ACADEMIC", "TRAVEL", "GRATITUDE", "CAPSULE")) { itemType ->
                    val isSelected = type == itemType
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { type = itemType },
                        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155))
                    ) {
                        Text(
                            text = itemType,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Form fields
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title of memory", color = Color(0xFF64748B)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        item {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Write details, thoughts or descriptions...", color = Color(0xFF64748B)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 8
            )
        }

        // Mood selection emoji triggers
        item {
            Column {
                Text("Select Current Emotional Resonance:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val moods = listOf(
                        "HAPPY" to "😊 Happy",
                        "SAD" to "😢 Sad",
                        "EXCITED" to "🤩 Excited",
                        "CALM" to "🧘 Calm",
                        "TIRED" to "😴 Tired",
                        "ANGRY" to "😡 Angry"
                    )
                    moods.forEach { (mCode, mLabel) ->
                        val isSel = mood == mCode
                        Box(
                            modifier = Modifier
                                .background(if (isSel) Color(0xFF3B82F6) else Color(0xFF334155), RoundedCornerShape(12.dp))
                                .clickable { mood = mCode }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(text = mLabel.take(2), fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        // Metadata inputs
        item {
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (e.g., Paris, Kitchen, Forest)", color = Color(0xFF64748B)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        item {
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated, e.g. Cosmic, Happy)", color = Color(0xFF64748B)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        item {
            OutlinedTextField(
                value = people,
                onValueChange = { people = it },
                label = { Text("People mentioned (comma separated, e.g. Elena, Marcus)", color = Color(0xFF64748B)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Travel Specific properties
        if (type == "TRAVEL") {
            item {
                OutlinedTextField(
                    value = travelTripName,
                    onValueChange = { travelTripName = it },
                    label = { Text("Trip Group (e.g. Summer Vacation, Tokyo)", color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        }

        // Capsule specific settings
        if (type == "CAPSULE") {
            item {
                Column {
                    Text("Time Capsule Locked Interval (Days to Unlock):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(7, 30, 90, 365).forEach { days ->
                            val isSelCapsule = capsuleUnlockInDays == days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelCapsule) Color(0xFF3B82F6) else Color(0xFF334155), RoundedCornerShape(12.dp))
                                    .clickable { capsuleUnlockInDays = days }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (days < 365) "$days Days" else "1 Year",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pinned configuration
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pin in Memory Vault (Keeps core moments immortalized):", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = isPinned,
                    onCheckedChange = { isPinned = it }
                )
            }
        }

        // Submit Button
        item {
            Button(
                onClick = {
                    if (title.isBlank() && content.isBlank()) {
                        Toast.makeText(context, "Please write some details or title first!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.addMemory(
                        type = type,
                        title = title,
                        content = content,
                        mood = mood,
                        location = location,
                        tags = tags,
                        people = people,
                        capsuleUnlockInDays = if (type == "CAPSULE") capsuleUnlockInDays else 0,
                        isPinned = isPinned,
                        tripName = travelTripName
                    )
                    Toast.makeText(context, "Memory safely committed offline!", Toast.LENGTH_SHORT).show()

                    // Reset
                    title = ""
                    content = ""
                    location = ""
                    tags = ""
                    people = ""
                    travelTripName = ""
                    isPinned = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp)
                    .testTag("save_memory_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Seal Inside Vault Securely", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ==========================================
// 5. TIMELINE SCREEN (YEAR -> MONTH -> DAY)
// ==========================================
@Composable
fun TimelineScreen(viewModel: MemoryViewModel) {
    val memories by viewModel.allMemories.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Interactive Life Timeline",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Time filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "GRATITUDE", "CAPSULE", "VOICE", "DREAM").forEach { filter ->
                val active = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = filter, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val displayedTimeline = memories.filter { selectedFilter == "ALL" || it.type == selectedFilter }

        if (displayedTimeline.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No entries in this timeline sweep.", color = Color(0xFF64748B))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(displayedTimeline) { memory ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Left Date vertical indicator segment
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
                            val sdfMonth = SimpleDateFormat("MMM", Locale.getDefault())
                            val sdfDay = SimpleDateFormat("dd", Locale.getDefault())

                            Text(text = sdfYear.format(Date(memory.timestamp)), color = Color(0xFF3B82F6), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text(text = sdfMonth.format(Date(memory.timestamp)), color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = sdfDay.format(Date(memory.timestamp)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Dot visual connector
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(60.dp)
                                    .background(Color(0xFF334155))
                            )
                        }

                        // Right Card segment
                        Box(modifier = Modifier.weight(1f)) {
                            MemoryItemRow(memory = memory, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. ACADEMIC & TRAVEL JOURNEYS
// ==========================================
@Composable
fun AcademicTravelScreen(viewModel: MemoryViewModel) {
    val memories by viewModel.allMemories.collectAsState()
    var tabIndex by remember { mutableStateOf(0) } // 0: Academic Tracker, 1: Travel Journal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { tabIndex = 0 },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (tabIndex == 0) Color(0xFF3B82F6) else Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Academic Milestone", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { tabIndex = 1 },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (tabIndex == 1) Color(0xFF3B82F6) else Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Travel Journal stories", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tabIndex == 0) {
            // Academic Logs
            val academics = memories.filter { it.type == "ACADEMIC" }
            if (academics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No exam scores or certifications. Save type ACADEMIC to map this journey!", color = Color(0xFF64748B), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("🎓 Educational Timeline Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Total Certifications & Milestones secured: ${academics.size}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }
                    items(academics) { exam ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = exam.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = exam.academicGrade.ifBlank { "Passed" }, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = exam.content, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Travel stories
            val travels = memories.filter { it.type == "TRAVEL" }
            if (travels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No travel logs found. Seal type TRAVEL memories to populate!", color = Color(0xFF64748B), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(travels) { trip ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF10B981))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = trip.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                }
                                if (trip.tripName.isNotEmpty()) {
                                    Text(text = "Part of: ${trip.tripName}", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = trip.content, color = Color(0xFFCBD5E1), fontSize = 13.sp)

                                if (trip.location.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "📍 Recorded at: ${trip.location}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. FAMILY VAULT & RELATIONSHIPS
// ==========================================
@Composable
fun FamilyVaultScreen(viewModel: MemoryViewModel) {
    val relationships by viewModel.allRelationships.collectAsState()
    val memories by viewModel.allMemories.collectAsState()

    var newMemberName by remember { mutableStateOf("") }
    var newMemberRelation by remember { mutableStateOf("FAMILY") }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Family Vault & Relationships",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Maintain offline family albums and trace key friends mention timeline securely",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        // Add relationship buddy form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add Private Core Circle Person:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { newMemberName = it },
                        label = { Text("Name of contact", color = Color(0xFF64748B)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("FAMILY", "FRIEND", "CLASSMATE").forEach { role ->
                            val selected = newMemberRelation == role
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) Color(0xFF3B82F6) else Color(0xFF334155), RoundedCornerShape(12.dp))
                                    .clickable { newMemberRelation = role }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = role, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (newMemberName.isBlank()) {
                                Toast.makeText(context, "Give contact a name!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.addRelationship(newMemberName, newMemberRelation)
                            Toast.makeText(context, "$newMemberName added in circle!", Toast.LENGTH_SHORT).show()
                            newMemberName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text("Register circle", color = Color.White)
                    }
                }
            }
        }

        // List circle members and highlight most mentioned people
        item {
            Text("Core Circle & Contacts Mention Analyzer:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (relationships.isEmpty()) {
            item {
                Text("No contacts or circle members saved yet.", color = Color(0xFF64748B))
            }
        } else {
            items(relationships) { member ->
                // Compute mentions
                val mentionCount = memories.count {
                    it.peopleAsString.contains(member.name, ignoreCase = true) ||
                    it.content.contains(member.name, ignoreCase = true)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = if (member.relationType == "FAMILY") "🏠" else "🤝", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = member.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(text = "Relation: ${member.relationType}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(text = "$mentionCount Mentions", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(onClick = { viewModel.removeRelationship(member) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete circle", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. SECURITY & VAULT PIN LOCK
// ==========================================
@Composable
fun SecurityVaultScreen(viewModel: MemoryViewModel) {
    val securityPin by viewModel.securityPin.collectAsState()
    val pinnedMemories by viewModel.allMemories.collectAsState()
    val actualPinnedList = pinnedMemories.filter { it.isPinned }

    var configPinInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Military-grade Privacy Security",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Seal the private areas of your memory journal. Set an offline pass-vault lock PIN.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (securityPin.isBlank()) "No Pass-vault Lock Configured" else "Pass-vault PIN Lock: ENABLED ✅",
                        color = if (securityPin.isBlank()) Color(0xFFEF4444) else Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = configPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) configPinInput = it },
                        label = { Text("Enter 4-digit PIN lock code", color = Color(0xFF64748B)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (configPinInput.length < 4) {
                                Toast.makeText(context, "PIN must be exactly 4-digits!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.setSecurityPin(configPinInput)
                            Toast.makeText(context, "Secured vault PIN locked successfully!", Toast.LENGTH_SHORT).show()
                            configPinInput = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Deploy Private PIN Code")
                    }

                    if (securityPin.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.setSecurityPin("")
                                Toast.makeText(context, "Vault lock disabled safely", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Disable PIN Lock Protection")
                        }
                    }
                }
            }
        }

        // Pinned memories list
        item {
            Text("Your Pinned Memory Vault:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (actualPinnedList.isEmpty()) {
            item {
                Text("No pinned memories stored. Check the star icon on journals to vault them!", color = Color(0xFF64748B))
            }
        } else {
            items(actualPinnedList) { pMem ->
                MemoryItemRow(memory = pMem, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SecurityLockGate(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    var attemptCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, contentDescription = "Locked icon", tint = Color(0xFFEF4444), modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Memory Vault is Private", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text("Provide your 4-digit security PIN to unlock session", color = Color(0xFF94A3B8), fontSize = 13.sp)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = attemptCode,
            onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) attemptCode = it },
            label = { Text("Unlock PIN", color = Color(0xFF64748B)) },
            modifier = Modifier
                .width(200.dp)
                .testTag("security_pin_input"),
            textStyle = TextStyle(textAlign = TextAlign.Center, color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val success = viewModel.attemptUnlock(attemptCode)
                if (!success) {
                    Toast.makeText(context, "Invalid PIN code! Try again.", Toast.LENGTH_SHORT).show()
                }
                attemptCode = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text("Decrypt Vault", fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 9. KIDS MODE PLAYGROUND (CARTOON CHIC)
// ==========================================
@Composable
fun KidsModeWorkspace(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    var toolSelected by remember { mutableStateOf("TALK") } // "TALK", "DRAW", "STORIES"
    val isRecording by viewModel.isRecording.collectAsState()

    val context = LocalContext.current

    // Cartoon Soft Pastel colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE0F2FE)) // Warm light cyan sky
    ) {
        // Kids App Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFBBF24), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🤖", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Vaulty's world!", color = Color(0xFF0369A1), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("My fun memory box ✨", color = Color(0xFF0284C7), fontSize = 12.sp)
                }
            }

            // Exiting Kids Mode
            Button(
                onClick = { viewModel.toggleKidsMode() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Exit Kids Mode", color = Color.White, fontWeight = FontWeight.Black)
            }
        }

        // Animated Cartoon Tab Selectors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KidsTabButton(icon = "🎤", label = "Talk to Vaulty", isActive = toolSelected == "TALK", modifier = Modifier.weight(1f)) {
                toolSelected = "TALK"
            }
            KidsTabButton(icon = "🎨", label = "Sketches", isActive = toolSelected == "DRAW", modifier = Modifier.weight(1f)) {
                toolSelected = "DRAW"
            }
            KidsTabButton(icon = "✨", label = "Magical Stories", isActive = toolSelected == "STORIES", modifier = Modifier.weight(1f)) {
                toolSelected = "STORIES"
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions visual wrappers
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when (toolSelected) {
                "TALK" -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isRecording) "Vaulty is listening... 💖 Speak now!" else "Press the magical candy mic to tell your story!",
                            color = Color(0xFF0369A1),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                                    .clickable {
                                        viewModel.stopAndSaveVoiceRecording("Kids Adventure Note", "HAPPY")
                                        Toast.makeText(context, "Saved to your magical memory chest!", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🛑", fontSize = 48.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Click stop when done talking!", color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .background(Color(0xFFFBBF24), CircleShape)
                                    .clickable { viewModel.startRecording() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎤", fontSize = 64.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Tell Vaulty about your day!", color = Color(0xFF0369A1), fontWeight = FontWeight.Black)
                        }
                    }
                }
                "DRAW" -> {
                    KidsDrawingSketchpad()
                }
                "STORIES" -> {
                    KidsMagicStoriesSection(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun KidsTabButton(
    icon: String,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (isActive) Color(0xFFFBBF24) else Color.White, RoundedCornerShape(20.dp))
            .border(3.dp, if (isActive) Color(0xFFD97706) else Color(0xFFBAE6FD), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 28.sp)
            Text(label, color = Color(0xFF0369A1), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun KidsDrawingSketchpad() {
    var lines by remember { mutableStateOf(listOf<Offset>()) }
    var stickerSelected by remember { mutableStateOf("⭐") }
    var stickersOnPlace by remember { mutableStateOf(listOf<Pair<Offset, String>>()) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White, RoundedCornerShape(28.dp))
            .border(4.dp, Color(0xFFFBBF24), RoundedCornerShape(28.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stamp Stickers! 👇", color = Color(0xFFD97706), fontWeight = FontWeight.Black, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("⭐", "🦖", "🎈", "🍕", "🚀").forEach { sticker ->
                    val active = stickerSelected == sticker
                    Box(
                        modifier = Modifier
                            .background(if (active) Color(0xFFFBBF24) else Color(0xFFF3F4F6), CircleShape)
                            .clickable { stickerSelected = sticker }
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(sticker, fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Drawing Playground
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFFEF08A).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            lines = lines + offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            lines = lines + change.position
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Drawing lines in continuous sequences
                for (i in 0 until lines.size - 1) {
                    if (lines[i] != Offset.Unspecified && lines[i + 1] != Offset.Unspecified) {
                        drawCircle(color = Color(0xFF3B82F6), radius = 6f, center = lines[i])
                    }
                }
            }

            // Display placed stickers
            stickersOnPlace.forEach { (pos, emote) ->
                Box(
                    modifier = Modifier.offset(pos.x.dp, pos.y.dp)
                ) {
                    Text(emote, fontSize = 24.sp)
                }
            }

            // Help instruction overlay
            Text(
                "Draw with your finger or tap here!",
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                color = Color(0xFF854D0E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    lines = emptyList()
                    stickersOnPlace = emptyList()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Clear Canvas", color = Color.White)
            }

            Button(
                onClick = {
                    Toast.makeText(context, "Saved Sketch securely inside memory chest!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Commit Sketch 🎨", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun KidsMagicStoriesSection(viewModel: MemoryViewModel) {
    val memories by viewModel.allMemories.collectAsState()
    val kidsStories = memories.filter { it.isKidsMode || it.type == "DRAWING" }

    if (kidsStories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tell Vaulty something or draw first to unlock stories!", color = Color(0xFF0369A1), fontWeight = FontWeight.Black)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(kidsStories) { story ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, Color(0xFFFBBF24)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "✨ " + story.title + " ✨", color = Color(0xFF0369A1), fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (story.aiSummary.isNotEmpty()) story.aiSummary else "Every time you draw or write, Vaulty creates a funny story! Speak to Vaulty to draft awesome tales.",
                            color = Color(0xFF0284C7),
                            fontSize = 13.sp
                        )
                        if (story.stickers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                story.stickers.forEach { sName ->
                                    Box(
                                        modifier = Modifier.background(Color(0xFFFEF08A), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "🎈 $sName", color = Color(0xFF854D0E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. AI RECUT SLIDESHOW DIALOG
// ==========================================
@Composable
fun SlideshowDialog(
    memories: List<LocalMemory>,
    onDismiss: () -> Unit
) {
    var slideIndex by remember { mutableStateOf(0) }
    val featuredSlides = memories.take(5) // recapitulating up to 5 entries

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                Text("Close", color = Color.White)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, "Recap film icon", tint = Color(0xFFFBBF24))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Highlight Slideshow Reel", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (featuredSlides.isEmpty()) {
                Text("Seed mock data at the profile top-right or log journals to preview dynamic slideshows!", color = Color(0xFFCBD5E1))
            } else {
                val activeMemory = featuredSlides[slideIndex]

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Play slides
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF3B82F6), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "SLIDE ${slideIndex + 1} OF ${featuredSlides.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = activeMemory.title,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeMemory.content,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (slideIndex > 0) slideIndex--
                            },
                            enabled = slideIndex > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Text("< Back")
                        }

                        // Simulated offline video background play status
                        Text(text = "🎵 Playing ambient synth track", color = Color(0xFF64748B), fontSize = 11.sp)

                        Button(
                            onClick = {
                                if (slideIndex < featuredSlides.size - 1) {
                                    slideIndex++
                                } else {
                                    slideIndex = 0 // loop
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("Next >")
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF1E293B),
        shape = RoundedCornerShape(24.dp)
    )
}
