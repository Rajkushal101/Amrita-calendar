package acn.amrita.chen.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import androidx.lifecycle.viewmodel.compose.viewModel
import acn.amrita.chen.planner.data.SessionStatus



// Colour tokens
private val AcnRed      = Color(0xFFC62828)
private val AcnSurface  = Color(0xFF1A1A1A)
private val AcnCard     = Color(0xFF242424)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSec     = Color(0xFFAAAAAA)
private val GreenSafe   = Color(0xFF4CAF50)
private val YellowWarn  = Color(0xFFFFC107)
private val RedDanger   = Color(0xFFF44336)

// ── Domain model ──────────────────────────────────────────────────────────────



val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")


@Composable
fun WeekScreen(viewModel: acn.amrita.chen.planner.ui.MainViewModel) {
    val weekVm: WeekViewModel = viewModel()
    val timetable by weekVm.timetable.collectAsState()

    WeekScreenContent(
        timetable = timetable,
        onAddClass = { /* TODO navigate or show dialog */ }
    )
}
@Composable
fun WeekScreenContent(
    timetable: Map<String, List<TimetableSession>> = sampleTimetable(),
    onAddClass: () -> Unit = {}
) {
    val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)     // 1=Sun
    // Map Calendar day-of-week to our 0-based Mon index
    val initialDay = when (todayDow) {
        Calendar.MONDAY    -> 0
        Calendar.TUESDAY   -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY  -> 3
        Calendar.FRIDAY    -> 4
        Calendar.SATURDAY  -> 5
        else               -> 0
    }
    var selectedDay by remember { mutableIntStateOf(initialDay) }
    val sessions = timetable[DAYS[selectedDay]] ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AcnSurface)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Timetable", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary)
                Text("VII Semester · CYS-B", fontSize = 13.sp, color = TextSec)
            }
            IconButton(
                onClick = onAddClass,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AcnRed)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Add, "Add class", tint = Color.White)
            }
        }

        // ── Day tab strip (scrollable, matching MyAmrita pattern) ─────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DAYS.forEachIndexed { index, day ->
                val isSelected = index == selectedDay
                val isToday    = index == initialDay
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                isSelected -> AcnRed
                                else       -> Color(0xFF2C2C2C)
                            }
                        )
                        .clickable { selectedDay = index }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            day,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSec
                        )
                        if (isToday && !isSelected) {
                            Spacer(Modifier.height(3.dp))
                            Box(
                                Modifier.size(4.dp)
                                    .clip(CircleShape)
                                    .background(AcnRed)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Session list ──────────────────────────────────────────────────────
        if (sessions.isEmpty()) {
            EmptyDayView()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions) { session ->
                    SessionCard(session)
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }
}

// ── Session Card ──────────────────────────────────────────────────────────────
@Composable
private fun SessionCard(session: TimetableSession) {
    val isCancelled   = session.status == SessionStatus.CANCELLED
    val isRoomChanged = session.status == SessionStatus.ROOM_CHANGED

    val leftBarColor = when {
        isCancelled   -> RedDanger
        isRoomChanged -> YellowWarn
        session.type == SessionType.LAB -> Color(0xFF7C4DFF)
        else          -> AcnRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCancelled) Color(0xFF2A1818) else AcnCard
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Coloured left bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max)
                    .background(leftBarColor)
                    .fillMaxHeight()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Period circle (matching MyAmrita style)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCancelled) Color(0xFF3A2020) else Color(0xFF3A0000)
                        )
                ) {
                    Text(
                        "${session.periodNumber}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCancelled) RedDanger else AcnRed
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Subject info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            session.subject,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCancelled) TextSec else TextPrimary,
                            textDecoration = if (isCancelled)
                                androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        // Type badge (LAB / TUT)
                        if (session.type != SessionType.THEORY) {
                            SmallBadge(
                                label = if (session.type == SessionType.LAB) "LAB" else "TUT",
                                color = if (session.type == SessionType.LAB)
                                    Color(0xFF7C4DFF) else Color(0xFF00BCD4)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(session.subjectCode, fontSize = 11.sp, color = AcnRed)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeekInfoChip(Icons.Default.Schedule, session.timeLabel)
                        WeekInfoChip(Icons.Default.Room,
                            if (isRoomChanged) "${session.room} ⚠" else session.room)
                    }
                    Spacer(Modifier.height(4.dp))
                    WeekInfoChip(Icons.Default.Person, session.faculty)
                }

                // Status column (right side)
                Column(horizontalAlignment = Alignment.End) {
                    when (session.status) {
                        SessionStatus.CANCELLED   -> SmallBadge("CANCELLED", RedDanger)
                        SessionStatus.ROOM_CHANGED -> SmallBadge("MOVED", YellowWarn)
                        else -> {}
                    }
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyDayView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.EventAvailable, null,
                tint = Color(0xFF3A3A3A), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("No classes this day", fontSize = 16.sp, color = TextSec)
            Spacer(Modifier.height(4.dp))
            Text("Add a class with the + button", fontSize = 13.sp, color = Color(0xFF555555))
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────
@Composable
private fun WeekInfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF666666), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 11.sp, color = TextSec)
    }
}

@Composable
private fun SmallBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp)
    }
}

// ── Sample data ───────────────────────────────────────────────────────────────
private fun sampleTimetable(): Map<String, List<TimetableSession>> = mapOf(
    "Mon" to listOf(
        TimetableSession(1, "Digital Communication", "20CYS405", "AB2-102", "Dr. Priya", "08:00–09:00"),
        TimetableSession(2, "Network Security", "20CYS406", "AB2-304", "Dr. Kumar", "09:00–10:00"),
        TimetableSession(3, "Web Application Security", "20CYS403", "AB2-208", "Dr. Mehta", "10:00–11:00"),
        TimetableSession(5, "IoT Lab", "19CSE446L", "Lab-3", "Dr. Rajan", "14:00–16:00",
            SessionType.LAB)
    ),
    "Tue" to listOf(
        TimetableSession(1, "Distributed Systems", "20CYS402", "AB3-301", "Dr. Anand", "08:00–09:00"),
        TimetableSession(2, "Android App Development", "20CYS404", "AB2-103", "Dr. Priya", "09:00–10:00",
            status = SessionStatus.ROOM_CHANGED),
        TimetableSession(3, "Secure Software Engg", "20CYS401", "AB1-201", "Dr. Kumar", "10:00–11:00"),
        TimetableSession(4, "Info Security Risk", "20MNG331", "AB2-304", "Dr. Sharma", "11:00–12:00")
    ),
    "Wed" to listOf(
        TimetableSession(2, "Network Security", "20CYS406", "AB2-304", "Dr. Kumar", "09:00–10:00"),
        TimetableSession(3, "Web Application Security", "20CYS403", "AB2-208", "Dr. Mehta", "10:00–11:00",
            status = SessionStatus.CANCELLED),
        TimetableSession(5, "Network Security Lab", "20CYS406L", "Lab-2", "Dr. Kumar", "14:00–16:00",
            SessionType.LAB)
    ),
    "Thu" to listOf(
        TimetableSession(1, "Digital Communication", "20CYS405", "AB2-102", "Dr. Priya", "08:00–09:00"),
        TimetableSession(2, "IoT", "19CSE446", "AB1-105", "Dr. Rajan", "09:00–10:00"),
        TimetableSession(4, "Indian Constitution", "19LAW300", "AB2-004", "Prof. Iyer", "11:00–12:00")
    ),
    "Fri" to listOf(
        TimetableSession(1, "Distributed Systems", "20CYS402", "AB3-301", "Dr. Anand", "08:00–09:00"),
        TimetableSession(2, "Secure Software Engg", "20CYS401", "AB1-201", "Dr. Kumar", "09:00–10:00"),
        TimetableSession(3, "Android App Development", "20CYS404", "Lab-5", "Dr. Priya", "10:00–12:00",
            SessionType.LAB)
    ),
    "Sat" to emptyList()
)

