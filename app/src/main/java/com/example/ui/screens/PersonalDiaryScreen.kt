package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.LocalMemory
import com.example.ui.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PersonalDiaryScreen(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val memories by viewModel.allMemories.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isRecordingPaused by viewModel.isRecordingPaused.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val speechStatus by viewModel.speechStatus.collectAsState()
    val audioRmsLevel by viewModel.audioRmsLevel.collectAsState()
    val isFormattingDiary by viewModel.isFormattingDiary.collectAsState()
    val diaryFormatNotice by viewModel.diaryFormatNotice.collectAsState()
    val playingMemoryId by viewModel.playingMemoryId.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()

    // Filter diary and voice memories
    val diaryMemories = memories.filter {
        it.type == "DIARY" || it.type == "VOICE" || it.type == "JOURNAL"
    }

    // Editor state
    val currentDateFormatted = remember {
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date())
    }

    var diaryTitle by remember { mutableStateOf("") }
    var diaryText by remember { mutableStateOf("") }
    var predictedEmotion by remember { mutableStateOf("CALM") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Personal Diary Editor, 1: Past Diary Entries

    // System Voice Input Intent Launcher (Guaranteed to work across all Android devices/emulators)
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!spokenMatches.isNullOrEmpty()) {
                val spokenText = spokenMatches[0]
                viewModel.updateLiveTranscript(spokenText)
                viewModel.convertVoiceToPersonalDiary(rawTranscript = spokenText) { formattedMemory ->
                    diaryTitle = formattedMemory.title
                    diaryText = formattedMemory.content
                    predictedEmotion = formattedMemory.mood
                    Toast.makeText(context, "Voice converted to diary points!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val launchSystemVoiceInput = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your diary points now...")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice recording.", Toast.LENGTH_SHORT).show()
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
        modifier = modifier
            .fillMaxSize()
            .testTag("lazy_diary_column")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.5.dp, Color(0xFF334155))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📖", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Personal Daily Diary",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = "Voice-to-Text • AI Emotion Prediction • Point-Wise Format",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🔒 100% Private",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sub Navigation: Diary Editor vs Past Diaries
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f).testTag("tab_diary_editor"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) Color(0xFF3B82F6) else Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (selectedTab == 0) Color(0xFF60A5FA) else Color(0xFF334155))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Diary Editor", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f).testTag("tab_past_diaries"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) Color(0xFF3B82F6) else Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (selectedTab == 1) Color(0xFF60A5FA) else Color(0xFF334155))
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Past Entries (${diaryMemories.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedTab == 0) {
            // Voice-to-Diary Quick Capture Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.5.dp, if (isRecording) Color(0xFFEF4444) else Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                    text = if (isRecording) "RECORDING & TRANSCRIBING VOICE" else "VOICE TO DIARY CONVERTER",
                                    color = if (isRecording) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    style = TextStyle(letterSpacing = 1.sp)
                                )
                            }

                            if (isRecording) {
                                Text(
                                    text = "⏱ 00:${String.format("%02d", recordingDuration)}",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isRecording) {
                            // Sound wave visualizer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bars = listOf(0.4f, 0.7f, 1.0f, 0.6f, 0.9f, 0.5f, 0.8f, 0.3f, 0.95f, 0.6f, 0.4f)
                                bars.forEachIndexed { index, scale ->
                                    val heightFactor = (audioRmsLevel * scale * 24f).coerceIn(4f, 26f)
                                    Box(
                                        modifier = Modifier
                                            .width(3.5.dp)
                                            .height(heightFactor.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (index % 2 == 0) Color(0xFFEF4444) else Color(0xFFF59E0B))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = speechStatus,
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp
                            )

                            if (liveTranscript.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Live Speech: \"$liveTranscript\"",
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons when recording
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.convertVoiceToPersonalDiary { formattedMemory ->
                                            diaryTitle = formattedMemory.title
                                            diaryText = formattedMemory.content
                                            predictedEmotion = formattedMemory.mood
                                            Toast.makeText(context, "Voice converted to Personal Diary!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.5f).testTag("convert_voice_to_diary_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Done & Convert to Diary", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.cancelRecording() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                }
                            }
                        } else {
                            // Idle state: prompt to speak
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Speak to Write Diary",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Tap to speak. Whatever you say will be converted to text and formatted as clean date & point-wise diary entries.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = launchSystemVoiceInput,
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(48.dp)
                                            .testTag("system_voice_speech_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("🎙️", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tap to Speak", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = requestVoiceRecord,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("diary_mic_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("🔴", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Live Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Formatting status loader
            if (isFormattingDiary) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF3B82F6),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "✨ Gemini 2.5 Flash Transcribing & Structuring Diary...",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Processing audio with AI • Transcribing speech & formatting neat bullet points",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            if (diaryFormatNotice.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "✨ $diaryFormatNotice",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // AUTHENTIC PERSONAL DIARY PAPER / NOTEBOOK CANVAS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.5.dp, Color(0xFF475569))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Notebook Header with Date & Predicted Emotion Banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "📅 $currentDateFormatted",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Personal Daily Ledger",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }

                            // Predicted Emotion Badge
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌿", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Emotion: $predictedEmotion",
                                        color = Color(0xFF60A5FA),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFF334155), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Diary Title Field
                        OutlinedTextField(
                            value = diaryTitle,
                            onValueChange = { diaryTitle = it },
                            label = { Text("Diary Title", color = Color(0xFF64748B)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diary_title_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toolbar for Quick Formatting & AI Actions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Direct Speak into Editor Button
                            Button(
                                onClick = launchSystemVoiceInput,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("editor_voice_input_btn")
                            ) {
                                Text("🎙️ Speak to Write", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Insert Clean Template
                            OutlinedButton(
                                onClick = {
                                    if (diaryTitle.isBlank()) {
                                        diaryTitle = "Personal Diary • $currentDateFormatted"
                                    }
                                    diaryText = "📅 $currentDateFormatted\n🌿 Mood: $predictedEmotion\n\n• "
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("insert_template_btn"),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6))
                            ) {
                                Text("📋 Diary Template", color = Color(0xFF93C5FD), fontSize = 11.sp)
                            }

                            // Format with AI Button
                            Button(
                                onClick = {
                                    if (diaryText.isBlank()) {
                                        Toast.makeText(context, "Please write or speak some thoughts first!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.formatTextToPersonalDiary(diaryText) { title, content, emotion ->
                                        diaryTitle = title
                                        diaryText = content
                                        predictedEmotion = emotion
                                        Toast.makeText(context, "Formatted into neat diary points!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("ai_format_btn")
                            ) {
                                Text("✨ Point-Wise Format", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Insert Bullet Point
                            OutlinedButton(
                                onClick = {
                                    diaryText = if (diaryText.isBlank()) {
                                        "• "
                                    } else if (diaryText.endsWith("\n")) {
                                        "$diaryText• "
                                    } else {
                                        "$diaryText\n• "
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text("• Bullet Point", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }

                            // Clear Editor
                            OutlinedButton(
                                onClick = {
                                    diaryTitle = ""
                                    diaryText = ""
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                            ) {
                                Text("🗑️ Clear", color = Color(0xFFFCA5A5), fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Notebook Ruled Text Editor
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                .padding(2.dp)
                        ) {
                            OutlinedTextField(
                                value = diaryText,
                                onValueChange = { diaryText = it },
                                placeholder = {
                                    Text(
                                        "📅 $currentDateFormatted\n🌿 Mood: $predictedEmotion\n\n• Speak or type your daily thoughts here...",
                                        color = Color(0xFF64748B),
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 360.dp)
                                    .testTag("diary_text_editor_field"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color(0xFFF1F5F9),
                                    unfocusedTextColor = Color(0xFFF1F5F9),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    fontFamily = FontFamily.Default
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Emotion chips for manual adjustment if desired
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val emotionList = listOf(
                                "GRATEFUL" to "🙏 Grateful",
                                "PEACEFUL" to "🧘 Peaceful",
                                "HAPPY" to "😊 Happy",
                                "INSPIRED" to "💡 Inspired",
                                "TIRED" to "😴 Tired",
                                "REFLECTIVE" to "💭 Reflective",
                                "ANXIOUS" to "🌪️ Anxious",
                                "DETERMINED" to "🔥 Determined"
                            )
                            emotionList.forEach { (code, label) ->
                                val isSel = predictedEmotion.equals(code, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSel) Color(0xFF3B82F6) else Color(0xFF0F172A))
                                        .border(1.dp, if (isSel) Color(0xFF60A5FA) else Color(0xFF334155), RoundedCornerShape(16.dp))
                                        .clickable { predictedEmotion = code }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Save Entry Button
                        Button(
                            onClick = {
                                if (diaryText.isBlank()) {
                                    Toast.makeText(context, "Please write some thoughts in your diary first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.addMemory(
                                    type = "DIARY",
                                    title = diaryTitle.ifBlank { "Personal Daily Diary • $currentDateFormatted" },
                                    content = diaryText,
                                    mood = predictedEmotion,
                                    location = "Personal Journal",
                                    tags = "Diary, Personal, Daily, $predictedEmotion",
                                    people = "",
                                    capsuleUnlockInDays = 0,
                                    isPinned = true,
                                    tripName = ""
                                )
                                Toast.makeText(context, "Diary entry permanently sealed in your offline vault!", Toast.LENGTH_SHORT).show()
                                selectedTab = 1
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_diary_entry_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Seal in Personal Diary Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        } else {
            // PAST DIARY ENTRIES TAB
            item {
                Text(
                    text = "Saved Personal Daily Entries",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            if (diaryMemories.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📖", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No diary entries recorded yet.", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "Speak or write your daily reflections in the Diary Editor tab above.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(diaryMemories) { entry ->
                    val isPlayingThis = playingMemoryId == entry.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diary_entry_card_${entry.id}"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF8B5CF6), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (entry.audioPath.isNotBlank()) "🎙️ VOICE DIARY" else "📖 DIARY",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row {
                                    IconButton(onClick = { viewModel.togglePin(entry) }) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Pin entry",
                                            tint = if (entry.isPinned) Color(0xFFFBBF24) else Color(0xFF475569),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteMemory(entry) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete entry",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Mood and Date metadata
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(entry.timestamp)),
                                    color = Color(0xFFF59E0B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Mood: ${entry.mood}",
                                        color = Color(0xFF60A5FA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Diary Content with notebook styling
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = entry.content,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }

                            // Audio player if recorded from voice
                            if (entry.audioPath.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isPlayingThis) Color(0xFFEF4444) else Color(0xFF3B82F6))
                                            .clickable { viewModel.playMemoryAudio(entry) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Default.Close else Icons.Default.PlayArrow,
                                            contentDescription = "Play voice recording",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isPlayingThis) "▶ Playing original voice entry..." else "Original Voice Memo Recording",
                                            color = if (isPlayingThis) Color(0xFFEF4444) else Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        LinearProgressIndicator(
                                            progress = { if (isPlayingThis) audioProgress else 0f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(CircleShape),
                                            color = Color(0xFFEF4444),
                                            trackColor = Color(0xFF334155)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons: Copy & Load into Editor
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        diaryTitle = entry.title
                                        diaryText = entry.content
                                        predictedEmotion = entry.mood
                                        selectedTab = 0
                                        Toast.makeText(context, "Loaded into Diary Editor!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open in Editor", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Diary Entry", "${entry.title}\n\n${entry.content}")
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Diary entry copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Text", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
