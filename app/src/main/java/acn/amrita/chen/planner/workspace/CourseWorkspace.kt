package acn.amrita.chen.planner.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.compose.ui.graphics.Color
import acn.amrita.chen.planner.ui.theme.*
import acn.amrita.chen.planner.ui.components.*

data class EditRequest(
    val kind: String,
    val courseId: String = "",
    val groupId: String = "",
    val existing: AcademicRecord? = null,
    val initialDate: String = ""
)

val sections = mapOf(
    "Full Syllabus" to "SYLLABUS",
    "Midterm" to "MIDTERM",
    "End Sem" to "END_SEMESTER",
    "Quizzes" to "QUIZ",
    "Assignments" to "ASSIGNMENT",
    "Projects" to "PROJECT",
    "Resources" to "RESOURCE",
    "Attendance" to "ATTENDANCE"
)

fun formatCourseOption(c: AcademicRecord): String {
    val title = c.title.trim().ifBlank { "Untitled course" }
    val code = c.json().optString("code").trim()
    val sem = c.json().optInt("semester", 0)
    val parts = mutableListOf(title)
    if (code.isNotBlank()) parts.add(code)
    if (sem > 0) parts.add("Sem $sem")
    return parts.joinToString(" · ")
}

fun courseOptionMap(courses: List<AcademicRecord>): Map<String, String> {
    val duplicates = courses.groupingBy { formatCourseOption(it) }.eachCount()
    return courses.associate { c ->
        val base = formatCourseOption(c)
        val key = if ((duplicates[base] ?: 0) > 1) "$base (${c.id.takeLast(4)})" else base
        key to c.id
    }
}

fun resolveLinkedCourseIds(records: List<AcademicRecord>, courseId: String): Set<String> {
    if (courseId.isBlank()) return emptySet()
    val reverseMapped = records.filter { it.kind == "COURSE" && personal(records, it.id).optString("mapping") == courseId }.map { it.id }
    val directMapping = personal(records, courseId).optString("mapping").takeIf { it.isNotBlank() && it != "enrolled" }
    return setOf(courseId) + reverseMapped + listOfNotNull(directMapping)
}

fun resolveSyllabusTopics(records: List<AcademicRecord>, courseId: String): List<JSONObject> {
    val linked = resolveLinkedCourseIds(records, courseId)
    if (linked.isEmpty()) return emptyList()
    return records.filter { it.kind == "SYLLABUS" && it.courseId in linked }.flatMap { s ->
        val a = s.json().optJSONArray("topics") ?: JSONArray()
        (0 until a.length()).map { a.getJSONObject(it) }
    }
}

@Composable
fun CoursesPage(
    vm: WorkspaceViewModel,
    records: List<AcademicRecord>,
    open: (String) -> Unit,
    sync: () -> Unit,
    add: () -> Unit
) {
    val semester by vm.semester.collectAsState()
    val courses = enrolledCourses(records).filter { it.json().optInt("semester") == semester }

    Page {
        AcnSectionHeader(
            title = "Your courses",
            subtitle = "Semester $semester enrolled academic courses",
            action = {
                Choice(
                    label = "Semester $semester",
                    options = (1..12).map { "Semester $it" }
                ) {
                    vm.semester.value = it.substringAfterLast(' ').toInt()
                }
            }
        )

        // Sync Toolbar Card
        AcnCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Amrita Portal Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Import official course attendance and marks safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AcnButton(
                    onClick = sync,
                    icon = Icons.Default.Sync
                ) {
                    Text("Sync My Amrita", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (courses.isEmpty()) {
            AcnEmptyState(
                title = "Build your semester",
                description = "Sync My Amrita, add a course manually, or map a course from your class group.",
                icon = Icons.Default.School
            )
        } else {
            courses.forEach { course ->
                val p = personal(records, course.id)
                val code = course.json().optString("code")
                val total = p.optInt("total", -1)
                val attended = p.optInt("attended", 0)

                AcnCard(onClick = { open(course.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (code.isNotBlank()) {
                            AcnBadge(text = code, style = AcnBadgeStyle.PRIMARY)
                        } else {
                            AcnBadge(text = "Course", style = AcnBadgeStyle.NEUTRAL)
                        }

                        // Truthful attendance badge
                        if (!p.has("total") || total < 0) {
                            AcnBadge(text = "Not synced", style = AcnBadgeStyle.NEUTRAL)
                        } else if (total == 0) {
                            AcnBadge(text = "0 classes recorded", style = AcnBadgeStyle.NEUTRAL)
                        } else {
                            val pct = 100.0 * attended / total
                            AcnBadge(
                                text = "${"%.1f".format(pct)}% attendance",
                                style = if (pct >= 75.0) AcnBadgeStyle.SUCCESS else AcnBadgeStyle.CAUTION,
                                icon = if (pct >= 75.0) Icons.Default.CheckCircleOutline else Icons.Default.WarningAmber
                            )
                        }
                    }

                    // Full title wrapping naturally
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Attendance progress bar if synced
                    if (p.has("total") && total > 0) {
                        val progress = (attended.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = if (progress >= 0.75f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "$attended attended of $total classes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Last sync timestamp
                    if (p.optLong("syncedAt") > 0) {
                        Text(
                            text = "Last sync: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.optLong("syncedAt")))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Nearest deadline
                    val nextItem = courseItems(records, course)
                        .filter { it.json().optString("date") >= java.time.LocalDate.now().toString() }
                        .minByOrNull { it.json().optString("date") }

                    if (nextItem != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Next: ${nextItem.title} · ${dateLabel(nextItem.json())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        AcnOutlinedButton(
            onClick = add,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Add
        ) {
            Text("Add course manually", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CourseWorkspace(
    vm: WorkspaceViewModel,
    course: AcademicRecord,
    records: List<AcademicRecord>,
    edit: (EditRequest) -> Unit,
    ask: (String) -> Unit
) {
    var section by rememberSaveable(course.id) { mutableStateOf("Full Syllabus") }
    val items = courseItems(records, course)
    val syllabus = items.filter { it.kind == "SYLLABUS" }.maxByOrNull { it.updatedAt }
    val topics = syllabus?.json()?.optJSONArray("topics") ?: JSONArray()
    val personal = personal(records, course.id)

    Page {
        // Course Identity Banner
        AcnCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AcnBadge(
                    text = "${course.json().optString("code")} · Semester ${course.json().optInt("semester")}",
                    style = AcnBadgeStyle.PRIMARY
                )
                AcnBadge(
                    text = if (course.groupId.isBlank()) "Private course" else "Shared group course",
                    style = if (course.groupId.isBlank()) AcnBadgeStyle.NEUTRAL else AcnBadgeStyle.SHARED,
                    icon = if (course.groupId.isBlank()) Icons.Default.Lock else Icons.Default.Groups
                )
            }
            Text(
                text = course.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Section Selector Horizontal Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.keys.forEach { label ->
                FilterChip(
                    selected = section == label,
                    onClick = { section = if (section == label) "Full Syllabus" else label },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AcnBrandCrimson,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        if (section == "Attendance") {
            if (!personal.has("total")) {
                AcnEmptyState(
                    title = "Attendance not synced",
                    description = "Return to Subjects and import your official attendance from My Amrita.",
                    icon = Icons.Default.SyncLock
                )
            } else {
                val total = personal.optInt("total")
                val attended = personal.optInt("attended")
                var required by rememberSaveable(course.id) { mutableStateOf(personal.optInt("requiredAttendance", 75).toString()) }
                val a = AcademicValidation.attendance(attended, total, required.toIntOrNull()?.coerceIn(1, 99) ?: 75)

                AcnCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = a.percentage?.let { "${"%.1f".format(it)}%" } ?: "No classes recorded",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson
                        )
                        AcnBadge(
                            text = a.label,
                            style = if (a.needed == 0) AcnBadgeStyle.SUCCESS else AcnBadgeStyle.CAUTION
                        )
                    }
                    Text(
                        text = "$attended / $total classes attended",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Can miss: ${a.canMiss} classes · To recover: ${a.needed} consecutive classes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))
                    AcnTextField(
                        value = required,
                        onValueChange = { required = it },
                        label = "Required percentage (e.g. 75)"
                    )
                    AcnOutlinedButton(
                        onClick = {
                            required.toIntOrNull()?.takeIf { it in 1..99 }?.let {
                                vm.personal(course.id, personal.put("requiredAttendance", it))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save requirement", fontWeight = FontWeight.SemiBold)
                    }
                }

                // What-if simulator
                var futureAttend by remember { mutableIntStateOf(0) }
                var futureMiss by remember { mutableIntStateOf(0) }
                AcnCard {
                    AcnSectionHeader(
                        title = "What-if simulator",
                        subtitle = "Personal estimate; your imported attendance stays unchanged"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Attend upcoming classes:", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { futureAttend = (futureAttend - 1).coerceAtLeast(0) }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
                            }
                            Text("$futureAttend", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { futureAttend++ }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Miss upcoming classes:", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { futureMiss = (futureMiss - 1).coerceAtLeast(0) }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
                            }
                            Text("$futureMiss", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { futureMiss++ }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
                            }
                        }
                    }
                    val simTotal = total + futureAttend + futureMiss
                    val projected = if (simTotal == 0) "No classes recorded" else "Projected: ${"%.1f".format(100.0 * (attended + futureAttend) / simTotal)}%"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = projected,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        } else if (section == "Full Syllabus") {
            if (syllabus == null) {
                AcnEmptyState(
                    title = "Your syllabus belongs here",
                    description = "Upload a document with the assistant or add units and topics manually.",
                    icon = Icons.Default.MenuBook
                )
            } else {
                AcnCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Full Syllabus",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        AcnBadge(
                            text = "Version ${syllabus.revision} · ${if (syllabus.groupId.isBlank()) "Private" else "Shared"}",
                            style = AcnBadgeStyle.NEUTRAL
                        )
                    }
                    if (syllabus.json().optString("content").isNotBlank()) {
                        Text(
                            text = syllabus.json().optString("content"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    SourceLine(vm, syllabus)

                    val completed = personal(records, syllabus.id)
                    var currentUnit = ""
                    for (i in 0 until topics.length()) {
                        val topic = topics.getJSONObject(i)
                        val key = topic.getString("id")
                        val unit = topic.optString("unit")
                        if (unit != currentUnit) {
                            currentUnit = unit
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = currentUnit,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson
                            )
                        }

                        val isDone = completed.optJSONArray("completed")?.let { a ->
                            (0 until a.length()).any { a.optString(it) == key }
                        } ?: false

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = { checked ->
                                    val values = completed.optJSONArray("completed")?.let { a ->
                                        (0 until a.length()).map { a.getString(it) }.toMutableSet()
                                    } ?: mutableSetOf()
                                    if (checked) values += key else values -= key
                                    vm.personal(syllabus.id, completed.put("completed", JSONArray(values.toList())))
                                },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = topic.getString("title"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            AcnOutlinedButton(
                onClick = { edit(EditRequest("SYLLABUS", course.id, course.groupId, syllabus)) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.EditNote
            ) {
                Text(if (syllabus == null) "Add syllabus" else "Edit syllabus · new version", fontWeight = FontWeight.SemiBold)
            }
        } else {
            val selected = items.filter { it.kind == sections[section] }.sortedBy { it.json().optString("date") }
            if (selected.isEmpty()) {
                AcnEmptyState(
                    title = "No ${section.lowercase()} yet",
                    description = "Details appear here after you save them or your class publishes an update.",
                    icon = Icons.Default.EventNote
                )
            }
            selected.forEach { item ->
                var showPersonal by remember(item.id) { mutableStateOf(false) }
                val isLocalOnly = item.json().optBoolean("localOnly")
                val subtitle = if (isLocalOnly) "${dateLabel(item.json())} · (Local only · uncommitted)" else dateLabel(item.json())

                AcnCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AcnBadge(
                            text = item.kind.replace('_', ' '),
                            style = if (item.groupId.isNotBlank()) AcnBadgeStyle.SHARED else AcnBadgeStyle.PRIMARY
                        )
                        if (isLocalOnly) {
                            AcnBadge(text = "Uncommitted", style = AcnBadgeStyle.CAUTION)
                        }
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val j = item.json()
                    if (j.optString("content").isNotBlank()) {
                        Text(text = j.optString("content"), style = MaterialTheme.typography.bodyMedium)
                    }

                    val coverage = j.optJSONArray("coverage") ?: JSONArray()
                    if (item.kind in listOf("MIDTERM", "END_SEMESTER", "QUIZ")) {
                        if (coverage.length() == 0) {
                            Text(
                                text = "Coverage not announced",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Announced syllabus coverage:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (i in 0 until coverage.length()) {
                                    val id = coverage.getString(i)
                                    val title = (0 until topics.length()).map { topics.getJSONObject(it) }
                                        .find { it.optString("id") == id }?.optString("title") ?: "Topic mapping requires review"
                                    AcnBadge(text = title, style = AcnBadgeStyle.NEUTRAL)
                                }
                            }
                        }
                    }

                    if (j.optBoolean("coverageNeedsReview")) {
                        AcnNoticeBanner(
                            message = "Syllabus changed — coverage needs review",
                            icon = Icons.Default.Warning
                        )
                    }

                    val milestones = j.optJSONArray("milestones") ?: JSONArray()
                    if (milestones.length() > 0) {
                        Text(
                            text = "Milestones checklist:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        for (i in 0 until milestones.length()) {
                            val m = milestones.getJSONObject(i)
                            val state = personal(records, m.getString("id"))
                            val mDone = state.optBoolean("completed")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = mDone,
                                    onCheckedChange = { vm.personal(m.getString("id"), state.put("completed", it)) },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "${m.optString("title")} · ${m.optString("date", "No date")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (mDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (j.optString("url").startsWith("https://")) {
                        val handler = LocalUriHandler.current
                        AcnOutlinedButton(
                            onClick = { handler.openUri(j.getString("url")) },
                            icon = Icons.Default.OpenInNew
                        ) {
                            Text("Open resource")
                        }
                    }

                    SourceLine(vm, item)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { edit(EditRequest(item.kind, item.courseId, item.groupId, item)) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Edit details")
                        }
                        Button(
                            onClick = { showPersonal = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("My progress / marks")
                        }
                    }

                    val state = personal(records, item.id)
                    if (state.has("earned")) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Private score: ${state.optString("earned")} / ${state.optString("maximum")}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (MaterialTheme.colorScheme.background == AcnDarkBackground) MaterialTheme.colorScheme.onSurface else AcnBrandCrimson,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (showPersonal) {
                    PersonalEditor(vm, item, personal(records, item.id)) { showPersonal = false }
                }
            }

            AcnOutlinedButton(
                onClick = { edit(EditRequest(sections.getValue(section), course.id, course.groupId)) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Add
            ) {
                Text("Add ${section.lowercase().removeSuffix("s")}", fontWeight = FontWeight.SemiBold)
            }
        }

        AcnButton(
            onClick = { ask(course.id) },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.AutoAwesome
        ) {
            Text("Ask the assistant about this course", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SourceLine(vm: WorkspaceViewModel, record: AcademicRecord) {
    val j = record.json()
    val refs = j.optJSONArray("sources") ?: JSONArray()
    if (record.groupId.isNotBlank()) {
        Text(
            text = "Shared · approved by ${j.optString("approvedBy", "publisher")} · revision ${record.revision}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (j.optBoolean("sourceNeedsReview")) {
        Text(
            text = "Source changed — review required",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    SourceEvidence(vm, record.groupId, refs)
    val files = j.optJSONArray("attachmentIds") ?: JSONArray()
    val fileRefs = JSONArray()
    for (i in 0 until files.length()) {
        fileRefs.put(JSONObject().put("id", files.getString(i)).put("type", "attachment"))
    }
    SourceEvidence(vm, record.groupId, fileRefs)
    Text(
        text = "Updated ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.updatedAt))}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    VersionHistory(vm, record)
}

@Composable
fun Choice(label: String, options: List<String>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        expanded = false
                        select(value)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicEditor(
    vm: WorkspaceViewModel,
    request: EditRequest,
    records: List<AcademicRecord>,
    close: () -> Unit
) {
    val original = request.existing?.json() ?: JSONObject()
    var attachments by remember { mutableStateOf<List<String>>(original.optJSONArray("attachmentIds")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.action {
            val file = vm.repo.upload(uri, request.groupId, request.courseId)
            attachments = attachments + file.getString("id")
        }
    }
    var kind by remember { mutableStateOf(request.kind) }
    var title by remember { mutableStateOf(original.optString("title")) }
    var content by remember { mutableStateOf(original.optString("content")) }
    var course by remember { mutableStateOf(request.courseId) }
    var code by remember { mutableStateOf(original.optString("code")) }
    var semester by remember { mutableStateOf(original.optInt("semester", vm.semester.value).toString()) }
    var date by remember { mutableStateOf(original.optString("date", request.initialDate).takeUnless { it == "null" } ?: "") }
    var time by remember { mutableStateOf(original.optString("time").takeUnless { it == "null" } ?: "") }
    var end by remember { mutableStateOf(original.optString("endTime")) }
    var room by remember { mutableStateOf(original.optString("room")) }
    var day by remember { mutableStateOf(original.optInt("day", 1).toString()) }
    var url by remember { mutableStateOf(original.optString("url")) }
    var error by remember { mutableStateOf("") }
    var cancelled by remember { mutableStateOf(original.optBoolean("cancelled")) }
    var holiday by remember { mutableStateOf(original.optBoolean("isHoliday")) }
    var template by remember { mutableStateOf(original.optString("templateId")) }
    var reminder by remember { mutableStateOf(original.optInt("reminderMinutes", -1)) }
    val originalTopics = original.optJSONArray("topics") ?: JSONArray()
    val originalMilestones = original.optJSONArray("milestones") ?: JSONArray()
    var topicText by remember { mutableStateOf((0 until originalTopics.length()).joinToString("\n") { val t = originalTopics.getJSONObject(it); "${t.optString("unit")} | ${t.optString("title")}" }) }
    var milestoneText by remember { mutableStateOf((0 until originalMilestones.length()).joinToString("\n") { val t = originalMilestones.getJSONObject(it); "${t.optString("title")} | ${t.optString("date").takeUnless { it == "null" }.orEmpty()}" }) }
    var coverage by remember { mutableStateOf(original.optJSONArray("coverage")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()) }
    val courses = if (request.groupId.isBlank()) enrolledCourses(records) else records.filter { it.kind == "COURSE" && it.groupId == request.groupId }
    val courseMap = courseOptionMap(courses)

    androidx.activity.compose.BackHandler(onBack = close)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (request.existing == null) "Add academic details" else "Edit academic details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = close) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
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
                AcnBadge(
                    text = if (request.groupId.isBlank()) "Private workspace" else "Shared group update · publisher permission required",
                    style = if (request.groupId.isBlank()) AcnBadgeStyle.NEUTRAL else AcnBadgeStyle.SHARED
                )

                if (request.existing == null && request.kind != "COURSE") {
                    Choice(kind.replace('_', ' '), AcademicKind.entries.filter { it != AcademicKind.COURSE }.map { it.name }) {
                        kind = it
                    }
                }
                if (kind != "COURSE") {
                    Choice(courses.find { it.id == course }?.let { formatCourseOption(it) } ?: "Choose course", courseMap.keys.toList()) { name ->
                        courseMap[name]?.let { course = it }
                    }
                }

                Field("Title", title, { title = it })
                Field("Details / instructions", content, { content = it }, single = false)

                if (kind != "COURSE") {
                    AcnOutlinedButton(
                        enabled = attachments.size < 20,
                        onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain")) },
                        icon = Icons.Default.AttachFile
                    ) {
                        Text("Attach document / image")
                    }
                    if (attachments.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${attachments.size} attached files", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { attachments = emptyList() }) {
                                Text("Remove attachments")
                            }
                        }
                    }
                }

                if (kind == "COURSE") {
                    Field("Course code", code, { code = it })
                    Field("Semester", semester, { semester = it })
                }
                if (kind == "SYLLABUS") {
                    Field("One topic per line: Unit | Topic", topicText, { topicText = it }, single = false)
                }
                if (kind == "PROJECT") {
                    Field("Milestones: title | YYYY-MM-DD (optional)", milestoneText, { milestoneText = it }, single = false)
                }
                if (kind !in listOf("COURSE", "SYLLABUS", "RESOURCE")) {
                    Field("Date (YYYY-MM-DD; leave blank if unknown)", date, { date = it })
                    Field("Time (HH:mm; optional)", time, { time = it })
                }
                if (kind == "TIMETABLE") {
                    Field("Day (1 Monday – 7 Sunday)", day, { day = it })
                    Field("End time (HH:mm)", end, { end = it })
                    Field("Room", room, { room = it })
                    val templates = records.filter { it.kind == "TIMETABLE" && it.courseId == course && it.groupId == request.groupId && it.id != request.existing?.id && it.json().optString("date").let { d -> d.isBlank() || d == "null" } }
                    if (date.isNotBlank()) {
                        Choice("Weekly class to override", listOf("Extra class") + templates.map { "${it.title} · ${it.json().optInt("day")} ${it.json().optString("time")}" }) { choice ->
                            template = templates.find { "${it.title} · ${it.json().optInt("day")} ${it.json().optString("time")}" == choice }?.id ?: ""
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(cancelled, { cancelled = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                        Text("Cancelled on this date")
                    }
                }
                if (kind == "NOTICE") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(holiday, { holiday = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                        Text("Non-instructional day (no classes)")
                    }
                }
                if (request.groupId.isBlank() && kind in listOf("MIDTERM", "END_SEMESTER", "QUIZ", "ASSIGNMENT", "PROJECT", "STUDY_SESSION", "NOTICE")) {
                    Choice("Reminder: ${if (reminder < 0) "None" else "$reminder minutes before"}", listOf("None", "At time", "1 hour before", "1 day before", "1 week before")) {
                        reminder = when (it) {
                            "At time" -> 0
                            "1 hour before" -> 60
                            "1 day before" -> 1440
                            "1 week before" -> 10080
                            else -> -1
                        }
                    }
                }
                if (kind == "RESOURCE") {
                    Field("HTTPS resource link (optional)", url, { url = it })
                }
                if (kind in listOf("MIDTERM", "END_SEMESTER", "QUIZ", "ASSIGNMENT")) {
                    Text("Select announced syllabus coverage. Leave empty if unknown.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val topicsList = resolveSyllabusTopics(records, course)
                    topicsList.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                t.getString("id") in coverage,
                                { checked -> coverage = if (checked) coverage + t.getString("id") else coverage - t.getString("id") },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(t.getString("title"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (error.isNotBlank()) {
                    AcnNoticeBanner(message = error, icon = Icons.Default.ErrorOutline)
                }

                AcnButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val data = JSONObject().put("title", title.trim()).put("content", content).put("coverage", JSONArray(coverage.toList())).put("attachmentIds", JSONArray(attachments))
                            if (date.isNotBlank()) data.put("date", date.trim())
                            if (time.isNotBlank()) data.put("time", time.trim())
                            if (kind == "COURSE") data.put("code", code.trim()).put("semester", semester.toIntOrNull() ?: 0)
                            if (kind == "TIMETABLE") {
                                data.put("day", day.toInt()).put("endTime", end.trim()).put("room", room).put("cancelled", cancelled)
                                if (template.isNotBlank()) data.put("templateId", template)
                                require(!cancelled || date.isNotBlank()) { "Choose the cancellation date" }
                            }
                            if (kind == "NOTICE") data.put("isHoliday", holiday)
                            if (reminder >= 0) {
                                require(date.isNotBlank()) { "Choose a date before setting a reminder" }
                                data.put("reminderMinutes", reminder)
                            }
                            if (url.isNotBlank()) data.put("url", url.trim())
                            if (kind == "SYLLABUS") {
                                val topicsArr = JSONArray()
                                topicText.lines().filter { it.isNotBlank() }.forEach { line ->
                                    val parts = line.split('|', limit = 2)
                                    require(parts.size == 2) { "Use Unit | Topic on each line" }
                                    val old = (0 until originalTopics.length()).map { originalTopics.getJSONObject(it) }.find { it.optString("unit") == parts[0].trim() && it.optString("title") == parts[1].trim() }
                                    topicsArr.put(JSONObject().put("id", old?.getString("id") ?: UUID.randomUUID().toString()).put("unit", parts[0].trim()).put("title", parts[1].trim()))
                                }
                                data.put("topics", topicsArr)
                            }
                            if (kind == "PROJECT") {
                                val milestonesArr = JSONArray()
                                milestoneText.lines().filter { it.isNotBlank() }.forEach { line ->
                                    val parts = line.split('|', limit = 2)
                                    val d = parts.getOrNull(1)?.trim().orEmpty()
                                    if (d.isNotBlank()) java.time.LocalDate.parse(d)
                                    val old = (0 until originalMilestones.length()).map { originalMilestones.getJSONObject(it) }.find { it.optString("title") == parts[0].trim() }
                                    milestonesArr.put(JSONObject().put("id", old?.getString("id") ?: UUID.randomUUID().toString()).put("title", parts[0].trim()).put("date", d.ifBlank { null }))
                                }
                                data.put("milestones", milestonesArr)
                            }
                            AcademicValidation.validate(kind, data)
                            require(kind in listOf("COURSE", "NOTICE", "STUDY_SESSION") || course.isNotBlank()) { "Choose a course" }
                            vm.save(kind, data, course, request.existing, request.groupId)
                            close()
                        } catch (e: Exception) {
                            error = e.message ?: "Check your input"
                        }
                    }
                ) {
                    Text("Save details", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit, single: Boolean = true) {
    AcnTextField(value = value, onValueChange = change, label = label, singleLine = single)
}

@Composable
fun PersonalEditor(vm: WorkspaceViewModel, item: AcademicRecord, state: JSONObject, close: () -> Unit) {
    var notes by remember { mutableStateOf(state.optString("notes")) }
    var earned by remember { mutableStateOf(state.optString("earned")) }
    var maximum by remember { mutableStateOf(state.optString("maximum")) }
    var complete by remember { mutableStateOf(state.optBoolean("completed")) }
    var error by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf(if (state.isNull("reminder")) -1 else state.optInt("reminder", -1)) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("My progress · private", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Notes", notes, { notes = it }, false)
                Field("Marks earned (optional)", earned, { earned = it })
                Field("Maximum marks", maximum, { maximum = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(complete, { complete = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                    Text("Completed", style = MaterialTheme.typography.bodyMedium)
                }
                Choice("Reminder: ${if (reminder < 0) "None" else "$reminder minutes before"}", listOf("None", "At time", "1 hour before", "1 day before")) {
                    reminder = when (it) {
                        "At time" -> 0
                        "1 hour before" -> 60
                        "1 day before" -> 1440
                        else -> -1
                    }
                }
                Text("Reminders use the saved date/time, or 09:00 if only a date is known.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            AcnButton(onClick = {
                try {
                    val p = JSONObject().put("notes", notes).put("completed", complete)
                    if (earned.isNotBlank()) {
                        val e = earned.toDouble()
                        val m = maximum.toDouble()
                        require(m > 0 && e in 0.0..m) { "Marks must be between zero and maximum" }
                        p.put("earned", e).put("maximum", m)
                    }
                    if (reminder >= 0) require(item.json().optString("date").let { it.isNotBlank() && it != "null" }) { "Set a date before adding a reminder" }
                    p.put("reminder", if (reminder >= 0) reminder else JSONObject.NULL)
                    vm.personal(item.id, p)
                    close()
                } catch (e: Exception) {
                    error = e.message ?: "Invalid marks"
                }
            }) {
                Text("Save privately")
            }
        },
        dismissButton = {
            TextButton(onClick = close) {
                Text("Cancel")
            }
        }
    )
}
