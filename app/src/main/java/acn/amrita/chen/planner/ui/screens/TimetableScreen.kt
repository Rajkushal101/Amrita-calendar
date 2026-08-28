package acn.amrita.chen.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import acn.amrita.chen.planner.ui.MainViewModel
import acn.amrita.chen.planner.data.ClassSession
import acn.amrita.chen.planner.data.Subject
import java.time.LocalDate

// Colors matching the screenshot design
private val BgDark = Color(0xFF121212)
private val CardDark = Color(0xFF1E2235)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF9E9E9E)
private val AccentCyan = Color(0xFF4FC3F7)
private val TabActive = Color(0xFFFFA000)
private val TabInactive = Color(0xFF2C2C3E)
private val GreenSafe = Color(0xFF2E7D32)
private val YellowWarn = Color(0xFFF57F17)
private val RedLow = Color(0xFFC62828)

@Composable
fun TimetableScreen(viewModel: MainViewModel) {
    // Current day logic (1 = Mon, 7 = Sun)
    val today = LocalDate.now().dayOfWeek.value
    // Default to Monday if it's weekend, otherwise use today
    var selectedDay by remember { mutableStateOf(if (today in 1..5) today else 1) }
    
    val sessions by viewModel.repository.getScheduleForDay(selectedDay).collectAsState(initial = emptyList())
    val subjects by viewModel.subjects.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(top = 16.dp)
    ) {
        // Day Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val days = listOf(1 to "MON", 2 to "TUE", 3 to "WED", 4 to "THU", 5 to "FRI")
            for ((dayInt, dayName) in days) {
                val isActive = selectedDay == dayInt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) TabActive else TabInactive)
                        .clickable { selectedDay = dayInt }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayName,
                        color = if (isActive) Color.White else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Classes List
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No classes scheduled for this day.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions) { session ->
                    val subject = subjects.find { it.id == session.subjectId }
                    TimetableCard(session, subject)
                }
            }
        }
    }
}

@Composable
fun TimetableCard(session: ClassSession, subject: Subject?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time Column
        Column(
            modifier = Modifier.width(60.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = formatMinutesToTime(session.startTimeMinutes),
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatMinutesToTime(session.endTimeMinutes),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(2.dp)
                .height(40.dp)
                .background(Color(0xFF2C2C3E))
        )

        // Subject Info Column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = subject?.name ?: "Unknown Subject",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${subject?.code ?: "N/A"} • ${session.room}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Attendance Percentage Box
        val attendance = subject?.attendancePercentage ?: 0f
        val attColor = when {
            attendance >= 85f -> GreenSafe
            attendance >= 75f -> YellowWarn
            else -> RedLow
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(attColor)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${String.format("%.1f", attendance)}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatMinutesToTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return String.format("%02d:%02d", h, m)
}
