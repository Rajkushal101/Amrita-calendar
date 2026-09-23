package acn.amrita.chen.planner.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import acn.amrita.chen.planner.ui.theme.*
import androidx.compose.ui.graphics.Color
import acn.amrita.chen.planner.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceApp(vm: WorkspaceViewModel = viewModel()) {
    val records by vm.records.collectAsState()
    val busy by vm.busy.collectAsState()
    val owner by vm.owner.collectAsState()
    val notice by vm.message.collectAsState()
    val shared by SharedImports.uris.collectAsState()
    val sharedText by SharedImports.text.collectAsState()

    var route by rememberSaveable { mutableStateOf("Today") }
    var courseId by rememberSaveable { mutableStateOf("") }
    var groupId by rememberSaveable { mutableStateOf("") }
    var assistant by remember { mutableStateOf(false) }
    var aiContext by remember { mutableStateOf(AgentContext()) }
    var editor by remember { mutableStateOf<EditRequest?>(null) }

    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val preferences = remember { activity?.getSharedPreferences("appearance", 0) }
    var dark by rememberSaveable { mutableStateOf(preferences?.takeIf { it.contains("dark") }?.getBoolean("dark", false) ?: false) }

    SideEffect {
        activity?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    LaunchedEffect(owner) {
        courseId = ""
        groupId = ""
        route = "Today"
        assistant = false
        editor = null
    }

    LaunchedEffect(shared, sharedText) {
        if (shared.isNotEmpty() || sharedText.isNotBlank()) {
            aiContext = AgentContext()
            assistant = true
        }
    }

    fun openAi(context: AgentContext = AgentContext()) {
        aiContext = context
        assistant = true
    }

    fun back() {
        when {
            courseId.isNotBlank() -> courseId = ""
            groupId.isNotBlank() -> {
                groupId = ""
                vm.repo.closeChat()
            }
            route == "My Amrita" -> route = "Subjects"
            route == "Settings" -> route = "Today"
            else -> route = "Today"
        }
    }

    BackHandler(enabled = courseId.isNotBlank() || groupId.isNotBlank() || route !in listOf("Today", "Subjects", "Planner", "Groups", "Inbox")) {
        back()
    }

    AcnPlannerTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
            topBar = {
                if (route != "My Amrita") {
                    TopAppBar(
                        title = {
                            Column {
                                val titleText = when {
                                    courseId.isNotBlank() -> records.find { it.id == courseId }?.json()?.optString("code")?.ifBlank { null } ?: "Course Workspace"
                                    groupId.isNotBlank() -> "Class Workspace"
                                    else -> route
                                }
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (owner == "legacy") {
                                    Text(
                                        text = "Personal workspace · sign in for groups",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (courseId.isNotBlank() || groupId.isNotBlank() || route == "Settings") {
                                IconButton(onClick = { back() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (route != "Settings") {
                                Button(
                                    onClick = { openAi(AgentContext(courseId, groupId)) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AcnBrandCrimson,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Ask academic assistant",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Assistant",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            IconButton(onClick = {
                                route = "Settings"
                                courseId = ""
                                groupId = ""
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                if (courseId.isBlank() && groupId.isBlank() && route != "My Amrita") {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        listOf(
                            "Today" to Icons.Default.Home,
                            "Subjects" to Icons.Default.School,
                            "Planner" to Icons.Default.DateRange,
                            "Groups" to Icons.Default.Groups,
                            "Inbox" to Icons.Default.Inbox
                        ).forEach { (label, icon) ->
                            NavigationBarItem(
                                selected = route == label,
                                onClick = { route = label },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = if (MaterialTheme.colorScheme.background == AcnDarkBackground) AcnDarkText else AcnBrandCrimson,
                                    indicatorColor = AcnBrandCrimson,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            val contentPadding = if (route == "My Amrita") PaddingValues(0.dp) else padding
            Column(
                Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                if (busy && route != "My Amrita") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (notice.isNotBlank() && route != "My Amrita") {
                    AcnNoticeBanner(
                        message = notice,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        onAction = { vm.message.value = "" }
                    )
                }
                when {
                    courseId.isNotBlank() -> records.find { it.id == courseId }?.let {
                        CourseWorkspace(vm, it, records, { editor = it }, { openAi(AgentContext(courseId = it)) })
                    }
                    groupId.isNotBlank() -> GroupWorkspace(vm, groupId, records, { editor = it }, { courseId = it }, { openAi(it) })
                    route == "Today" -> TodayPage(vm, records, { route = it }, { courseId = it }, { openAi() })
                    route == "Subjects" -> CoursesPage(vm, records, { courseId = it }, { route = "My Amrita" }, { editor = EditRequest("COURSE") })
                    route == "Planner" -> PlannerPage(vm, records, { editor = it })
                    route == "Groups" -> GroupsPage(vm) { groupId = it; vm.repo.openChat(it) }
                    route == "Inbox" -> InboxPage(vm, records)
                    route == "Settings" -> SettingsPage(vm, dark) {
                        dark = it
                        preferences?.edit()?.putBoolean("dark", it)?.apply()
                    }
                    route == "My Amrita" -> PortalScreen(vm) { route = "Subjects" }
                }
            }
        }
        editor?.let { e -> AcademicEditor(vm, e, records) { editor = null } }
        if (assistant) {
            AgentSheet(vm, aiContext) {
                assistant = false
                SharedImports.uris.value = emptyList()
                SharedImports.text.value = ""
            }
        }
        }
    }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
fun Panel(
    title: String,
    subtitle: String = "",
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    AcnCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        content()
    }
}

@Composable
fun SectionTitle(text: String) {
    AcnSectionHeader(title = text)
}

@Composable
fun EmptyState(title: String, detail: String) {
    AcnEmptyState(title = title, description = detail)
}

fun personal(records: List<AcademicRecord>, id: String) =
    records.find { it.id == "personal:$id" }?.json() ?: JSONObject()

fun enrolledCourses(records: List<AcademicRecord>) =
    records.filter { it.kind == "COURSE" && (it.groupId.isBlank() || personal(records, it.id).optString("mapping") == "enrolled") }

fun courseItems(records: List<AcademicRecord>, course: AcademicRecord): List<AcademicRecord> {
    val linked = records.filter { it.kind == "COURSE" && personal(records, it.id).optString("mapping") == course.id }.map { it.id }
    return records.filter { it.courseId == course.id || it.courseId in linked }
}

fun dateLabel(j: JSONObject) =
    j.optString("date").takeUnless { it.isBlank() || it == "null" }?.let {
        it + j.optString("time").takeUnless { t -> t.isBlank() || t == "null" }?.let { t -> " · $t" }.orEmpty()
    } ?: "Date not announced"

@Composable
fun TodayPage(
    vm: WorkspaceViewModel,
    records: List<AcademicRecord>,
    navigate: (String) -> Unit,
    openCourse: (String) -> Unit,
    ask: () -> Unit
) {
    val now by vm.now.collectAsState()
    val profile by vm.profile.collectAsState()
    val courses = enrolledCourses(records)
    val ids = courses.flatMap { c ->
        listOf(c.id) + records.filter { it.kind == "COURSE" && personal(records, it.id).optString("mapping") == c.id }.map { it.id }
    }
    val enrolledGroups = records.filter { it.id in ids }.map { it.groupId }.toSet()
    val schedule = ScheduleEngine.forDate(
        records.filter { it.courseId in ids || (it.kind == "NOTICE" && (it.groupId.isBlank() || it.groupId in enrolledGroups)) },
        now.toLocalDate()
    )
    val current = schedule.firstOrNull {
        val j = it.json()
        now.toLocalTime().toString() >= j.optString("time") && now.toLocalTime().toString() < j.optString("endTime")
    }
    val next = schedule.firstOrNull { it.json().optString("time") > now.toLocalTime().toString() }

    Page {
        // Branded Greeting Header
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val greeting = when {
                now.hour < 12 -> "Good morning"
                now.hour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
            val studentName = profile.optString("name").takeIf { it.isNotBlank() }?.let { ", ${it.substringBefore(' ')}" }.orEmpty()
            Text(
                text = "$greeting$studentName",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = now.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Current / Next Class Card
        val focus = current ?: next
        if (focus != null) {
            val isCurrent = current != null
            AcnCard(
                onClick = { openCourse(focus.courseId) },
                borderColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AcnBadge(
                        text = if (isCurrent) "In progress" else "Next class",
                        style = if (isCurrent) AcnBadgeStyle.PRIMARY else AcnBadgeStyle.SHARED,
                        icon = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.Schedule
                    )
                    Text(
                        text = focus.json().optString("room").ifBlank { "Classroom" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = brandAccentText()
                    )
                }
                Text(
                    text = focus.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${focus.json().optString("time")} – ${focus.json().optString("endTime")}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = brandAccentText()
                )
            }
        } else {
            AcnEmptyState(
                title = "Your day is clear",
                description = "No classes scheduled for today in your saved timetable.",
                icon = Icons.Default.CheckCircleOutline
            )
        }

        // Coming Up Deadlines
        AcnSectionHeader(title = "Coming up", subtitle = "Upcoming assessments and study deadlines")
        val due = records.filter {
            it.kind in listOf("MIDTERM", "END_SEMESTER", "QUIZ", "ASSIGNMENT", "PROJECT", "STUDY_SESSION", "NOTICE") &&
                    (it.groupId.isBlank() || it.courseId in ids) &&
                    it.json().optString("date").let { d -> d.isNotBlank() && d != "null" && d >= now.toLocalDate().toString() }
        }.sortedBy { it.json().optString("date") }.take(5)

        if (due.isEmpty()) {
            AcnEmptyState(
                title = "No upcoming deadlines",
                description = "Add an assessment or extract one from a class message with the assistant.",
                icon = Icons.Default.EventAvailable
            )
        } else {
            due.forEach { item ->
                AcnCard(onClick = { if (item.courseId.isNotBlank()) openCourse(item.courseId) else navigate("Planner") }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AcnBadge(
                            text = item.kind.replace('_', ' '),
                            style = when (item.kind) {
                                "QUIZ", "MIDTERM", "END_SEMESTER" -> AcnBadgeStyle.PRIMARY
                                "PROJECT", "ASSIGNMENT" -> AcnBadgeStyle.SHARED
                                else -> AcnBadgeStyle.NEUTRAL
                            }
                        )
                        Text(
                            text = dateLabel(item.json()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Attendance Alerts Section
        val alertedCourses = courses.mapNotNull { c ->
            val p = personal(records, c.id)
            if (p.has("total") && p.optInt("total") > 0) {
                val required = p.optInt("requiredAttendance", 75)
                val a = AcademicValidation.attendance(p.optInt("attended"), p.optInt("total"), required)
                if (a.needed > 0) c to a else null
            } else null
        }

        if (alertedCourses.isNotEmpty()) {
            AcnSectionHeader(title = "Attendance alerts", subtitle = "Classes requiring recovery attendance")
            alertedCourses.forEach { (c, a) ->
                AcnCard(
                    onClick = { openCourse(c.id) },
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = c.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AcnBadge(
                            text = "${"%.1f".format(a.percentage)}%",
                            style = AcnBadgeStyle.CAUTION,
                            icon = Icons.Default.Warning
                        )
                    }
                    Text(
                        text = "Attend ${a.needed} consecutive classes to reach minimum requirement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Direct Actions to Planner and Assistant
        AcnOutlinedButton(
            onClick = { navigate("Planner") },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.CalendarMonth
        ) {
            Text("Open weekly planner", fontWeight = FontWeight.SemiBold)
        }

        AcnButton(
            onClick = ask,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.AutoAwesome
        ) {
            Text("Prepare my study plan", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PlannerPage(
    vm: WorkspaceViewModel,
    records: List<AcademicRecord>,
    edit: (EditRequest) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf("Calendar") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    var day by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }

    val courses = enrolledCourses(records)
    val allowed = courses.flatMap { listOf(it.id) + courseItems(records, it).map { r -> r.courseId } }.toSet()
    val items = records.filter { it.kind in AcademicKind.entries.map { it.name } && (it.groupId.isBlank() || it.courseId in allowed) }

    Page {
        // Tab Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Calendar" to Icons.Default.CalendarToday, "Week" to Icons.Default.ViewWeek, "Tasks" to Icons.Default.Checklist).forEach { (label, icon) ->
                FilterChip(
                    selected = tab == label,
                    onClick = { tab = label },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AcnBrandCrimson,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
            }
        }

        when (tab) {
            "Calendar" -> {
                // Month Navigation
                AcnCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                        }
                        Text(
                            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                        }
                    }

                    // Calendar Grid Days
                    Row(Modifier.fillMaxWidth()) {
                        listOf(
                            "M" to null,
                            "T" to null,
                            "W" to null,
                            "T" to null,
                            "F" to null,
                            "S" to Color(0xFF0D9488),
                            "S" to Color(0xFF8B5CF6)
                        ).forEach { (dayLabel, dayColor) ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = dayColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val start = month.atDay(1).dayOfWeek.value - 1
                    for (week in 0..5) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0..6) {
                                val d = week * 7 + col - start + 1
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (d in 1..month.lengthOfMonth()) {
                                        val dayObj = month.atDay(d)
                                        val dayDate = dayObj.toString()
                                        val isSelected = date == dayDate
                                        val isToday = dayDate == LocalDate.now().toString()
                                        val isSaturday = dayObj.dayOfWeek == java.time.DayOfWeek.SATURDAY
                                        val isSunday = dayObj.dayOfWeek == java.time.DayOfWeek.SUNDAY
                                        val dayEvents = items.filter { it.json().optString("date") == dayDate }
                                        val hasHoliday = dayEvents.any { it.json().optString("calendarType") == "holiday" }
                                        val hasExam = dayEvents.any { it.json().optString("calendarType") == "exam" || it.kind in listOf("MIDTERM", "END_SEMESTER", "QUIZ") }
                                        val hasAcademic = dayEvents.any { it.json().optString("calendarType") == "academic" }

                                        Surface(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clickable { date = dayDate },
                                            shape = CircleShape,
                                            color = when {
                                                isSelected -> AcnBrandCrimson
                                                isToday -> if (MaterialTheme.colorScheme.background == AcnDarkBackground) AcnDarkSurfaceRaised else Color(0xFFEFE8EC)
                                                hasHoliday -> Color(0xFFFEE2E2).copy(alpha = 0.5f)
                                                isSunday -> Color(0xFFF3E8FF).copy(alpha = 0.45f)
                                                isSaturday -> Color(0xFFCCFBF1).copy(alpha = 0.4f)
                                                else -> Color.Transparent
                                            }
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = d.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isSelected || isToday || dayEvents.isNotEmpty() || isSaturday || isSunday) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSelected -> Color.White
                                                        isToday -> brandAccentText()
                                                        hasHoliday -> Color(0xFFDC2626)
                                                        hasExam -> Color(0xFFD97706)
                                                        isSunday -> Color(0xFF7C3AED)
                                                        isSaturday -> Color(0xFF0D9488)
                                                        dayEvents.isNotEmpty() -> MaterialTheme.colorScheme.onSurface
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                val showDots = dayEvents.isNotEmpty() || isSaturday || isSunday
                                                if (showDots) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (hasHoliday) {
                                                            Box(Modifier.size(4.dp).background(if (isSelected) Color.White else Color(0xFFEF4444), CircleShape))
                                                        }
                                                        if (hasExam) {
                                                            Box(Modifier.size(4.dp).background(if (isSelected) Color.White else Color(0xFFF59E0B), CircleShape))
                                                        }
                                                        if (hasAcademic) {
                                                            Box(Modifier.size(4.dp).background(if (isSelected) Color.White else Color(0xFF3B82F6), CircleShape))
                                                        }
                                                        if (isSaturday) {
                                                            Box(Modifier.size(4.dp).background(if (isSelected) Color.White else Color(0xFF0D9488), CircleShape))
                                                        }
                                                        if (isSunday) {
                                                            Box(Modifier.size(4.dp).background(if (isSelected) Color.White else Color(0xFF8B5CF6), CircleShape))
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

                    // Calendar Legend
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "Holiday" to Color(0xFFEF4444),
                            "Exam" to Color(0xFFF59E0B),
                            "Academic" to Color(0xFF3B82F6),
                            "Saturday" to Color(0xFF0D9488),
                            "Sunday" to Color(0xFF8B5CF6)
                        ).forEach { (label, dotColor) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(Modifier.size(5.dp).background(dotColor, CircleShape))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Selected Date Agenda
                AcnSectionHeader(
                    title = "Selected: $date",
                    subtitle = if (date == LocalDate.now().toString()) "Today's academic schedule" else "Scheduled items"
                )

                val dayItems = items.filter { it.json().optString("date") == date }
                if (dayItems.isEmpty()) {
                    AcnEmptyState(
                        title = "Nothing scheduled",
                        description = "No classes, assessments, or study sessions scheduled for this date.",
                        icon = Icons.Default.EventNote
                    )
                } else {
                    dayItems.forEach { r ->
                        val isHoliday = r.json().optString("calendarType") == "holiday"
                        val isExam = r.json().optString("calendarType") == "exam" || r.kind in listOf("MIDTERM", "END_SEMESTER")
                        val isAcademic = r.json().optString("calendarType") == "academic"
                        val badgeText = when {
                            isHoliday -> "HOLIDAY"
                            isExam -> "EXAM"
                            isAcademic -> "ACADEMIC"
                            else -> r.kind.replace('_', ' ')
                        }
                        val badgeStyle = when {
                            isHoliday -> AcnBadgeStyle.ERROR
                            isExam -> AcnBadgeStyle.CAUTION
                            isAcademic -> AcnBadgeStyle.PRIMARY
                            r.groupId.isNotBlank() -> AcnBadgeStyle.SHARED
                            else -> AcnBadgeStyle.PRIMARY
                        }
                        AcnCard(onClick = { edit(EditRequest(r.kind, r.courseId, r.groupId, r)) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AcnBadge(
                                    text = badgeText,
                                    style = badgeStyle
                                )
                                Text(
                                    text = dateLabel(r.json()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = r.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                AcnButton(
                    onClick = { edit(EditRequest("STUDY_SESSION", initialDate = date)) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Add
                ) {
                    Text("Add study session or event", fontWeight = FontWeight.SemiBold)
                }

                // Month Events Overview
                val currentMonthStr = month.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val monthEvents = items.filter {
                    it.json().optString("date").startsWith(currentMonthStr)
                }.sortedBy { it.json().optString("date") }

                if (monthEvents.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    AcnSectionHeader(
                        title = "Events in ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}",
                        subtitle = "${monthEvents.size} scheduled academic events"
                    )
                    monthEvents.forEach { r ->
                        val evDate = r.json().optString("date")
                        val isEvHoliday = r.json().optString("calendarType") == "holiday"
                        val isEvExam = r.json().optString("calendarType") == "exam" || r.kind in listOf("MIDTERM", "END_SEMESTER")
                        val isEvAcademic = r.json().optString("calendarType") == "academic"
                        val isEvSelected = date == evDate

                        AcnCard(
                            onClick = { date = evDate },
                            borderColor = if (isEvSelected) AcnBrandCrimson else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isEvHoliday -> Color(0xFFFEE2E2)
                                            isEvExam -> Color(0xFFFEF3C7)
                                            else -> Color(0xFFDBEAFE)
                                        },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val dayNum = try { LocalDate.parse(evDate).dayOfMonth.toString() } catch (_: Exception) { "" }
                                            Text(
                                                text = dayNum,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isEvHoliday -> Color(0xFFDC2626)
                                                    isEvExam -> Color(0xFFD97706)
                                                    else -> Color(0xFF2563EB)
                                                }
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = r.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = try {
                                                LocalDate.parse(evDate).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
                                            } catch (_: Exception) { evDate },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                AcnBadge(
                                    text = when {
                                        isEvHoliday -> "HOLIDAY"
                                        isEvExam -> "EXAM"
                                        isEvAcademic -> "ACADEMIC"
                                        else -> r.kind.replace('_', ' ')
                                    },
                                    style = when {
                                        isEvHoliday -> AcnBadgeStyle.ERROR
                                        isEvExam -> AcnBadgeStyle.CAUTION
                                        else -> AcnBadgeStyle.PRIMARY
                                    }
                                )
                            }
                        }
                    }
                }
            }

            "Week" -> {
                // Mon-Sun Day selector
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = day == d,
                            onClick = { day = d },
                            label = { Text(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[d - 1]) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AcnBrandCrimson,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                val classes = items.filter { it.kind == "TIMETABLE" && it.json().optInt("day") == day }.sortedBy { it.json().optString("time") }
                if (classes.isEmpty()) {
                    AcnEmptyState(
                        title = "No classes saved",
                        description = "Add class times manually or import your schedule with the academic assistant.",
                        icon = Icons.Default.Schedule
                    )
                } else {
                    classes.forEach { r ->
                        AcnCard(onClick = { edit(EditRequest(r.kind, r.courseId, r.groupId, r)) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${r.json().optString("time")} – ${r.json().optString("endTime")}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = brandAccentText()
                                )
                                AcnBadge(
                                    text = r.json().optString("room").ifBlank { "Classroom" },
                                    style = AcnBadgeStyle.NEUTRAL
                                )
                            }
                            Text(
                                text = r.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                AcnButton(
                    onClick = { edit(EditRequest("TIMETABLE")) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Add
                ) {
                    Text("Add class", fontWeight = FontWeight.SemiBold)
                }
            }

            else -> {
                // Tasks
                AcnSectionHeader(title = "Academic tasks", subtitle = "Assignments and projects")
                val tasks = items.filter { it.kind in listOf("ASSIGNMENT", "PROJECT", "STUDY_SESSION") }.sortedBy { it.json().optString("date") }

                if (tasks.isEmpty()) {
                    AcnEmptyState(
                        title = "No tasks recorded",
                        description = "Add an assignment or study session to track your coursework progress.",
                        icon = Icons.Default.Checklist
                    )
                } else {
                    tasks.forEach { r ->
                        val p = personal(records, r.id)
                        val isCompleted = p.optBoolean("completed")

                        AcnCard(onClick = { edit(EditRequest(r.kind, r.courseId, r.groupId, r)) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isCompleted,
                                    onCheckedChange = { vm.personal(r.id, p.put("completed", it)) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = r.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${r.kind.replace('_', ' ')} · ${dateLabel(r.json())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCompleted) {
                                    AcnBadge(text = "Completed", style = AcnBadgeStyle.SUCCESS)
                                }
                            }
                        }
                    }
                }

                AcnButton(
                    onClick = { edit(EditRequest("ASSIGNMENT")) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Add
                ) {
                    Text("Add assignment", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
