package acn.amrita.chen.planner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import acn.amrita.chen.planner.ai.ChatMessage
import androidx.lifecycle.viewmodel.compose.viewModel
import acn.amrita.chen.planner.ai.AssistantViewModel
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts



// Colour tokens
private val AcnRed      = Color(0xFFC62828)
private val AcnSurface  = Color(0xFF1A1A1A)
private val AcnCard     = Color(0xFF242424)
private val AcnCardAlt  = Color(0xFF2C2C2C)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSec     = Color(0xFFAAAAAA)
private val UserBubble  = Color(0xFF3A0000)
private val AiBubble    = Color(0xFF242424)

// ── Domain models ─────────────────────────────────────────────────────────────

val SUGGESTION_CHIPS = listOf(
    "What's my next class?",
    "Show my attendance",
    "Any upcoming exams?",
    "What changed today?",
    "Am I safe to skip a class?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: acn.amrita.chen.planner.ui.MainViewModel) {
    val assistantVm: AssistantViewModel = viewModel()
    val messages by assistantVm.messages.collectAsState()
    val isApiKeySet by assistantVm.isApiKeySet.collectAsState()
    val isLoading by assistantVm.isLoading.collectAsState()

    AssistantScreenContent(
        hasApiKey = isApiKeySet,
        messages = messages,
        isThinking = isLoading,
        onSend = { text, uris -> assistantVm.sendMessage(text, uris) },
        onSavePdfAnalysis = { assistantVm.simulatePdfImport() },
        onOpenKeyDialog = { /* handled inside content */ },
        onApiKeySave = { key -> assistantVm.setApiKey(key) }
    )
}
@Composable
fun AssistantScreenContent(
    hasApiKey: Boolean = false,
    messages: List<ChatMessage> = sampleMessages(),
    isThinking: Boolean = false,
    onSend: (String, List<Uri>) -> Unit = { _, _ -> },
    onSavePdfAnalysis: () -> Unit = {},
    onOpenKeyDialog: () -> Unit = {},
    onApiKeySave: (String) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var showKeyDialog by remember { mutableStateOf(!hasApiKey) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedUris = selectedUris + uris
    }

    // Scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty())
            listState.animateScrollToItem(messages.size - 1)
    }

    Box(modifier = Modifier.fillMaxSize().background(AcnSurface)) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AcnRed)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null,
                            tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("ACN AI", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            if (hasApiKey) "Gemini · Connected" else "No API key",
                            fontSize = 12.sp,
                            color = if (hasApiKey) Color(0xFF4CAF50) else Color(0xFFFFC107)
                        )
                    }
                }
                Row {
                    // PDF import
                    IconButton(onClick = onSavePdfAnalysis) {
                        Icon(Icons.Default.PictureAsPdf, null,
                            tint = if (hasApiKey) AcnRed else TextSec)
                    }
                    // API key
                    IconButton(onClick = { showKeyDialog = true }) {
                        Icon(Icons.Default.Key, null,
                            tint = if (hasApiKey) Color(0xFF4CAF50) else YellowWarnA)
                    }
                }
            }

            // ── Main chat area ────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Welcome card (only when no messages)
                if (messages.isEmpty()) {
                    item { WelcomeCard(hasApiKey) }
                }

                items(messages) { msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { 30 })
                    ) {
                        when (msg) {
                            is ChatMessage.User     -> UserBubble(msg.text, msg.attachmentCount)
                            is ChatMessage.Ai       -> AiBubble(msg.text)
                            is ChatMessage.ToolCall -> ToolCallIndicator(msg.toolName, msg.description)
                            ChatMessage.Thinking    -> ThinkingIndicator()
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // ── Suggestion chips ──────────────────────────────────────────────
            if (messages.isEmpty() || messages.last() is ChatMessage.Ai) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SUGGESTION_CHIPS) { chip ->
                        SuggestionChip(
                            onClick = { onSend(chip, emptyList()); inputText = "" },
                            label = { Text(chip, fontSize = 12.sp, color = TextPrimary) },
                            border = BorderStroke(1.dp, Color(0xFF444444)),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = AcnCard
                            )
                        )
                    }
                }
            }

            // ── Input bar ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Show selected files if any
                if (selectedUris.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        items(selectedUris) { uri ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF333333))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("File", color = Color.White, fontSize = 12.sp)
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { selectedUris = selectedUris.filter { it != uri } },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach files", tint = Color(0xFF666666))
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask something...", color = Color(0xFF666666)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AcnRed,
                        unfocusedBorderColor = Color(0xFF3A3A3A),
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        cursorColor          = AcnRed,
                        focusedContainerColor   = AcnCard,
                        unfocusedContainerColor = AcnCard
                    ),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                val canSend = (inputText.isNotBlank() || selectedUris.isNotEmpty()) && !isThinking
                IconButton(
                    onClick = {
                        if (canSend) {
                            onSend(inputText.trim(), selectedUris)
                            inputText = ""
                            selectedUris = emptyList()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) AcnRed else Color(0xFF333333))
                ) {
                    Icon(
                        Icons.Default.Send, null,
                        tint = if (canSend) Color.White else Color(0xFF666666),
                        modifier = Modifier.size(20.dp)
                    )
                }
                }
            }
        }

        // ── API Key setup dialog ──────────────────────────────────────────────
        if (showKeyDialog) {
            ApiKeyDialog(
                hasExistingKey = hasApiKey,
                onDismiss = { showKeyDialog = false },
                onSave    = { key ->
                    onApiKeySave(key)
                    showKeyDialog = false
                }
            )
        }
    }
}

// ── Message bubbles ───────────────────────────────────────────────────────────
@Composable
private fun UserBubble(text: String, attachmentCount: Int = 0) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                .background(UserBubble)
                .padding(12.dp, 10.dp)
        ) {
            Column {
                if (attachmentCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$attachmentCount file(s) attached", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (text.isNotBlank()) {
                    Text(text, color = TextPrimary, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun AiBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AcnRed)
        ) {
            Icon(Icons.Default.AutoAwesome, null,
                tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(AiBubble)
                .padding(12.dp, 10.dp)
        ) {
            Text(text, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ToolCallIndicator(toolName: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(36.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E2A1E))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Build, null,
                tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(description, fontSize = 12.sp, color = Color(0xFF81C784))
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "phase"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(AcnRed)
        ) {
            Icon(Icons.Default.AutoAwesome, null,
                tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(AiBubble)
                .padding(16.dp, 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    val dotPhase = (phase + i * 0.33f) % 1f
                    val alpha    = if (dotPhase < 0.5f) dotPhase * 2f else (1f - dotPhase) * 2f
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TextSec.copy(alpha = 0.3f + alpha * 0.7f))
                    )
                }
            }
        }
    }
}

// ── Welcome card (no messages state) ─────────────────────────────────────────
@Composable
private fun WelcomeCard(hasApiKey: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null,
                    tint = AcnRed, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("ACN Planner AI", fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            if (hasApiKey) {
                Text(
                    "I know your schedule, attendance, assignments, and exams. Ask me anything.",
                    fontSize = 14.sp, color = TextSec, lineHeight = 20.sp
                )
            } else {
                Text(
                    "Connect your free Gemini API key to enable AI features. Tap the 🔑 key icon above.",
                    fontSize = 14.sp, color = TextSec, lineHeight = 20.sp
                )
            }
        }
    }
}

// ── API Key dialog ────────────────────────────────────────────────────────────
@Composable
private fun ApiKeyDialog(
    hasExistingKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyText  by remember { mutableStateOf("") }
    var showKey  by remember { mutableStateOf(false) }
    val focusReq = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusReq.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1E1E1E),
        shape            = RoundedCornerShape(16.dp),
        title = {
            Text(
                if (hasExistingKey) "Update API Key" else "Connect Gemini AI",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Enter your free Gemini API key. Your key is stored securely on this device only and never sent to any server.",
                    fontSize = 13.sp, color = TextSec, lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Gemini API key", color = TextSec) },
                    placeholder = { Text("AIza...", color = Color(0xFF555555)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusReq),
                    visualTransformation = if (showKey)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = TextSec
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = AcnRed,
                        unfocusedBorderColor    = Color(0xFF3A3A3A),
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = AcnRed,
                        focusedContainerColor   = AcnCard,
                        unfocusedContainerColor = AcnCard
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text("Get a free key at aistudio.google.com",
                    fontSize = 12.sp, color = AcnRed)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (keyText.isNotBlank()) onSave(keyText.trim()) },
                enabled = keyText.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = AcnRed)
            ) { Text("Save & Connect") }
        },
        dismissButton = {
            if (hasExistingKey) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSec)
                }
            }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private val YellowWarnA = Color(0xFFFFC107)

private fun sampleMessages(): List<ChatMessage> = listOf(
    ChatMessage.User("What's my next class?"),
    ChatMessage.ToolCall("get_next_class", "Checking your timetable..."),
    ChatMessage.Ai("Your next class is **Network Security** at 10:00 AM in AB2-304.\n\nIt starts in 38 minutes. Dr. Kumar is the faculty."),
    ChatMessage.User("Am I safe to skip IoT tomorrow?"),
    ChatMessage.ToolCall("get_attendance_what_if", "Simulating attendance..."),
    ChatMessage.Ai("Not recommended. IoT (19CSE446) is currently at 73.1% — already below 75%.\n\nSkipping one more will drop you to 70.4%. You need to attend the next 3 consecutive classes to recover above 75%."),
)

