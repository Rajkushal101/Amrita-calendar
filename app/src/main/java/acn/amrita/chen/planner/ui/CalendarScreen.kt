package acn.amrita.chen.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import acn.amrita.chen.planner.data.Event
import acn.amrita.chen.planner.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val TextPrimary = Color(0xFF1A1D2E)
val TextSecondary = Color(0xFF5A6282)
val TextMuted = Color(0xFF9CA3BF)
val Accent = Color(0xFF4F6EF7)
val AccentLight = Color(0xFFEEF1FF)
val Green = Color(0xFF22C55E)
val GreenLight = Color(0xFFECFDF5)
val Amber = Color(0xFFF59E0B)
val AmberLight = Color(0xFFFFFBEB)
val Red = Color(0xFFEF4444)
val RedLight = Color(0xFFFEF2F2)
val Purple = Color(0xFF8B5CF6)
val PurpleLight = Color(0xFFF5F3FF)
val Teal = Color(0xFF14B8A6)
val TealLight = Color(0xFFF0FDFA)
val SurfaceColor = Color(0xFFFFFFFF)
val Surface2 = Color(0xFFF0F2FA)
val BorderColor = Color(0xFFE4E7F0)
val BgColor = Color(0xFFF7F8FC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    onAddEventClick: () -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        containerColor = BgColor,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEventClick, containerColor = Accent, contentColor = Color.White) {
                Icon(Icons.Filled.Add, contentDescription = "Add Event")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HeaderSection(searchQuery, viewModel::updateSearchQuery)
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CalendarNavigation(currentMonth = currentMonth, onPrevious = { viewModel.previousMonth() }, onNext = { viewModel.nextMonth() })
                    Spacer(modifier = Modifier.height(16.dp))
                    StatsBar(currentMonth = currentMonth, events = filteredEvents)
                    Spacer(modifier = Modifier.height(16.dp))
                    CalendarGrid(
                        currentMonth = currentMonth,
                        selectedDate = selectedDate,
                        events = filteredEvents,
                        onDateSelected = { viewModel.selectDate(it) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    UpcomingEvents(events = filteredEvents)
                    Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                }
            }
        }
    }
}

@Composable
fun HeaderSection(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    Surface(
        color = SurfaceColor,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACN Planner",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chennai Campus",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = acn.amrita.chen.planner.R.drawable.ic_acn_logo),
                    contentDescription = "ACN Logo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                placeholder = { Text("Search events...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
    }
}

@Composable
fun CalendarNavigation(currentMonth: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Previous", tint = TextSecondary)
        }
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ArrowForward, contentDescription = "Next", tint = TextSecondary)
        }
    }
}

@Composable
fun StatsBar(currentMonth: YearMonth, events: List<Event>) {
    val y = currentMonth.year
    val m = currentMonth.monthValue
    
    val monthEvents = events.filter {
        val date = java.time.Instant.ofEpochMilli(it.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        date.year == y && date.monthValue == m
    }
    
    val holidays = monthEvents.count { it.type == "holiday" }
    val exams = monthEvents.count { it.type == "exam" }
    val academics = monthEvents.count { it.type == "academic" }
    val daysInMonth = currentMonth.lengthOfMonth()
    val working = daysInMonth - holidays

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("Days", daysInMonth.toString(), AccentLight, Modifier.weight(1f))
        StatCard("Work", working.toString(), GreenLight, Modifier.weight(1f))
        StatCard("Holidays", holidays.toString(), RedLight, Modifier.weight(1f))
        StatCard("Exams", exams.toString(), AmberLight, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, bgColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = SurfaceColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(text = value, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    events: List<Event>,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstDayOfMonth = currentMonth.atDay(1)
    val startDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7
    val daysInMonth = currentMonth.lengthOfMonth()

    val totalCells = (startDayOfWeek + daysInMonth).let { if (it % 7 == 0) it else it + (7 - it % 7) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().background(Surface2).padding(vertical = 8.dp)) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEachIndexed { index, day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (index == 0) Red else if (index == 6) Accent else TextMuted
                    )
                }
            }
            
            var dayCounter = 1
            for (row in 0 until (totalCells / 7)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        if (row == 0 && col < startDayOfWeek || dayCounter > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(0.8f).border(0.5.dp, BorderColor).background(BgColor))
                        } else {
                            val date = currentMonth.atDay(dayCounter)
                            val dateEvents = events.filter {
                                java.time.Instant.ofEpochMilli(it.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate() == date
                            }
                            CalendarCell(
                                date = date,
                                isSelected = date == selectedDate,
                                dateEvents = dateEvents,
                                modifier = Modifier.weight(1f).aspectRatio(0.8f).border(0.5.dp, BorderColor),
                                onClick = { onDateSelected(date) }
                            )
                            dayCounter++
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarCell(
    date: LocalDate,
    isSelected: Boolean,
    dateEvents: List<Event>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = date == today
    val isHoliday = dateEvents.any { it.type == "holiday" } || date.dayOfWeek.value == 7

    val bgColor = when {
        isSelected -> AccentLight
        isToday -> AccentLight
        isHoliday -> RedLight
        else -> SurfaceColor
    }
    
    val textColor = when {
        isToday -> Color.White
        isHoliday -> Red
        date.dayOfWeek.value == 7 -> Accent
        else -> TextPrimary
    }

    Box(
        modifier = modifier
            .background(bgColor)
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isToday) Accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            
            val displayEvents = dateEvents.take(3)
            displayEvents.forEach { ev ->
                val (chipBg, chipText) = getTypeColors(ev.type)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(chipBg)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = ev.title,
                        color = chipText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (dateEvents.size > 3) {
                Text(text = "+${dateEvents.size - 3} more", fontSize = 8.sp, color = TextMuted)
            }
        }
    }
}



fun getTypeColors(type: String): Pair<Color, Color> {
    return when (type) {
        "holiday" -> AcnGreen.copy(alpha = 0.1f) to AcnGreen
        "exam" -> AcnAmber.copy(alpha = 0.1f) to AcnAmber
        "personal" -> AcnPurple.copy(alpha = 0.1f) to AcnPurple
        "academic", "meeting", "event", "reminder", "deadline" -> AcnBlue.copy(alpha = 0.1f) to AcnBlue
        else -> AcnBlue.copy(alpha = 0.1f) to AcnBlue
    }
}

@Composable
fun UpcomingEvents(events: List<Event>) {
    val now = LocalDate.now()
    val cutoff = now.plusDays(45)
    
    val upcoming = events.filter {
        val date = java.time.Instant.ofEpochMilli(it.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        !date.isBefore(now) && !date.isAfter(cutoff)
    }.sortedBy { it.dateMillis }.take(15)

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Upcoming Events",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        if (upcoming.isEmpty()) {
            Text("No upcoming events in the next 45 days.", color = TextMuted, fontSize = 14.sp)
        } else {
            upcoming.forEach { ev ->
                val date = java.time.Instant.ofEpochMilli(ev.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                val diff = java.time.temporal.ChronoUnit.DAYS.between(now, date)
                val whenStr = when (diff) {
                    0L -> "Today"
                    1L -> "Tomorrow"
                    else -> "In $diff days"
                }
                
                val (_, dotColor) = getTypeColors(ev.type)
                
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = Surface2,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(4.dp).height(30.dp).background(dotColor, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "$whenStr • ${date.format(DateTimeFormatter.ofPattern("MMM dd"))}", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                            Text(text = ev.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (ev.timeString != null) {
                                Text(text = ev.timeString, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
