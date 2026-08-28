package acn.amrita.chen.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.viewmodel.compose.viewModel



// Colour tokens
private val AcnRed      = Color(0xFFC62828)
private val AcnSurface  = Color(0xFF1A1A1A)
private val AcnCard     = Color(0xFF242424)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSec     = Color(0xFFAAAAAA)

// ── Domain model ──────────────────────────────────────────────────────────────


@Composable
fun AnnouncementsScreen(viewModel: acn.amrita.chen.planner.ui.MainViewModel) {
    val annVm: AnnouncementsViewModel = viewModel()
    val announcements by annVm.announcements.collectAsState()

    AnnouncementsScreenContent(
        userRole = "STUDENT",
        announcements = announcements,
        onPostAnnouncement = {}
    )
}
@Composable
fun AnnouncementsScreenContent(
    userRole: String = "STUDENT",           // "STUDENT" | "FACULTY"
    announcements: List<AnnouncementUi> = sampleAnnouncements(),
    onPostAnnouncement: () -> Unit = {}
) {
    var filterTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Urgent", "Important", "Normal")

    val filtered = when (filterTab) {
        1    -> announcements.filter { it.priority == AnnPriority.URGENT }
        2    -> announcements.filter { it.priority == AnnPriority.IMPORTANT }
        3    -> announcements.filter { it.priority == AnnPriority.NORMAL }
        else -> announcements
    }.sortedWith(compareByDescending<AnnouncementUi> { it.isPinned }
        .thenByDescending { it.priority.ordinal.inv() })

    Box(modifier = Modifier.fillMaxSize().background(AcnSurface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notices", fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                if (announcements.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AcnRed.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("${announcements.size}", fontSize = 13.sp,
                            color = AcnRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Filter tabs
            ScrollableTabRow(
                selectedTabIndex = filterTab,
                containerColor   = AcnSurface,
                contentColor     = AcnRed,
                edgePadding      = 16.dp,
                divider          = {}
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = filterTab == idx,
                        onClick  = { filterTab = idx },
                        text     = {
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = if (filterTab == idx) FontWeight.Bold else FontWeight.Normal,
                                color = if (filterTab == idx) AcnRed else TextSec
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // List
            if (filtered.isEmpty()) {
                EmptyNoticesView()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { ann ->
                        AnnouncementCard(ann)
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // Faculty FAB
        if (userRole == "FACULTY") {
            ExtendedFloatingActionButton(
                onClick = onPostAnnouncement,
                containerColor = AcnRed,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Post Notice", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Announcement card ─────────────────────────────────────────────────────────
@Composable
private fun AnnouncementCard(ann: AnnouncementUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: priority badge + pin icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityBadge(ann.priority)
                    if (ann.isPinned) {
                        Icon(Icons.Default.PushPin, null,
                            tint = AcnRed, modifier = Modifier.size(14.dp))
                    }
                }
                Text(ann.timeAgo, fontSize = 11.sp, color = TextSec)
            }

            Spacer(Modifier.height(10.dp))

            // Title
            Text(ann.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = TextPrimary)

            Spacer(Modifier.height(6.dp))

            // Body
            Text(ann.body, fontSize = 13.sp, color = TextSec, maxLines = 3,
                overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)

            Spacer(Modifier.height(12.dp))

            // Footer row
            HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(AcnRed.copy(alpha = 0.2f))
                    ) {
                        Text(ann.author.first().toString(), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = AcnRed)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(ann.author, fontSize = 12.sp, color = TextSec)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, null,
                        tint = Color(0xFF666666), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ann.audience, fontSize = 11.sp, color = Color(0xFF666666))
                }
            }
        }
    }
}

// ── Priority badge ────────────────────────────────────────────────────────────
@Composable
private fun PriorityBadge(priority: AnnPriority) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(priority.color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(priority.label, fontSize = 10.sp, color = priority.color,
            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyNoticesView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Notifications, null,
                tint = Color(0xFF3A3A3A), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("No notices", fontSize = 16.sp, color = TextSec)
            Spacer(Modifier.height(4.dp))
            Text("New notices will appear here", fontSize = 13.sp, color = Color(0xFF555555))
        }
    }
}

// ── Sample data ───────────────────────────────────────────────────────────────
private fun sampleAnnouncements() = listOf(
    AnnouncementUi(
        id = "1", title = "Midterm Exam Schedule Released",
        body = "The midterm examination schedule for VII semester has been published. Exams begin on 07 August 2026. Please check the official portal for detailed timetable.",
        author = "Academic Section",
        timeAgo = "2h ago",
        priority = AnnPriority.URGENT,
        isPinned = true,
        audience = "VII Semester"
    ),
    AnnouncementUi(
        id = "2", title = "Android App Development — Lab Moved",
        body = "Tomorrow's Android lab session (20CYS404) has been moved from AB2-103 to Lab-5 due to maintenance work. Please note the room change.",
        author = "Dr. Priya",
        timeAgo = "4h ago",
        priority = AnnPriority.IMPORTANT,
        audience = "CYS-B Section"
    ),
    AnnouncementUi(
        id = "3", title = "Independence Day Holiday",
        body = "15 August 2026 is a declared holiday. All classes and labs stand cancelled. The campus will be closed. Independence Day celebrations will be held on the 14th.",
        author = "Admin",
        timeAgo = "Yesterday",
        priority = AnnPriority.NORMAL,
        isPinned = false,
        audience = "All Students"
    ),
    AnnouncementUi(
        id = "4", title = "Final Year Project — Review Reminder",
        body = "Project reviews are scheduled for 31 August 2026. All final year project teams must submit their progress report by 29 August.",
        author = "Dr. S. Udhaya Kumar",
        timeAgo = "2 days ago",
        priority = AnnPriority.IMPORTANT,
        audience = "Final Year CYS"
    ),
)

