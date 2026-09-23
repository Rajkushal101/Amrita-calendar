package acn.amrita.chen.planner.workspace

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.json.JSONObject
import androidx.compose.ui.graphics.Color
import acn.amrita.chen.planner.ui.theme.*
import acn.amrita.chen.planner.ui.components.*

@Composable
fun GroupsPage(vm: WorkspaceViewModel, open: (String) -> Unit) {
    val groups by vm.repo.groups.collectAsState()
    var create by remember { mutableStateOf(false) }
    var join by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    val preferences by vm.repo.groupPreferences.collectAsState()
    val counts by vm.repo.messageCounts.collectAsState()

    Page {
        AcnSectionHeader(
            title = "Class workspaces",
            subtitle = "Collaborate on coursework, announcements, and verified academic updates."
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AcnButton(
                onClick = { value = ""; create = true },
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Add
            ) {
                Text("Create group", fontWeight = FontWeight.SemiBold)
            }
            AcnOutlinedButton(
                onClick = { value = ""; join = true },
                modifier = Modifier.weight(1f),
                icon = Icons.Default.GroupAdd
            ) {
                Text("Join with code", fontWeight = FontWeight.SemiBold)
            }
        }

        if (groups.isEmpty()) {
            AcnEmptyState(
                title = "No class groups yet",
                description = "Sign in with a verified university email, then create a group or request access using a 6-digit invitation code.",
                icon = Icons.Default.Groups
            )
        } else {
            groups.forEach { g ->
                val groupId = g.getString("id")
                val unread = ((counts[groupId] ?: 0) - (preferences[groupId]?.optLong("lastReadSequence") ?: 0)).coerceAtLeast(0)
                val status = g.optString("status")
                val role = g.optString("role")
                val isApproved = status == "approved"

                AcnCard(
                    onClick = if (isApproved) { { open(groupId) } } else null,
                    borderColor = if (isApproved) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = g.optString("name"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AcnBadge(
                                text = role.replaceFirstChar { it.uppercase() },
                                style = when (role) {
                                    "owner" -> AcnBadgeStyle.PRIMARY
                                    "administrator", "publisher" -> AcnBadgeStyle.SHARED
                                    else -> AcnBadgeStyle.NEUTRAL
                                }
                            )
                            if (status != "approved") {
                                AcnBadge(
                                    text = status.replaceFirstChar { it.uppercase() },
                                    style = AcnBadgeStyle.CAUTION
                                )
                            }
                            if (unread > 0) {
                                AcnBadge(
                                    text = "$unread new",
                                    style = AcnBadgeStyle.PRIMARY
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isApproved) "Tap to open class chat, syllabus topics, and review queue." else "Membership pending administrator approval.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (create || join) {
        AlertDialog(
            onDismissRequest = { create = false; join = false },
            title = {
                Text(
                    text = if (create) "Create class group" else "Request group membership",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AcnTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = if (create) "Class group name" else "Invitation code (12 hex digits)"
                    )
                    Text(
                        text = if (create) "You become the group owner with authority to approve members and appoint publishers. Maximum 150 members."
                        else "Enter the invite code shared by your class representative or faculty. An administrator must approve your request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                AcnButton(
                    enabled = value.isNotBlank(),
                    onClick = {
                        if (create) vm.createGroup(value) else vm.joinGroup(value)
                        create = false
                        join = false
                    }
                ) {
                    Text(if (create) "Create" else "Request access")
                }
            },
            dismissButton = {
                TextButton(onClick = { create = false; join = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GroupWorkspace(
    vm: WorkspaceViewModel,
    group: String,
    records: List<AcademicRecord>,
    edit: (EditRequest) -> Unit,
    openCourse: (String) -> Unit,
    ask: (AgentContext) -> Unit
) {
    val messages by vm.repo.messages.collectAsState()
    val members by vm.repo.members.collectAsState()
    val files by vm.repo.files.collectAsState()
    val pending by vm.pending.collectAsState()
    val progress by vm.repo.uploadProgress.collectAsState()
    val groups by vm.repo.groups.collectAsState()
    val context = LocalContext.current

    val role = groups.find { it.optString("id") == group }?.optString("role") ?: "member"
    val publisher = role in listOf("owner", "administrator", "publisher")
    val admin = role in listOf("owner", "administrator")

    var tab by rememberSaveable(group) { mutableStateOf("Chat") }
    var text by rememberSaveable(group) { mutableStateOf("") }
    var selected by remember(group) { mutableStateOf(setOf<String>()) }
    var selectedFiles by remember(group) { mutableStateOf(setOf<String>()) }
    var composeFiles by remember(group) { mutableStateOf(listOf<JSONObject>()) }
    var reply by remember(group) { mutableStateOf("") }

    val preferences by vm.repo.groupPreferences.collectAsState()
    val muted = preferences[group]?.optBoolean("muted") ?: false

    LaunchedEffect(group, tab, messages) {
        if (tab == "Chat" && messages.isNotEmpty()) {
            vm.action { vm.repo.markRead(group, messages.maxOf { it.optLong("sequence") }) }
        }
    }

    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var editMessage by remember { mutableStateOf<JSONObject?>(null) }
    var editedText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.action {
            composeFiles = composeFiles + vm.repo.upload(uri, group)
        }
    }

    fun open(file: JSONObject) {
        vm.action {
            val local = vm.repo.openAttachment(file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", local)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, file.optString("mime"))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Horizontal Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Chat", "Files", "Course Updates", "Review Queue", "Members").forEach { label ->
                FilterChip(
                    selected = tab == label,
                    onClick = { tab = label },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AcnBrandCrimson,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        when (tab) {
            "Chat" -> {
                // ANCHORED CHAT LAYOUT: LazyColumn for messages + Anchored Composer at bottom
                Column(Modifier.fillMaxSize()) {
                    // Action strip: Load older + Extract selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { vm.action { vm.repo.loadOlder() } },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson
                            )
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Older messages", style = MaterialTheme.typography.labelSmall)
                        }
                        if (selected.isNotEmpty()) {
                            AcnButton(
                                onClick = {
                                    val attachmentIds = messages.filter { it.optString("id") in selected }.flatMap { m ->
                                        val a = m.optJSONArray("attachmentIds")
                                        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
                                    }
                                    ask(AgentContext(groupId = group, messageIds = selected.toList(), attachmentIds = attachmentIds))
                                },
                                icon = Icons.Default.AutoAwesome
                            ) {
                                Text("Extract (${selected.size})", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Message List
                    val listState = rememberLazyListState()
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AcnEmptyState(
                                title = "Start the class discussion",
                                description = "Share updates, deadlines, or attach syllabus files. Messages appear once acknowledged.",
                                icon = Icons.Default.Forum
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.getString("id") }) { m ->
                                val id = m.getString("id")
                                val isMine = m.optString("sender") == vm.owner.value
                                val isDeleted = m.optBoolean("deleted")
                                val isPinned = m.optBoolean("pinned")

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isMine) 16.dp else 4.dp,
                                            bottomEnd = if (isMine) 4.dp else 16.dp
                                        ),
                                        color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Header
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = id in selected,
                                                        onCheckedChange = { checked ->
                                                            selected = if (checked) selected + id else selected - id
                                                        },
                                                        enabled = !isDeleted,
                                                        modifier = Modifier.size(20.dp),
                                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = m.optString("senderName", "Student"),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isMine) (if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson) else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                if (isPinned) {
                                                    AcnBadge(text = "Pinned", style = AcnBadgeStyle.PRIMARY, icon = Icons.Default.PushPin)
                                                }
                                            }

                                            // Reply context
                                            if (m.optString("replyTo").isNotBlank()) {
                                                val repliedMsg = messages.find { it.optString("id") == m.optString("replyTo") }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "Reply to: ${repliedMsg?.optString("text")?.take(60) ?: m.optString("replyTo")}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(6.dp)
                                                    )
                                                }
                                            }

                                            // Main Text
                                            Text(
                                                text = if (isDeleted) "Message removed" else m.optString("text"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                            )

                                            // Attachment Buttons
                                            val attachments = m.optJSONArray("attachmentIds")
                                            if (attachments != null) {
                                                for (i in 0 until attachments.length()) {
                                                    files.find { it.optString("id") == attachments.getString(i) }?.let { f ->
                                                        OutlinedButton(
                                                            onClick = { open(f) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(f.optString("name"), style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            }

                                            // Reactions
                                            val reactions = m.optJSONObject("reactions")
                                            if (reactions != null) {
                                                val rxText = reactions.keys().asSequence().map { reactions.optString(it) }.filter { it.isNotBlank() }.joinToString(" ")
                                                if (rxText.isNotBlank()) {
                                                    AcnBadge(text = rxText, style = AcnBadgeStyle.NEUTRAL)
                                                }
                                            }

                                            // Action Buttons
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                TextButton(onClick = { reply = id }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text("Reply", style = MaterialTheme.typography.labelSmall)
                                                }
                                                TextButton(onClick = { vm.messageAction(group, id, "react", mapOf("reaction" to "👍")) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text("👍 Like", style = MaterialTheme.typography.labelSmall)
                                                }
                                                if (isMine && !isDeleted) {
                                                    TextButton(onClick = { editMessage = m; editedText = m.optString("text") }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                                if (publisher) {
                                                    TextButton(onClick = { vm.messageAction(group, id, "pin", mapOf("pinned" to !isPinned)) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                                        Text(if (isPinned) "Unpin" else "Pin", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                                if (isMine || admin) {
                                                    TextButton(onClick = { confirmation = "Remove this message? Academic facts derived from it will be flagged for review." to { vm.messageAction(group, id, "delete") } }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pending writes banner in chat
                    pending.filter { it.operation == "sendMessage" && JSONObject(it.payload).optString("groupId") == group }.forEach { pWrite ->
                        AcnNoticeBanner(
                            message = "Pending message: ${JSONObject(pWrite.payload).optString("text")}",
                            actionLabel = "Retry",
                            onAction = { vm.retry() }
                        )
                    }

                    // ANCHORED COMPOSER BAR
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Reply indicator bar
                            if (reply.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Replying to message", style = MaterialTheme.typography.labelMedium, color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson)
                                    IconButton(onClick = { reply = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Attached files preview chips
                            if (composeFiles.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    composeFiles.forEach { f ->
                                        AcnBadge(text = f.optString("name"), style = AcnBadgeStyle.SHARED, icon = Icons.Default.AttachFile)
                                    }
                                }
                            }

                            // Upload Progress
                            progress?.let {
                                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                            }

                            // Input row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain")) },
                                    enabled = composeFiles.size < 5 && progress == null
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "Attach file", tint = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson)
                                }

                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    placeholder = { Text("Message your class…", style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(20.dp),
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        vm.send(group, text, composeFiles.map { it.getString("id") }, reply)
                                        text = ""
                                        composeFiles = emptyList()
                                        reply = ""
                                    },
                                    enabled = text.isNotBlank() || composeFiles.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = if (text.isNotBlank() || composeFiles.isNotEmpty()) (if (MaterialTheme.colorScheme.background == AcnDarkBackground) Color.White else AcnBrandCrimson) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "Files" -> {
                Page {
                    AcnSectionHeader(title = "Shared class files", subtitle = "Attachments posted in this class group")
                    if (files.isEmpty()) {
                        AcnEmptyState(
                            title = "No files uploaded",
                            description = "Attachments posted in class chat appear here for easy reference.",
                            icon = Icons.Default.FolderOpen
                        )
                    } else {
                        files.forEach { f ->
                            AcnCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(
                                            checked = f.getString("id") in selectedFiles,
                                            onCheckedChange = { checked ->
                                                selectedFiles = if (checked) selectedFiles + f.getString("id") else selectedFiles - f.getString("id")
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(text = f.optString("name"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(text = "${f.optLong("size") / 1024} KB · ${f.optString("mime")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    AcnOutlinedButton(onClick = { open(f) }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                    if (selectedFiles.isNotEmpty()) {
                        AcnButton(
                            onClick = { ask(AgentContext(groupId = group, attachmentIds = selectedFiles.toList())) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.AutoAwesome
                        ) {
                            Text("Extract selected files with AI", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            "Course Updates" -> {
                Page {
                    AcnSectionHeader(title = "Published courses", subtitle = "Official academic courses shared with this group")
                    val courses = records.filter { it.kind == "COURSE" && it.groupId == group }
                    if (publisher) {
                        AcnButton(
                            onClick = { edit(EditRequest("COURSE", groupId = group)) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.Add
                        ) {
                            Text("Add shared course", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (courses.isEmpty()) {
                        AcnEmptyState(
                            title = "No published courses",
                            description = "An administrator or publisher can add courses for the class.",
                            icon = Icons.Default.MenuBook
                        )
                    } else {
                        courses.forEach { c ->
                            AcnCard(onClick = { openCourse(c.id) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = c.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    AcnBadge(text = c.json().optString("code"), style = AcnBadgeStyle.PRIMARY)
                                }
                                val privateCourses = records.filter { it.kind == "COURSE" && it.groupId.isBlank() }
                                val mapping = personal(records, c.id).optString("mapping")
                                Text(
                                    text = if (mapping.isBlank()) "Not linked to your subjects" else "Linked to your personal subjects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (mapping.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                                Choice("Map to my semester", listOf("Enroll as a course") + privateCourses.map { it.title }) { choice ->
                                    vm.personal(c.id, personal(records, c.id).put("mapping", if (choice == "Enroll as a course") "enrolled" else privateCourses.first { it.title == choice }.id))
                                }
                            }
                        }
                    }

                    AcnSectionHeader(title = "Recent updates", subtitle = "Assessments and announcements")
                    records.filter { it.groupId == group && it.kind !in listOf("COURSE", "PROPOSAL") }.take(30).forEach { r ->
                        AcnCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AcnBadge(text = r.kind.replace('_', ' '), style = AcnBadgeStyle.SHARED)
                                Text(text = dateLabel(r.json()), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(text = r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            SourceLine(vm, r)
                            if (publisher) {
                                TextButton(onClick = { edit(EditRequest(r.kind, r.courseId, group, r)) }) {
                                    Text("Edit published details")
                                }
                            }
                        }
                    }
                }
            }

            "Review Queue" -> {
                Page {
                    AcnSectionHeader(
                        title = "Proposal review queue",
                        subtitle = if (publisher) "Review and publish academic proposals to the class." else "Proposals submitted for publisher review."
                    )
                    ReviewList(vm, records.filter { it.kind == "PROPOSAL" && it.groupId == group }, publisher)
                }
            }

            "Members" -> {
                Page {
                    AcnSectionHeader(title = "Group settings", subtitle = "Notification and membership preferences")
                    AcnCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mute group notifications", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = muted,
                                onCheckedChange = { value -> vm.action { vm.repo.groupPreference(group, value) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    if (admin) {
                        AcnOutlinedButton(
                            onClick = { vm.rotate(group) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.VpnKey
                        ) {
                            Text("Generate new invitation code", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    AcnSectionHeader(title = "Members (${members.size})", subtitle = "Approved students and administrators")
                    members.forEach { m ->
                        val memberId = m.optString("id")
                        val mRole = m.optString("role")
                        val mStatus = m.optString("status")

                        AcnCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = m.optString("name", "Student"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AcnBadge(
                                        text = mRole.replaceFirstChar { it.uppercase() },
                                        style = when (mRole) {
                                            "owner" -> AcnBadgeStyle.PRIMARY
                                            "administrator", "publisher" -> AcnBadgeStyle.SHARED
                                            else -> AcnBadgeStyle.NEUTRAL
                                        }
                                    )
                                    AcnBadge(
                                        text = mStatus.replaceFirstChar { it.uppercase() },
                                        style = if (mStatus == "approved") AcnBadgeStyle.SUCCESS else AcnBadgeStyle.CAUTION
                                    )
                                }
                            }

                            if (admin && memberId != vm.owner.value && mRole != "owner") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (mStatus == "pending") {
                                        AcnButton(
                                            onClick = { vm.manage(group, memberId, "approve") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Approve")
                                        }
                                    }
                                    if (role == "owner" && mStatus == "approved") {
                                        Choice("Role", listOf("member", "publisher", "administrator")) {
                                            vm.manage(group, memberId, "role", it)
                                        }
                                        TextButton(onClick = {
                                            confirmation = "Transfer group ownership to this member?" to { vm.manage(group, memberId, "transfer") }
                                        }) {
                                            Text("Transfer")
                                        }
                                    }
                                    TextButton(onClick = {
                                        confirmation = "Remove this member and revoke group access?" to { vm.manage(group, memberId, "remove") }
                                    }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmation?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Confirm action", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = { Text(c.first, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                AcnButton(onClick = {
                    c.second()
                    confirmation = null
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    editMessage?.let { m ->
        AlertDialog(
            onDismissRequest = { editMessage = null },
            title = { Text("Edit message", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                AcnTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    label = "Message text",
                    singleLine = false
                )
            },
            confirmButton = {
                AcnButton(
                    enabled = editedText.isNotBlank(),
                    onClick = {
                        vm.messageAction(group, m.getString("id"), "edit", mapOf("text" to editedText))
                        editMessage = null
                    }
                ) {
                    Text("Save edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { editMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
