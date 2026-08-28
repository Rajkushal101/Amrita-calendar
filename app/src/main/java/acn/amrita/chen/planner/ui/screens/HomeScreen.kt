package acn.amrita.chen.planner.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ── Colour tokens (mirror Theme.kt) ───────────────────────────────────────────
private val AcnRed      = Color(0xFFC62828)
private val AcnRedDeep  = Color(0xFF8E0000)
private val AcnSurface  = Color(0xFF1A1A1A)
private val AcnCard     = Color(0xFF242424)
private val AcnCardAlt  = Color(0xFF2C2C2C)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSec     = Color(0xFFAAAAAA)
private val GreenSafe   = Color(0xFF4CAF50)
private val YellowWarn  = Color(0xFFFFC107)
private val RedDanger   = Color(0xFFF44336)

// ── Placeholder data models (replace with ViewModel state) ────────────────────

@Composable
fun HomeScreen(viewModel: acn.amrita.chen.planner.ui.MainViewModel) {
    val homeVm: HomeViewModel = viewModel()
    val todayClasses by homeVm.todayClasses.collectAsState()
    val attendanceAlerts by homeVm.attendanceAlerts.collectAsState()
    val assignments by homeVm.assignmentsDue.collectAsState()

    // #region agent log
    acn.amrita.chen.planner.debug.DebugAgentLog.log(
        "HomeScreen.kt:HomeScreen",
        "Home wired with hardcoded identity and no nav callbacks",
        "D",
        mapOf(
            "userName" to "Raj",
            "todayClassCount" to todayClasses.size,
            "alertCount" to attendanceAlerts.size,
            "assignmentCount" to assignments.size,
            "firstClassSubject" to (todayClasses.firstOrNull()?.subject ?: "none")
        )
    )
    // #endregion
    HomeScreenContent(
        userName = "Raj",
        userRole = "STUDENT",
        todayClasses = todayClasses,
        attendanceAlerts = attendanceAlerts,
        assignmentsDue = assignments
    )
}
@Composable
fun HomeScreenContent(
    userName: String = "Raj",
    userRole: String = "STUDENT",       // "STUDENT" | "FACULTY"
    // ── real data comes from ViewModel ──
    todayClasses: List<ClassSessionUi> = sampleClasses(),
    attendanceAlerts: List<AttendanceAlertUi> = sampleAlerts(),
    assignmentsDue: List<AssignmentDueUi> = sampleAssignments(),
    semesterDay: Int = 57,
    semesterTotal: Int = 90,
    nextMilestone: String = "End Sem Practical · 05 Oct",
    onAskAi: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenSubjects: () -> Unit = {},
    onOpenWeek: () -> Unit = {}
) {
    val now = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
    val nextClass = remember(todayClasses) {
        todayClasses.filter { !it.isCancelled && it.startMinutes > now }
            .minByOrNull { it.startMinutes }
    }
    val currentClass = remember(todayClasses) {
        todayClasses.firstOrNull { !it.isCancelled && it.startMinutes <= now && it.endMinutes > now }
    }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        else      -> "Good evening"
    }
    val dateStr = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault())
        .format(Date()).uppercase()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AcnSurface),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "$greeting, $userName",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = dateStr,
                    fontSize = 13.sp,
                    color = TextSec,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // ── Next / Current class card ─────────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            when {
                currentClass != null -> LiveClassCard(currentClass)
                nextClass != null    -> NextClassCard(nextClass, now)
                else                 -> NoMoreClassesCard()
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Today's schedule strip ────────────────────────────────────────────
        item {
            SectionHeader("Today's Schedule", onOpenWeek)
        }
        item {
            TodayScheduleCard(todayClasses, now)
            Spacer(Modifier.height(16.dp))
        }

        // ── Attendance alerts (only if any WARN/DANGER) ───────────────────────
        val alerts = attendanceAlerts.filter { it.status != AttStatus.SAFE }
        if (alerts.isNotEmpty()) {
            item { SectionHeader("Attendance Alerts", onOpenSubjects) }
            item {
                alerts.forEach { AlertAttendanceRow(it) }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Assignments due ───────────────────────────────────────────────────
        if (assignmentsDue.isNotEmpty()) {
            item { SectionHeader("Due Soon", null) }
            items(assignmentsDue.take(3)) { AssignmentDueRow(it) }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // ── Semester progress ─────────────────────────────────────────────────
        item {
            SectionHeader("Semester Progress", null)
            SemesterProgressCard(semesterDay, semesterTotal, nextMilestone)
            Spacer(Modifier.height(16.dp))
        }

        // ── Ask AI FAB row ────────────────────────────────────────────────────
        item {
            AskAiButton(onClick = onAskAi)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Live Class Card (pulsing red dot) ─────────────────────────────────────────
@Composable
private fun LiveClassCard(session: ClassSessionUi) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "dot_scale"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E0A0A))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(AcnRedDeep.copy(alpha = 0.7f), AcnCard))
                )
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("LIVE NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252), letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(session.subject, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row {
                    InfoChip(Icons.Default.Room, session.room)
                    Spacer(Modifier.width(8.dp))
                    InfoChip(Icons.Default.Schedule, session.timeLabel)
                }
            }
        }
    }
}

// ── Next Class Card ───────────────────────────────────────────────────────────
@Composable
private fun NextClassCard(session: ClassSessionUi, nowMinutes: Int) {
    val minutesLeft = session.startMinutes - nowMinutes
    val timeLeftStr = when {
        minutesLeft < 60 -> "Starts in ${minutesLeft}m"
        else             -> "Starts in ${minutesLeft / 60}h ${minutesLeft % 60}m"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("NEXT CLASS", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = AcnRed, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(session.subject, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row {
                    InfoChip(Icons.Default.Room, session.room)
                    Spacer(Modifier.width(8.dp))
                    InfoChip(Icons.Default.Schedule, session.timeLabel)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (minutesLeft < 60) "${minutesLeft}m" else "${minutesLeft / 60}h",
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AcnRed
                )
                Text("away", fontSize = 12.sp, color = TextSec)
            }
        }
    }
}

@Composable
private fun NoMoreClassesCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = GreenSafe, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("All done for today", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary)
                Text("No more classes scheduled", fontSize = 13.sp, color = TextSec)
            }
        }
    }
}

// ── Today Schedule card ───────────────────────────────────────────────────────
@Composable
private fun TodayScheduleCard(classes: List<ClassSessionUi>, nowMinutes: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (classes.isEmpty()) {
                Text("No classes today", fontSize = 14.sp, color = TextSec,
                    modifier = Modifier.padding(vertical = 8.dp))
            } else {
                classes.forEachIndexed { idx, session ->
                    TodayClassRow(session, nowMinutes)
                    if (idx < classes.lastIndex)
                        HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TodayClassRow(session: ClassSessionUi, nowMinutes: Int) {
    val isDone      = session.endMinutes <= nowMinutes
    val isActive    = session.startMinutes <= nowMinutes && session.endMinutes > nowMinutes
    val statusColor = when {
        session.isCancelled -> RedDanger
        isActive            -> Color(0xFFFF5252)
        isDone              -> TextSec
        else                -> TextPrimary
    }
    val dotColor = when {
        session.isCancelled -> RedDanger.copy(alpha = 0.5f)
        isActive            -> Color(0xFFFF5252)
        isDone              -> Color(0xFF444444)
        else                -> AcnRed
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // time dot
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(12.dp))
        // time label
        Text(session.timeLabel, fontSize = 12.sp, color = TextSec,
            modifier = Modifier.width(80.dp))
        // subject
        Text(
            session.subject,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (session.isCancelled) TextSec else statusColor,
            textDecoration = if (session.isCancelled)
                androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        // status badge
        when {
            session.isCancelled  -> StatusBadge("CANCELLED", RedDanger)
            isActive             -> StatusBadge("LIVE", Color(0xFFFF5252))
            session.isRoomChanged -> StatusBadge("MOVED", YellowWarn)
            isDone               -> Icon(Icons.Default.CheckCircle, null,
                tint = GreenSafe.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

// ── Attendance alert row ──────────────────────────────────────────────────────
@Composable
private fun AlertAttendanceRow(alert: AttendanceAlertUi) {
    val (barColor, icon) = when (alert.status) {
        AttStatus.DANGER -> RedDanger to Icons.Default.Warning
        AttStatus.WARN   -> YellowWarn to Icons.Default.Info
        else             -> GreenSafe to Icons.Default.CheckCircle
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = barColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.subject, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = when (alert.status) {
                        AttStatus.DANGER -> "Below threshold — attend immediately"
                        else             -> "Approaching 75% threshold"
                    },
                    fontSize = 11.sp, color = TextSec
                )
            }
            Text(
                text = "${alert.percentage.toInt()}%",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = barColor
            )
        }
    }
}

// ── Assignment due row ────────────────────────────────────────────────────────
@Composable
private fun AssignmentDueRow(a: AssignmentDueUi) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Row(
            modifier = Modifier.padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (a.isUrgent) RedDanger else AcnRed)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(a.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.subject, fontSize = 11.sp, color = TextSec)
            }
            Text(a.dueLabel, fontSize = 12.sp,
                color = if (a.isUrgent) RedDanger else YellowWarn,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ── Semester progress card ────────────────────────────────────────────────────
@Composable
private fun SemesterProgressCard(day: Int, total: Int, nextMilestone: String) {
    val progress = day.toFloat() / total
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CD $day / $total", fontSize = 13.sp, color = TextSec)
                Text("${(progress * 100).toInt()}%", fontSize = 13.sp,
                    color = AcnRed, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = AcnRed,
                trackColor = Color(0xFF3A3A3A)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, null, tint = AcnRed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Next: $nextMilestone", fontSize = 12.sp, color = TextSec)
            }
        }
    }
}

// ── Ask AI button ─────────────────────────────────────────────────────────────
@Composable
private fun AskAiButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AcnRed)
    ) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Ask ACN AI", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            letterSpacing = 0.5.sp)
        if (onSeeAll != null)
            Text("See all", fontSize = 12.sp, color = AcnRed,
                modifier = Modifier.clickable { onSeeAll() })
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF333333))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, tint = TextSec, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = TextSec)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp)
    }
}

// ── Sample data (delete once ViewModel wires real data) ───────────────────────
private fun sampleClasses() = listOf(
    ClassSessionUi("Network Security", "AB2-304", "09:00–10:00", 540, 600),
    ClassSessionUi("Web Application Security", "AB2-208", "10:00–11:00", 600, 660),
    ClassSessionUi("IoT", "AB1-105", "11:00–12:00", 660, 720, isCancelled = true),
    ClassSessionUi("Distributed Systems", "AB3-301", "14:00–15:00", 840, 900),
)

private fun sampleAlerts() = listOf(
    AttendanceAlertUi("Internet of Things", 73.1f, AttStatus.DANGER),
    AttendanceAlertUi("Secure Software Engg", 76.7f, AttStatus.WARN),
)

private fun sampleAssignments() = listOf(
    AssignmentDueUi("Web Security Report", "20CYS403", "Due today", true),
    AssignmentDueUi("IoT Lab Writeup", "19CSE446", "Due tomorrow", false),
)

