package com.example.ui.auth

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MemoryViewModel

@Composable
fun AuthScreen(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    var isSignUpTab by remember { mutableStateOf(false) }
    val registeredUsers by viewModel.registeredUsers.collectAsState()

    // Sign In fields
    var loginEmail by remember { mutableStateOf("") }
    var loginPin by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    // Sign Up fields
    var signUpName by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpPin by remember { mutableStateOf("") }
    var showSignUpPassword by remember { mutableStateOf(false) }
    var selectedEmoji by remember { mutableStateOf("👤") }
    var selectedColor by remember { mutableStateOf("#3B82F6") }

    val context = LocalContext.current
    val emojis = listOf("👤", "🦊", "🐼", "🦁", "🦉", "🚀", "🪐", "💎", "⭐", "🍀")
    val colors = listOf("#3B82F6", "#EF4444", "#10B981", "#8B5CF6", "#FBBF24", "#EC4899", "#14B8A6")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B0F19),
                        Color(0xFF0F172A),
                        Color(0xFF1E293B)
                    )
                )
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emblem and Title
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                        )
                    )
                    .border(2.dp, Color(0xFF60A5FA), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "💎", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LifeVault AI",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Private, 100% Offline Local Memory Vault",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Switch Account if accounts already exist on device
            if (registeredUsers.isNotEmpty() && !isSignUpTab) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Saved Local Accounts on this Device:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(registeredUsers) { user ->
                                Surface(
                                    modifier = Modifier
                                        .clickable {
                                            loginEmail = user.email
                                        },
                                    color = if (loginEmail == user.email) Color(0xFF3B82F6).copy(alpha = 0.3f) else Color(0xFF0F172A),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (loginEmail == user.email) Color(0xFF3B82F6) else Color(0xFF334155))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(user.avatarEmoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(user.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(user.email, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tab Selector (Sign In vs Sign Up)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (!isSignUpTab) Color(0xFF3B82F6) else Color.Transparent)
                            .clickable { isSignUpTab = false }
                            .testTag("tab_sign_in"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            color = if (!isSignUpTab) Color.White else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSignUpTab) Color(0xFF3B82F6) else Color.Transparent)
                            .clickable { isSignUpTab = true }
                            .testTag("tab_sign_up"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Account",
                            color = if (isSignUpTab) Color.White else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Auth Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!isSignUpTab) {
                        // ==================== SIGN IN ====================
                        Text(
                            text = "Sign In to Your Local Vault",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        OutlinedTextField(
                            value = loginEmail,
                            onValueChange = { loginEmail = it },
                            label = { Text("Local Email", color = Color(0xFF64748B)) },
                            placeholder = { Text("e.g. yourname@example.com", color = Color(0xFF475569)) },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF60A5FA)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = loginPin,
                            onValueChange = { loginPin = it },
                            label = { Text("Security PIN / Password", color = Color(0xFF64748B)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF60A5FA)) },
                            trailingIcon = {
                                IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                    Icon(
                                        if (showLoginPassword) Icons.Default.Done else Icons.Default.Lock,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_pin_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (loginEmail.isBlank()) {
                                    Toast.makeText(context, "Please enter your email address!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val result = viewModel.login(loginEmail, loginPin)
                                if (result.first) {
                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_submit_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Vault & Sign In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = { showForgotPasswordDialog = true },
                                modifier = Modifier.testTag("forgot_password_btn")
                            ) {
                                Text(
                                    text = "Forgot PIN / Password?",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                    } else {
                        // ==================== SIGN UP ====================
                        Text(
                            text = "Create Your First Offline Account",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        OutlinedTextField(
                            value = signUpName,
                            onValueChange = { signUpName = it },
                            label = { Text("Your Full Name", color = Color(0xFF64748B)) },
                            placeholder = { Text("e.g. Alex Mercer", color = Color(0xFF475569)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF60A5FA)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_name_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = signUpEmail,
                            onValueChange = { signUpEmail = it },
                            label = { Text("Email Address (Used for local vault identity)", color = Color(0xFF64748B)) },
                            placeholder = { Text("e.g. alex@example.com", color = Color(0xFF475569)) },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF60A5FA)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_email_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = signUpPin,
                            onValueChange = { signUpPin = it },
                            label = { Text("Security PIN / Password (min 4 chars)", color = Color(0xFF64748B)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF60A5FA)) },
                            trailingIcon = {
                                IconButton(onClick = { showSignUpPassword = !showSignUpPassword }) {
                                    Icon(
                                        if (showSignUpPassword) Icons.Default.Done else Icons.Default.Lock,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            visualTransformation = if (showSignUpPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_pin_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )

                        // Avatar Selection
                        Column {
                            Text("Choose Profile Avatar:", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(emojis) { emoji ->
                                    val isSelected = selectedEmoji == emoji
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF3B82F6) else Color(0xFF0F172A))
                                            .border(1.5.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155), CircleShape)
                                            .clickable { selectedEmoji = emoji },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 20.sp)
                                    }
                                }
                            }
                        }

                        // Accent Color Selection
                        Column {
                            Text("Accent Theme Color:", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(colors) { hex ->
                                    val isSelected = selectedColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                            .clickable { selectedColor = hex }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val result = viewModel.signUp(
                                    fullName = signUpName,
                                    email = signUpEmail,
                                    pinOrPass = signUpPin,
                                    avatarEmoji = selectedEmoji,
                                    colorHex = selectedColor
                                )
                                if (result.first) {
                                    Toast.makeText(context, "Account created! Welcome to LifeVault.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("signup_submit_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Account & Open Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy guarantee badge
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Offline Secure",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% Offline & Local. Your data never leaves your device.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            viewModel = viewModel,
            initialEmail = loginEmail,
            onDismiss = { showForgotPasswordDialog = false }
        )
    }
}

@Composable
fun ForgotPasswordDialog(
    viewModel: MemoryViewModel,
    initialEmail: String = "",
    onDismiss: () -> Unit
) {
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Security PIN / Password", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Since LifeVault AI is 100% offline, you can reset your PIN instantly for any local account registered on this device.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                if (registeredUsers.isNotEmpty()) {
                    Text(
                        text = "Select account or type email:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(registeredUsers) { user ->
                            Surface(
                                modifier = Modifier.clickable { resetEmail = user.email },
                                color = if (resetEmail == user.email) Color(0xFF3B82F6).copy(alpha = 0.3f) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (resetEmail == user.email) Color(0xFF3B82F6) else Color(0xFF334155))
                            ) {
                                Text(
                                    text = "${user.avatarEmoji} ${user.email}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    label = { Text("Account Email", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF60A5FA)) },
                    modifier = Modifier.fillMaxWidth().testTag("reset_email_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = { Text("New PIN / Password (4+ digits)", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF60A5FA)) },
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                if (showPin) Icons.Default.Done else Icons.Default.Lock,
                                contentDescription = "Toggle visibility",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("reset_new_pin_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it },
                    label = { Text("Confirm New PIN / Password", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF60A5FA)) },
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("reset_confirm_pin_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (resetEmail.isBlank()) {
                        Toast.makeText(context, "Please enter your account email", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (newPin.length < 4) {
                        Toast.makeText(context, "New PIN must be at least 4 characters", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (newPin != confirmPin) {
                        Toast.makeText(context, "PINs do not match! Please check.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val result = viewModel.resetPasswordOrPin(resetEmail, newPin)
                    Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                    if (result.first) {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                modifier = Modifier.testTag("confirm_reset_password_btn")
            ) {
                Text("Reset & Sign In", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1E293B),
        shape = RoundedCornerShape(20.dp)
    )
}
