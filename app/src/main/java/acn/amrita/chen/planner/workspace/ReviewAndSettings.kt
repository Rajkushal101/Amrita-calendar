package acn.amrita.chen.planner.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import acn.amrita.chen.planner.ui.theme.*
import acn.amrita.chen.planner.ui.components.*

@Composable
fun InboxPage(vm: WorkspaceViewModel, records: List<AcademicRecord>) {
    val pending by vm.pending.collectAsState()
    val groups by vm.repo.groups.collectAsState()
    var conflict by remember { mutableStateOf<Triple<PendingWrite, JSONObject, Set<String>>?>(null) }
    var repair by remember { mutableStateOf<AcademicRecord?>(null) }

    Page {
        AcnSectionHeader(
            title = "Review & synchronization",
            subtitle = "Manage pending changes, imported records, and published proposals."
        )

        // Repair records
        records.filter { it.kind == "REPAIR" }.forEach { r ->
            AcnCard(borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = r.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AcnBadge(text = "Action required", style = AcnBadgeStyle.ERROR)
                }
                Text(
                    text = "Imported record needs a course mapping or corrected details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AcnOutlinedButton(
                    onClick = { repair = r },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Repair imported record", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Pending outbox changes
        if (pending.isNotEmpty()) {
            AcnCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pending.size} pending change(s)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AcnBadge(text = "Outbox", style = AcnBadgeStyle.PRIMARY, icon = Icons.Default.CloudSync)
                }
                Text(
                    text = "Changes are saved locally. Cloud synchronization completes after server acknowledgement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))
                pending.forEach { write ->
                    val errorText = userFacingError(write.error)
                    val isError = write.error.isNotBlank()

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = write.operation,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                AcnBadge(
                                    text = if (isError) "Conflict / Error" else "Queued",
                                    style = if (isError) AcnBadgeStyle.CAUTION else AcnBadgeStyle.NEUTRAL
                                )
                            }
                            Text(
                                text = errorText.ifBlank { "Waiting to synchronize with server" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isError) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (write.operation == "commitRecords") {
                                        OutlinedButton(
                                            onClick = {
                                                val target = JSONObject(write.payload).getJSONArray("changes").getJSONObject(0).getString("id")
                                                val pIds = pending.filter { target in affectedRecordIds(it) }.map { it.id }.toSet()
                                                vm.action { conflict = Triple(write, vm.repo.conflictSnapshot(write), pIds) }
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Review conflict", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    TextButton(onClick = { vm.discardPending(write) }) {
                                        Text("Discard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                AcnButton(
                    onClick = { vm.retry() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Refresh
                ) {
                    Text("Retry synchronization", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Academic proposals
        val proposals = records.filter { it.kind == "PROPOSAL" }
        AcnSectionHeader(
            title = "Proposals (${proposals.size})",
            subtitle = "Academic changes generated from class messages or syllabus imports"
        )
        if (proposals.isEmpty()) {
            AcnEmptyState(
                title = "Nothing to review",
                description = "Ask the assistant to prepare an import or extract class updates. Proposed changes arrive here.",
                icon = Icons.Default.RateReview
            )
        } else {
            proposals.forEach { p ->
                ReviewList(
                    vm = vm,
                    proposals = listOf(p),
                    canApprove = p.groupId.isBlank() || groups.find { it.optString("id") == p.groupId }?.optString("role") in listOf("owner", "administrator", "publisher")
                )
            }
        }

        // Academic notices
        val notices = records.filter { it.kind == "NOTICE" }
        if (notices.isNotEmpty()) {
            AcnSectionHeader(title = "Academic notices", subtitle = "Official announcements and schedule changes")
            notices.forEach { item ->
                AcnCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AcnBadge(text = dateLabel(item.json()), style = AcnBadgeStyle.NEUTRAL)
                    }
                    if (item.json().optString("content").isNotBlank()) {
                        Text(item.json().optString("content"), style = MaterialTheme.typography.bodyMedium)
                    }
                    SourceLine(vm, item)
                }
            }
        }

        // Change history
        val historyItems = records.filter { it.kind == "HISTORY" }.take(20)
        if (historyItems.isNotEmpty()) {
            AcnSectionHeader(title = "Recent change history", subtitle = "Locally preserved historical revisions")
            historyItems.forEach { h ->
                AcnCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(h.title.ifBlank { "Academic Record" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        AcnBadge(text = "Rev ${h.revision}", style = AcnBadgeStyle.NEUTRAL)
                    }
                    Text(
                        text = "Preserved on ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(h.updatedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    repair?.let { r ->
        AcademicEditor(vm, EditRequest(r.json().optString("recordKind"), r.courseId, existing = r), records) {
            repair = null
        }
    }

    // HUMAN-READABLE CONFLICT RESOLUTION DIALOG (NO RAW JSON DISPLAY!)
    conflict?.let { (write, server, reviewedPendingIds) ->
        ConflictResolutionDialog(
            write = write,
            server = server,
            reviewedPendingIds = reviewedPendingIds,
            records = records,
            onDismiss = { conflict = null },
            onResolve = { keepLocal ->
                val id = JSONObject(write.payload).getJSONArray("changes").getJSONObject(0).getString("id")
                val local = records.find { it.id == id }
                vm.action {
                    if (local != null) vm.repo.resolveConflict(write, server, local, keepLocal, reviewedPendingIds)
                    conflict = null
                    vm.repo.retryPending()
                }
            }
        )
    }
}

@Composable
fun ConflictResolutionDialog(
    write: PendingWrite,
    server: JSONObject,
    reviewedPendingIds: Set<String>,
    records: List<AcademicRecord>,
    onDismiss: () -> Unit,
    onResolve: (keepLocal: Boolean) -> Unit
) {
    val id = JSONObject(write.payload).getJSONArray("changes").getJSONObject(0).getString("id")
    val local = records.find { it.id == id }
    val localJson = local?.json() ?: JSONObject()
    val serverData = server.optJSONObject("data") ?: JSONObject()
    val isDeleted = server.optBoolean("deleted")
    var showRaw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Resolve record conflict",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "The server version has changed since your edit was queued. Review the comparison below and select which version to preserve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Local Version Card
                AcnCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Your local version", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson)
                        AcnBadge(text = "Revision ${local?.revision ?: 1}", style = AcnBadgeStyle.PRIMARY)
                    }
                    Text("Title: ${localJson.optString("title", "Untitled")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val date = localJson.optString("date")
                    if (date.isNotBlank() && date != "null") {
                        Text("Date: $date ${localJson.optString("time").takeUnless { it.isBlank() || it == "null" }?.let { "· $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (localJson.optString("content").isNotBlank()) {
                        Text("Content: ${localJson.optString("content")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Server Version Card
                AcnCard(borderColor = MaterialTheme.colorScheme.outline) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Server version", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        AcnBadge(
                            text = if (isDeleted) "Deleted remotely" else "Revision ${server.optLong("revision", 1)}",
                            style = if (isDeleted) AcnBadgeStyle.ERROR else AcnBadgeStyle.NEUTRAL
                        )
                    }
                    if (isDeleted) {
                        Text("This record was deleted on the server.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Title: ${serverData.optString("title", "Untitled")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        val sDate = serverData.optString("date")
                        if (sDate.isNotBlank() && sDate != "null") {
                            Text("Date: $sDate ${serverData.optString("time").takeUnless { it.isBlank() || it == "null" }?.let { "· $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (serverData.optString("content").isNotBlank()) {
                            Text("Content: ${serverData.optString("content")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Notice text
                Text(
                    text = "This resolves the ${reviewedPendingIds.size.coerceAtLeast(1)} reviewed pending edit(s) for this record. A copy of your local version stays in private history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Optional technical details collapsible
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide technical details" else "Show technical details", style = MaterialTheme.typography.labelSmall)
                }
                if (showRaw) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Local payload:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(local?.payload ?: "null", style = MaterialTheme.typography.bodySmall)
                            Text("Server data:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(serverData.toString(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            AcnButton(
                enabled = local != null && !isDeleted,
                onClick = { onResolve(true) }
            ) {
                Text("Use reviewed local version")
            }
        },
        dismissButton = {
            AcnOutlinedButton(
                onClick = { onResolve(false) }
            ) {
                Text("Keep server version")
            }
        }
    )
}

@Composable
fun ReviewList(vm: WorkspaceViewModel, proposals: List<AcademicRecord>, canApprove: Boolean) {
    var opened by remember { mutableStateOf<AcademicRecord?>(null) }
    proposals.sortedByDescending { it.updatedAt }.forEach { p ->
        val j = p.json()
        val answer = j.optString("answer").take(180).ifBlank { "Academic proposal" }
        val status = j.optString("status")
        val isPending = status == "pending"

        AcnCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AcnBadge(
                    text = if (p.groupId.isBlank()) "Private workspace" else "Shared class proposal",
                    style = if (p.groupId.isBlank()) AcnBadgeStyle.NEUTRAL else AcnBadgeStyle.SHARED
                )
                AcnBadge(
                    text = status.replaceFirstChar { it.uppercase() },
                    style = if (isPending) AcnBadgeStyle.PRIMARY else AcnBadgeStyle.SUCCESS
                )
            }
            Text(
                text = answer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${j.optJSONArray("changes")?.length() ?: 0} proposed academic changes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (j.optBoolean("sourceNeedsReview")) {
                AcnNoticeBanner(message = "Source changed — extract again", icon = Icons.Default.Warning)
            }

            if (isPending) {
                AcnButton(
                    onClick = { opened = p },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Visibility
                ) {
                    Text(if (canApprove) "Review & approve changes" else "View proposal details", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    opened?.let { ProposalReview(vm, it, canApprove) { opened = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalReview(vm: WorkspaceViewModel, proposal: AcademicRecord, canApprove: Boolean, close: () -> Unit) {
    val records by vm.records.collectAsState()
    val original = proposal.json()
    var changes: List<JSONObject> by remember(proposal.id) {
        mutableStateOf(original.optJSONArray("changes")?.let { a -> (0 until a.length()).map { JSONObject(a.getJSONObject(it).toString()) } } ?: emptyList())
    }
    var selected: Set<String> by remember(proposal.id) { mutableStateOf(changes.map { it.getString("id") }.toSet()) }
    var resolved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val questions = original.optJSONArray("questions") ?: JSONArray()

    fun modify(index: Int, key: String, value: String) {
        changes = changes.mapIndexed { i, c ->
            if (i != index) c else JSONObject(c.toString()).apply { getJSONObject("data").put(key, value.ifBlank { null }) }
        }
    }

    fun modifyValue(index: Int, key: String, value: Any) {
        changes = changes.mapIndexed { i, c ->
            if (i != index) c else JSONObject(c.toString()).apply { getJSONObject("data").put(key, value) }
        }
    }

    androidx.activity.compose.BackHandler(onBack = close)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Review academic changes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        TextButton(onClick = close) {
                            Text("Close", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AcnCard {
                    Text(
                        text = "Destination: ${if (proposal.groupId.isBlank()) "Your private workspace" else "Shared class course records"}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = original.optString("answer"), style = MaterialTheme.typography.bodyMedium)
                }

                val sources = original.optJSONArray("sources") ?: JSONArray()
                SourceEvidence(vm, proposal.groupId, sources)

                for (i in 0 until questions.length()) {
                    AcnNoticeBanner(
                        message = "Question: ${questions.getString(i)}",
                        icon = Icons.Default.HelpOutline
                    )
                }

                changes.forEachIndexed { i, c ->
                    val data = c.getJSONObject("data")
                    val old = records.find { it.id == c.getString("id") }
                    val changeId = c.getString("id")

                    AcnCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AcnBadge(
                                text = "${c.getString("action")} · ${c.getString("kind").replace('_', ' ')}",
                                style = AcnBadgeStyle.PRIMARY
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = changeId in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + changeId else selected - changeId
                                    },
                                    enabled = canApprove,
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Text("Include", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (old != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Current saved version:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text("${old.title} · ${dateLabel(old.json())}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Field("Title after approval", data.optString("title"), { modify(i, "title", it) })
                        Field("Details after approval", data.optString("content"), { modify(i, "content", it) }, false)
                        Field("Date (YYYY-MM-DD; blank = unknown)", data.optString("date").takeUnless { it == "null" } ?: "", { modify(i, "date", it) })
                        Field("Time (HH:mm; blank = unknown)", data.optString("time").takeUnless { it == "null" } ?: "", { modify(i, "time", it) })

                        if (c.getString("kind") == "COURSE") {
                            Field("Course code", data.optString("code"), { modify(i, "code", it) })
                            Field("Semester", data.optString("semester"), { modifyValue(i, "semester", it.toIntOrNull() ?: 0) })
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = resolved,
                        onCheckedChange = { resolved = it },
                        enabled = canApprove,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "I have checked the source, verified details, and resolved questions.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (error.isNotBlank()) {
                    AcnNoticeBanner(message = error, icon = Icons.Default.ErrorOutline)
                }

                AcnButton(
                    enabled = canApprove && resolved && selected.isNotEmpty() && !original.optBoolean("sourceNeedsReview"),
                    onClick = {
                        try {
                            val chosen = changes.filter { it.getString("id") in selected }
                            chosen.forEach { AcademicValidation.validate(it.getString("kind"), it.getJSONObject("data")) }
                            vm.approve(proposal, JSONArray(chosen), resolved)
                            close()
                        } catch (e: Exception) {
                            error = e.message ?: "Check the proposal"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Approve selected changes", fontWeight = FontWeight.SemiBold)
                }

                if (canApprove) {
                    AcnOutlinedButton(
                        onClick = {
                            vm.reject(proposal)
                            close()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reject proposal", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

private fun getUriDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
    }
    return name ?: uri.lastPathSegment ?: "File"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSheet(vm: WorkspaceViewModel, agentContext: AgentContext, close: () -> Unit) {
    val context = LocalContext.current
    val stage by vm.agentStage.collectAsState()
    val message by vm.message.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val selectedModel by vm.selectedModel.collectAsState()
    val records by vm.records.collectAsState()
    val shared by SharedImports.uris.collectAsState()

    var text by remember { mutableStateOf(SharedImports.text.value) }
    var attachments by remember { mutableStateOf(shared) }
    var messageDraft by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        attachments = (attachments + it).distinct().take(5)
    }

    androidx.activity.compose.BackHandler(onBack = close)

    val chatMessages = remember(records) {
        records.filter {
            it.kind == "CHAT" &&
            !it.title.trim().startsWith("{\"tool\"") &&
            !it.title.trim().startsWith("{\"action\"")
        }.sortedBy { it.updatedAt }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Academic assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            AcnBadge(
                                text = selectedModel.ifBlank { "gemini-3.6-flash" },
                                style = AcnBadgeStyle.PRIMARY,
                                icon = Icons.Default.AutoAwesome
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = close) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (chatMessages.isNotEmpty()) {
                            IconButton(onClick = { vm.clearChat() }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (!hasKey) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        AcnEmptyState(
                            title = "Connect your personal key",
                            description = "Open Settings to save your Google AI Studio API key and connect Gemini.",
                            icon = Icons.Default.Key
                        )
                    }
                }

                // Chat Messages Thread
                val listState = rememberLazyListState()
                LaunchedEffect(chatMessages.size, stage) {
                    if (chatMessages.isNotEmpty()) {
                        listState.animateScrollToItem(chatMessages.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Empty state welcome card
                    if (chatMessages.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(AcnBrandCrimson.copy(alpha = 0.12f), RoundedCornerShape(26.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = AcnBrandCrimson,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Text(
                                        text = "Amrita Academic Assistant",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Powered by Gemini 3.6 Flash. I analyze your registered courses, AUMS attendance, and timetable to answer questions or help organize your schedule.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Conversation items
                    items(chatMessages, key = { it.id }) { c ->
                        val json = c.json()
                        val isUser = json.optString("role") == "user"
                        val userFiles = json.optJSONArray("files")

                        if (isUser) {
                            // User message bubble (Right-aligned)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
                                    color = AcnBrandCrimson,
                                    contentColor = Color.White,
                                    modifier = Modifier.widthIn(max = 310.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Show uploaded file tags
                                        if (userFiles != null && userFiles.length() > 0) {
                                            for (i in 0 until userFiles.length()) {
                                                val fn = userFiles.optString(i)
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.White.copy(alpha = 0.22f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                                        Text(fn, style = MaterialTheme.typography.labelSmall, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = c.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            // Assistant message bubble (Left-aligned)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                                    color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) Color(0xFF26191E) else Color(0xFFFAF2F5),
                                    border = BorderStroke(1.dp, if (MaterialTheme.colorScheme.background == AcnDarkBackground) Color(0xFF42232F) else Color(0xFFF0D5DF)),
                                    modifier = Modifier.fillMaxWidth(0.94f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = AcnBrandCrimson,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                "Assistant",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson
                                            )
                                        }

                                        Text(
                                            text = c.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (agentContext.groupId.isNotBlank()) {
                                            TextButton(
                                                onClick = { messageDraft = c.title },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Share in class group", style = MaterialTheme.typography.labelSmall, color = AcnBrandCrimson)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // In-progress Thinking indicator
                    val isGenerating = stage.isNotBlank() && stage !in listOf("Failed — no academic changes applied", "Answer ready", "Awaiting review in Inbox")
                    if (isGenerating) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = AcnBrandCrimson
                                        )
                                        Text(stage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { vm.cancelAgent() }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                            Text("Cancel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Generation error
                    if (stage.contains("Failed", ignoreCase = true)) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Text("Generation failed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                    if (message.isNotBlank()) {
                                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    }
                }

                // Pinned Bottom Chat Input Bar (imePadding + navigationBarsPadding)
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Attached files preview row
                        if (attachments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                attachments.forEach { uri ->
                                    val fileName = getUriDisplayName(context, uri)
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                fileName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = AcnBrandCrimson
                                            )
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { attachments = attachments - uri },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove file",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Quick suggestion chips (if conversation has <= 2 messages and no attachments staged)
                        if (chatMessages.size <= 2 && attachments.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SuggestionChip(
                                    onClick = { text = "What is my current attendance for all subjects?" },
                                    label = { Text("📊 Check attendance", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { text = "Am I safe to skip any classes? Which subjects have good attendance?" },
                                    label = { Text("⚠️ Safe to skip class?", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { text = "List all my subjects and courses for Semester 7" },
                                    label = { Text("📚 Semester 7 subjects", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { text = "Update my academic calendar with upcoming tests and events" },
                                    label = { Text("📅 Update calendar", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        // 3. Unified Single-Line Input Row: [Files Option] [Text Box] [Send Arrow]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain")) },
                                enabled = hasKey && attachments.size < 5
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = "Attach file",
                                    tint = if (attachments.isNotEmpty()) AcnBrandCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text("Ask academic assistant...", style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AcnBrandCrimson,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )

                            val isGenerating = stage.isNotBlank() && stage !in listOf("Failed — no academic changes applied", "Answer ready", "Awaiting review in Inbox")
                            val canSend = hasKey && (text.isNotBlank() || attachments.isNotEmpty()) && !isGenerating
                            FilledIconButton(
                                onClick = {
                                    val query = text.trim()
                                    val filesToSend = attachments
                                    text = ""
                                    attachments = emptyList()
                                    vm.runAgent(query, false, agentContext, filesToSend)
                                },
                                enabled = canSend,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = AcnBrandCrimson,
                                    contentColor = Color.White,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    messageDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { messageDraft = null },
            title = { Text("Review message before sending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = { Field("Message text", draft, { messageDraft = it }, false) },
            confirmButton = {
                AcnButton(onClick = {
                    vm.send(agentContext.groupId, draft, emptyList(), "")
                    messageDraft = null
                }) {
                    Text("Send to group")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageDraft = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsPage(vm: WorkspaceViewModel, dark: Boolean, setDark: (Boolean) -> Unit) {
    val owner by vm.owner.collectAsState()
    val profile by vm.profile.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val models by vm.availableModels.collectAsState()
    val selected by vm.selectedModel.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    var name by remember(profile) { mutableStateOf(profile.optString("name")) }
    var campus by remember(profile) { mutableStateOf(profile.optString("campus", "Amrita Chennai")) }
    var programme by remember(profile) { mutableStateOf(profile.optString("programme")) }
    var batch by remember(profile) { mutableStateOf(profile.optString("batch")) }
    var semester by remember(profile) { mutableStateOf(profile.optInt("semester", 3).toString()) }
    var section by remember(profile) { mutableStateOf(profile.optString("section")) }
    var roll by remember(profile) { mutableStateOf(profile.optString("rollNumber")) }
    var availability by remember(profile) { mutableStateOf(profile.optString("availability")) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.message.value = if (granted) "Notifications enabled" else "Notifications are disabled; your planner still works"
    }

    Page {
        AcnSectionHeader(title = "Settings", subtitle = "Account, academic identity, AI, and preferences")

        // 1. Account Section Card
        AcnCard {
            AcnSectionHeader(title = "Account", subtitle = if (owner == "legacy") "Local private device storage" else "Cloud-connected university profile")
            val isVerified = vm.repo.auth.currentUser?.isEmailVerified == true

            if (owner == "legacy") {
                Text("Sign in with your verified email to create and collaborate in class groups.", style = MaterialTheme.typography.bodySmall)
                Field("Email", email, { email = it })
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AcnButton(onClick = { vm.login(email, password, false) }, modifier = Modifier.weight(1f)) {
                        Text("Sign in")
                    }
                    AcnOutlinedButton(onClick = { vm.login(email, password, true) }, modifier = Modifier.weight(1f)) {
                        Text("Create account")
                    }
                }
                TextButton(onClick = { vm.reset(email) }) {
                    Text("Reset password")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = vm.repo.auth.currentUser?.email ?: "Signed in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    AcnBadge(
                        text = if (isVerified) "Verified" else "Unverified",
                        style = if (isVerified) AcnBadgeStyle.SUCCESS else AcnBadgeStyle.CAUTION
                    )
                }

                if (!isVerified) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AcnButton(onClick = { vm.verify() }, modifier = Modifier.weight(1f)) {
                            Text("Check status")
                        }
                        AcnOutlinedButton(onClick = { vm.resend() }, modifier = Modifier.weight(1f)) {
                            Text("Resend email")
                        }
                    }
                }

                AcnOutlinedButton(
                    onClick = { vm.signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign out & remove stored API key", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 2. Academic Profile Card
        AcnCard {
            AcnSectionHeader(title = "Academic profile", subtitle = "Identity used across courses and class groups")
            Field("Full name", name, { name = it })
            Field("Campus", campus, { campus = it })
            Field("Programme / Department", programme, { programme = it })
            Field("Batch", batch, { batch = it })
            Field("Semester (1–12)", semester, { semester = it })
            Field("Section", section, { section = it })
            Field("Roll number", roll, { roll = it })
            Field("Study availability (e.g. Mon/Wed 18:00–19:00)", availability, { availability = it }, single = false)

            AcnButton(
                onClick = {
                    semester.toIntOrNull()?.let {
                        vm.saveProfile(
                            JSONObject().put("name", name).put("campus", campus).put("programme", programme)
                                .put("batch", batch).put("semester", it).put("section", section).put("rollNumber", roll).put("availability", availability)
                        )
                    } ?: run { vm.message.value = "Enter a valid semester" }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save profile", fontWeight = FontWeight.SemiBold)
            }
        }

        // 3. Personal AI Connection (Gemini BYOK) Card
        AcnCard {
            AcnSectionHeader(title = "Gemini AI connection", subtitle = "Bring your own Google AI Studio API key")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AcnBadge(
                    text = if (hasKey) "Key connected · Keystore encrypted" else "No personal key",
                    style = if (hasKey) AcnBadgeStyle.SUCCESS else AcnBadgeStyle.NEUTRAL,
                    icon = if (hasKey) Icons.Default.Lock else Icons.Default.KeyOff
                )
            }

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Google AI Studio API Key") },
                placeholder = { Text("Paste AIzaSy... key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            AcnButton(
                enabled = key.isNotBlank(),
                onClick = { vm.connect(key); key = "" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Validate and save key", fontWeight = FontWeight.SemiBold)
            }

            if (models.isNotEmpty()) {
                Choice("Selected model: ${selected.ifBlank { "Choose model" }}", models) {
                    vm.selectModel(it)
                }
            } else if (selected.isNotBlank()) {
                Text("Selected model: $selected", style = MaterialTheme.typography.bodySmall)
            }

            if (hasKey) {
                TextButton(onClick = { vm.clearKey() }) {
                    Text("Remove stored API key", color = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                text = "Keys are encrypted locally in the Android Keystore and never persisted in cloud databases.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 4. Appearance & Notifications Card
        AcnCard {
            AcnSectionHeader(title = "Appearance & notifications", subtitle = "Device display and alert preferences")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setDark(!dark) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark theme", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = dark,
                    onCheckedChange = setDark,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                AcnOutlinedButton(
                    onClick = { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Notifications
                ) {
                    Text("Enable academic reminders")
                }
            }
        }

        // 5. Existing Data & Legacy Migration Card
        AcnCard {
            AcnSectionHeader(title = "Legacy app data", subtitle = "Import SQLite data from earlier versions")
            Text(
                text = "Copies legacy courses, syllabus, projects, events, and assignments into your workspace. Existing records remain intact.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AcnOutlinedButton(
                onClick = { vm.importLegacy() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Download
            ) {
                Text("Import existing app records privately")
            }
        }
    }
}
