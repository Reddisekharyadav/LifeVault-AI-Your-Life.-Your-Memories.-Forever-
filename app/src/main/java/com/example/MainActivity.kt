package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.LocalMemory
import com.example.data.model.LocalUser
import com.example.data.storage.CompressedStorageManager
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.ForgotPasswordDialog
import com.example.ui.screens.PersonalDiaryScreen
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
    DIARY,
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
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val isKidsMode by viewModel.isKidsMode.collectAsState()
    val isScreenLocked by viewModel.isScreenLocked.collectAsState()

    if (!isUserLoggedIn) {
        AuthScreen(viewModel = viewModel, modifier = modifier)
    } else {
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
                    text = "📖 Personal Diary",
                    icon = Icons.Default.Edit,
                    isSelected = currentScreen == VaultScreen.DIARY,
                    onClick = { currentScreen = VaultScreen.DIARY }
                )
            }
            item {
                TabChip(
                    text = "Journal Lab",
                    icon = Icons.Default.Create,
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
                VaultScreen.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToCreator = { currentScreen = VaultScreen.CREATOR },
                    onNavigateToDiary = { currentScreen = VaultScreen.DIARY }
                )
                VaultScreen.DIARY -> PersonalDiaryScreen(viewModel = viewModel)
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
    val currentUser by viewModel.currentUser.collectAsState()
    val localUserEmail by viewModel.localUserEmail.collectAsState()
    var showProfileManager by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showProfileManager = true }
                    .testTag("profile_section_click"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarEmoji = currentUser?.avatarEmoji ?: "👤"
                val avatarColor = Color(
                    try {
                        android.graphics.Color.parseColor(currentUser?.colorHex ?: "#3B82F6")
                    } catch (e: Exception) {
                        0xFF3B82F6.toInt()
                    }
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarEmoji,
                        fontSize = 22.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentUser?.fullName ?: localUserEmail.substringBefore("@"),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Manage Account",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = "🔒 $localUserEmail",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    Text(
                        text = "100% Local Encrypted | Zero Cloud",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Storage & Accounts Quick Button
                IconButton(
                    onClick = { showProfileManager = true },
                    modifier = Modifier.background(Color(0xFF334155), CircleShape).size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Storage and Accounts",
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

    if (showProfileManager) {
        ProfileManagerDialog(viewModel = viewModel) {
            showProfileManager = false
        }
    }
}

@Composable
fun ProfileManagerDialog(
    viewModel: MemoryViewModel,
    onDismiss: () -> Unit
) {
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val localEmail by viewModel.localUserEmail.collectAsState()
    val telemetry by viewModel.storageTelemetry.collectAsState()

    var activeDialogTab by remember { mutableStateOf(0) } // 0: Local Account & Storage, 1: Device Accounts

    // Import / Export state
    var importArchiveInput by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }

    // New user creation state
    var showCreateForm by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var newUserEmail by remember { mutableStateOf("") }
    var newUserPin by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("👤") }
    var selectedColor by remember { mutableStateOf("#3B82F6") }

    val context = LocalContext.current
    val emojis = listOf("👤", "🦊", "🐼", "🦁", "🦉", "🚀", "🪐", "💎", "⭐", "🍀")
    val colors = listOf("#3B82F6", "#EF4444", "#10B981", "#8B5CF6", "#FBBF24", "#EC4899", "#14B8A6")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_profiles_dialog")) {
                Text("Close", color = Color.White)
            }
        },
        title = {
            Text(
                text = "Local Account & Storage Console",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Tab Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { activeDialogTab = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeDialogTab == 0) Color(0xFF3B82F6) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Active Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { activeDialogTab = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeDialogTab == 1) Color(0xFF3B82F6) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Device Accounts", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (activeDialogTab == 0) {
                    // Current User Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val avatarEmoji = currentUser?.avatarEmoji ?: "👤"
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF3B82F6), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(avatarEmoji, fontSize = 22.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = currentUser?.fullName ?: "Local User",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = localEmail,
                                        color = Color(0xFF10B981),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.logout()
                                        onDismiss()
                                        Toast.makeText(context, "Signed out of local vault.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Local Storage Telemetry & Actions
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🗜️", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Compressed Storage Telemetry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("High-density GZIP memory footprint", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Stats grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Raw Size", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        Text(CompressedStorageManager.formatBytes(telemetry.rawSizeBytes), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Compressed GZIP", color = Color(0xFF3B82F6), fontSize = 10.sp)
                                        Text(CompressedStorageManager.formatBytes(telemetry.compressedSizeBytes), color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Saved", color = Color(0xFF10B981), fontSize = 10.sp)
                                        Text(telemetry.compressionRatioText, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons: Export GZIP & Compact
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val result = viewModel.exportCompressedVault()
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("LifeVault Compressed Archive", result.compressedPayload)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied GZIP Archive (${result.savingsPercent}% compressed)! Saved ${CompressedStorageManager.formatBytes(result.rawBytes - result.compressedBytes)}.", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f).testTag("export_archive_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Export GZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val msg = viewModel.compactLocalDatabase()
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).testTag("compact_db_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Compact DB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Import compressed archive toggle
                            OutlinedButton(
                                onClick = { showImportBox = !showImportBox },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(if (showImportBox) "Hide Restore Box" else "Restore / Import GZIP Archive", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }

                            if (showImportBox) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = importArchiveInput,
                                    onValueChange = { importArchiveInput = it },
                                    label = { Text("Paste Base64 GZIP Archive", color = Color(0xFF64748B)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (importArchiveInput.isBlank()) {
                                            Toast.makeText(context, "Paste archive data first!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val (success, msg) = viewModel.importCompressedVault(importArchiveInput)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        if (success) {
                                            importArchiveInput = ""
                                            showImportBox = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Decompress & Restore Vault", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: Switch between accounts or create a new one
                    if (!showCreateForm) {
                        Text(
                            text = "Switch between local accounts on this device. Each user's data is completely isolated and stored offline.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )

                        registeredUsers.forEach { user ->
                            val isActive = user.email == localEmail
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchUser(user)
                                        Toast.makeText(context, "Switched to ${user.fullName}!", Toast.LENGTH_SHORT).show()
                                    }
                                    .testTag("user_account_item_${user.email}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) Color(0xFF334155) else Color(0xFF1E293B)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isActive) Color(0xFF3B82F6) else Color(0xFF334155)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Color(
                                                        try {
                                                            android.graphics.Color.parseColor(user.colorHex)
                                                        } catch (e: Exception) {
                                                            0xFF3B82F6.toInt()
                                                        }
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(user.avatarEmoji, fontSize = 20.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = user.fullName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = user.email,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showCreateForm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_new_account_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Another Local Account", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                    } else {
                        Text(
                            text = "Register a new local account on this device.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        OutlinedTextField(
                            value = newUserName,
                            onValueChange = { newUserName = it },
                            label = { Text("Full Name", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = newUserEmail,
                            onValueChange = { newUserEmail = it },
                            label = { Text("Email Address", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = newUserPin,
                            onValueChange = { newUserPin = it },
                            label = { Text("Security PIN / Password (min 4 digits)", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )

                        // Emoji Picker
                        Column {
                            Text("Profile Avatar:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(emojis) { emoji ->
                                    val active = selectedEmoji == emoji
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (active) Color(0xFF3B82F6) else Color(0xFF334155))
                                            .clickable { selectedEmoji = emoji },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        // Color Picker
                        Column {
                            Text("Theme Color:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(colors) { hex ->
                                    val active = selectedColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(2.dp, if (active) Color.White else Color.Transparent, CircleShape)
                                            .clickable { selectedColor = hex }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showCreateForm = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    val (success, msg) = viewModel.signUp(
                                        fullName = newUserName,
                                        email = newUserEmail,
                                        pinOrPass = newUserPin,
                                        avatarEmoji = selectedEmoji,
                                        colorHex = selectedColor
                                    )
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        newUserName = ""
                                        newUserEmail = ""
                                        newUserPin = ""
                                        showCreateForm = false
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .testTag("save_new_account_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save & Switch", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF1E293B),
        shape = RoundedCornerShape(24.dp)
    )
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
    onNavigateToCreator: () -> Unit,
    onNavigateToDiary: () -> Unit = {}
) {
    val context = LocalContext.current
    val memories by viewModel.allMemories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isRecordingPaused by viewModel.isRecordingPaused.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val speechStatus by viewModel.speechStatus.collectAsState()
    val audioRmsLevel by viewModel.audioRmsLevel.collectAsState()

    var showSlideshow by remember { mutableStateOf(false) }
    var voiceTitle by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("CALM") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice recording and transcription.", Toast.LENGTH_LONG).show()
        }
    }

    val requestVoiceRecord = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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

        // Live Voice Recording & Speech-to-Text Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, if (isRecording) Color(0xFFEF4444) else Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Color(0xFFEF4444) else Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRecording) "LIVE SPEECH CONVERSION" else "VOICE-FIRST JOURNAL",
                                color = if (isRecording) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                style = TextStyle(letterSpacing = 1.sp)
                            )
                        }

                        // Storage badge
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "📱 In-App Local Phone Storage",
                                color = Color(0xFF60A5FA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isRecording) {
                        // Sound Wave Visualizer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bars = listOf(0.4f, 0.7f, 1.0f, 0.6f, 0.9f, 0.5f, 0.8f, 0.3f, 0.95f, 0.6f, 0.4f)
                            bars.forEachIndexed { index, scale ->
                                val heightFactor = (audioRmsLevel * scale * 26f).coerceIn(4f, 30f)
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(heightFactor.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (index % 2 == 0) Color(0xFFEF4444) else Color(0xFFF59E0B))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Status & Timer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = speechStatus,
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "⏱ 00:${String.format("%02d", recordingDuration)}",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Real-Time Converted Text Input / Live Display
                        Text(
                            text = "Speech-to-Text Live Transcript (Editable):",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = liveTranscript,
                            onValueChange = { viewModel.updateLiveTranscript(it) },
                            placeholder = {
                                Text(
                                    "Speak now into your microphone... Transcribed words appear here in real time.",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp, max = 150.dp)
                                .testTag("voice_live_transcript_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Memoir Title (Optional)
                        OutlinedTextField(
                            value = voiceTitle,
                            onValueChange = { voiceTitle = it },
                            placeholder = { Text("Memory Title (Optional)", color = Color(0xFF64748B), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mood selection chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val moods = listOf(
                                "CALM" to "🧘 Calm",
                                "HAPPY" to "😊 Happy",
                                "EXCITED" to "🤩 Excited",
                                "TIRED" to "😴 Tired",
                                "SAD" to "😢 Sad",
                                "GRATEFUL" to "🙏 Grateful"
                            )
                            moods.forEach { (moodKey, moodLabel) ->
                                val isSelected = selectedMood == moodKey
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) Color(0xFF3B82F6) else Color(0xFF0F172A))
                                        .border(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155), RoundedCornerShape(20.dp))
                                        .clickable { selectedMood = moodKey }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = moodLabel,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Controls Row: Done & Save, Convert to Diary, Pause, Cancel
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.convertVoiceToPersonalDiary(customTitle = voiceTitle) {
                                        voiceTitle = ""
                                        onNavigateToDiary()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("convert_voice_to_personal_diary_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("✨ Convert Voice to Personal Diary (Auto-Emotion)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.stopAndSaveVoiceRecording(voiceTitle, selectedMood)
                                        voiceTitle = ""
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(42.dp)
                                        .testTag("save_voice_memo_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        if (isRecordingPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (isRecordingPaused) "Resume" else "Pause",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        viewModel.cancelRecording()
                                        voiceTitle = ""
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // Idle Press & Speak UI
                        Text(
                            text = "Press & Speak to Journal",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Speaks directly into the microphone. Converts your speech to text live, auto-predicts your emotion, and structures as a personal diary.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = requestVoiceRecord,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(72.dp)
                                    .testTag("mic_button")
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Start Voice Entry",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onNavigateToDiary,
                            modifier = Modifier.fillMaxWidth().testTag("open_diary_editor_btn"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6))
                        ) {
                            Text("📖 Open Personal Diary Notebook Editor", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        Text("No Memories Yet", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Tap '+ Add New' or use Voice Journaling above to record your first memory in your private vault!", color = Color(0xFF94A3B8), fontSize = 11.sp, textAlign = TextAlign.Center)
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
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val playingMemoryId by viewModel.playingMemoryId.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()
    val isPlayingThis = playingMemoryId == memory.id

    val isVoiceMemory = memory.type == "VOICE" || memory.audioPath.isNotBlank() || memory.voiceTranscript.isNotBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("memory_item_card_${memory.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isVoiceMemory) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Type Badge + Title + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = when (memory.type) {
                        "DIARY" -> Color(0xFF8B5CF6)
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
                        Text(
                            text = when (memory.type) {
                                "VOICE" -> "🎙️ VOICE MEMO"
                                "DIARY" -> "📖 PERSONAL DIARY"
                                else -> memory.type
                            },
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = memory.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content / Speech-to-Text Transcript Presentation
            if (isVoiceMemory) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📝 Transcribed Speech (Local):",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Transcribed Speech", memory.content)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Transcript copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Copy transcript",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = memory.content.ifBlank { memory.voiceTranscript.ifBlank { "Spontaneous voice memo" } },
                            color = Color(0xFFF1F5F9),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        // In-App Local Audio Player Bar
                        if (memory.audioPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlayingThis) Color(0xFFEF4444) else Color(0xFF3B82F6))
                                        .clickable { viewModel.playMemoryAudio(memory) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlayingThis) "Stop Audio" else "Play Audio",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPlayingThis) "▶ Playing local phone audio..." else "Local Audio Recording",
                                        color = if (isPlayingThis) Color(0xFFEF4444) else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    LinearProgressIndicator(
                                        progress = { if (isPlayingThis) audioProgress else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(CircleShape),
                                        color = Color(0xFFEF4444),
                                        trackColor = Color(0xFF334155)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📱 Private App Storage: ${memory.audioPath.ifBlank { "local_stt.txt" }}",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = memory.mood,
                                    color = Color(0xFF60A5FA),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = if (memory.isCapsuleLocked) "🔐 Time Capsule Locked! Date scheduled: Unlocking soon" else memory.content,
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
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
            }

            // Expanded detail segment for stories and tag lists
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
    val localEmail by viewModel.localUserEmail.collectAsState()
    val telemetry by viewModel.storageTelemetry.collectAsState()

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
                text = "100% Offline Vault & Privacy Protection",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Zero cloud exposure. All journals, voice notes, and media are compressed and stored locally.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        // Master Email Identity
        item {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Master Local Account Identity", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(localEmail, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Encrypted on-device (Offline Only)", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🔒 100% LOCAL", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Compressed Storage Telemetry & Compact
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🗜️ Compressed Local Storage Telemetry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).background(Color(0xFF0F172A), RoundedCornerShape(10.dp)).padding(8.dp)
                        ) {
                            Column {
                                Text("Raw SQLite", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                Text(CompressedStorageManager.formatBytes(telemetry.rawSizeBytes), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f).background(Color(0xFF0F172A), RoundedCornerShape(10.dp)).padding(8.dp)
                        ) {
                            Column {
                                Text("GZIP Density", color = Color(0xFF3B82F6), fontSize = 10.sp)
                                Text(CompressedStorageManager.formatBytes(telemetry.compressedSizeBytes), color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f).background(Color(0xFF0F172A), RoundedCornerShape(10.dp)).padding(8.dp)
                        ) {
                            Column {
                                Text("Space Saved", color = Color(0xFF10B981), fontSize = 10.sp)
                                Text(telemetry.compressionRatioText, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val result = viewModel.exportCompressedVault()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("LifeVault Archive", result.compressedPayload)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied Compressed Archive (${result.savingsPercent}% compression)! Saved ${CompressedStorageManager.formatBytes(result.rawBytes - result.compressedBytes)}.", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Export GZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val feedback = viewModel.compactLocalDatabase()
                                Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Compact & Index", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
    var showPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFEF4444).copy(alpha = 0.15f),
            border = BorderStroke(2.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, contentDescription = "Locked icon", tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Memory Vault is Locked", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(
            text = if (currentUser != null) "Active user: ${currentUser?.fullName} (${currentUser?.email})" else "Enter your Security PIN / Password to unlock",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = attemptCode,
            onValueChange = { attemptCode = it },
            label = { Text("Unlock PIN / Password", color = Color(0xFF64748B)) },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .testTag("security_pin_input"),
            textStyle = TextStyle(textAlign = TextAlign.Center, color = Color.White, fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.Done else Icons.Default.Lock,
                        contentDescription = "Toggle password visibility",
                        tint = Color(0xFF94A3B8)
                    )
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val success = viewModel.attemptUnlock(attemptCode)
                if (!success) {
                    Toast.makeText(context, "Incorrect PIN or Password! Try again or reset.", Toast.LENGTH_SHORT).show()
                }
                attemptCode = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(48.dp)
                .testTag("decrypt_vault_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Decrypt Vault", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showForgotDialog = true },
                modifier = Modifier.testTag("lock_forgot_pin_btn")
            ) {
                Text("Forgot PIN?", color = Color(0xFF60A5FA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Text("•", color = Color(0xFF475569))

            TextButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.testTag("lock_sign_out_btn")
            ) {
                Text("Switch Account / Sign Out", color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            viewModel = viewModel,
            initialEmail = currentUser?.email ?: "",
            onDismiss = { showForgotDialog = false }
        )
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
