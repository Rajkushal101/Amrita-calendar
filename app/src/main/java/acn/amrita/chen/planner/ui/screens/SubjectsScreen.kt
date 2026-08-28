package acn.amrita.chen.planner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel



// Colour tokens
private val AcnRed      = Color(0xFFC62828)
private val AcnSurface  = Color(0xFF1A1A1A)
private val AcnCard     = Color(0xFF242424)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSec     = Color(0xFFAAAAAA)
private val GreenSafe   = Color(0xFF4CAF50)
private val YellowWarn  = Color(0xFFFFC107)
private val RedDanger   = Color(0xFFF44336)
private val TrackColor  = Color(0xFF3A3A3A)

// ── Domain model ──────────────────────────────────────────────────────────────



@Composable
fun SubjectsScreen(viewModel: acn.amrita.chen.planner.ui.MainViewModel, onNavigateToAums: () -> Unit) {
    val subjectsVm: SubjectsViewModel = viewModel()
    val subjects by subjectsVm.subjects.collectAsState()

    SubjectsScreenContent(
        subjects = subjects,
        onSyncAums = onNavigateToAums
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreenContent(
    subjects: List<SubjectUi> = sampleSubjects(),
    onSyncAums: () -> Unit = {}
) {
    var selectedSubject by remember { mutableStateOf<SubjectUi?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AcnSurface),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Subjects & Attendance",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    val semLabel = "2026-27 Odd Sem"
                    Text(semLabel, fontSize = 13.sp, color = TextSec)
                }
                Button(
                    onClick = onSyncAums,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AcnRed),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sync AUMS", fontSize = 13.sp)
                }
            }
        }

        // Summary row
        item { AttendanceSummaryRow(subjects) }
        item { Spacer(Modifier.height(12.dp)) }

        // Subject cards
        items(subjects) { subject ->
            SubjectAttendanceCard(
                subject = subject,
                onClick  = { selectedSubject = subject }
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    // Bottom sheet for what-if detail
    if (selectedSubject != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSubject = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E),
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .size(40.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF444444))
                )
            }
        ) {
            AttendanceDetailSheet(selectedSubject!!)
        }
    }
}

// ── Summary row (3 chips: safe / warn / danger count) ─────────────────────────
@Composable
private fun AttendanceSummaryRow(subjects: List<SubjectUi>) {
    val safe   = subjects.count { it.status == AttStatus.SAFE }
    val warn   = subjects.count { it.status == AttStatus.WARN }
    val danger = subjects.count { it.status == AttStatus.DANGER }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip("$safe Safe", GreenSafe, Modifier.weight(1f))
        SummaryChip("$warn Warn", YellowWarn, Modifier.weight(1f))
        SummaryChip("$danger Low", RedDanger, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 10.dp)
    ) {
        Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Subject card (matches MyAmrita's circular ring style) ─────────────────────
@Composable
fun SubjectAttendanceCard(subject: SubjectUi, onClick: () -> Unit) {
    val ringColor = when (subject.status) {
        AttStatus.SAFE   -> GreenSafe
        AttStatus.WARN   -> YellowWarn
        AttStatus.DANGER -> RedDanger
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject info
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(subject.code, fontSize = 12.sp, color = AcnRed)
                Spacer(Modifier.height(8.dp))
                Text("${subject.attended} / ${subject.total} classes",
                    fontSize = 12.sp, color = TextSec)
                Spacer(Modifier.height(6.dp))
                // Context hint
                val hint = when (subject.status) {
                    AttStatus.DANGER -> "Need ${subject.classesToRecover} more to recover"
                    AttStatus.WARN   -> "Can miss ${subject.canMiss} more class${if (subject.canMiss == 1) "" else "es"}"
                    AttStatus.SAFE   -> "Safe · ${subject.canMiss} classes buffer"
                }
                Text(hint, fontSize = 11.sp,
                    color = when (subject.status) {
                        AttStatus.DANGER -> RedDanger
                        AttStatus.WARN   -> YellowWarn
                        else             -> GreenSafe.copy(alpha = 0.8f)
                    })
            }
            Spacer(Modifier.width(12.dp))
            // Circular ring (matches MyAmrita)
            AnimatedRing(
                percentage  = subject.percentage,
                ringColor   = ringColor,
                size        = 68.dp,
                strokeWidth = 6.dp
            )
        }
    }
}

// ── Animated circular ring ────────────────────────────────────────────────────
@Composable
fun AnimatedRing(
    percentage: Float,
    ringColor: Color,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp
) {
    val animPct by animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ring"
    )
    val displayPct = if (percentage.isNaN()) "N/A" else "${percentage.roundToInt()}%"

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset  = strokeWidth.toPx() / 2
            drawArc(
                color      = TrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                style      = stroke,
                topLeft    = androidx.compose.ui.geometry.Offset(inset, inset),
                size       = androidx.compose.ui.geometry.Size(
                    this.size.width - strokeWidth.toPx(),
                    this.size.height - strokeWidth.toPx()
                )
            )
            rotate(-90f) {
                drawArc(
                    color      = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animPct,
                    useCenter  = false,
                    style      = stroke,
                    topLeft    = androidx.compose.ui.geometry.Offset(inset, inset),
                    size       = androidx.compose.ui.geometry.Size(
                        this.size.width - strokeWidth.toPx(),
                        this.size.height - strokeWidth.toPx()
                    )
                )
            }
        }
        Text(displayPct, fontSize = if (size < 60.dp) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold, color = ringColor)
    }
}

// ── What-if detail bottom sheet ───────────────────────────────────────────────
@Composable
private fun AttendanceDetailSheet(subject: SubjectUi) {
    var simAttend by remember { mutableIntStateOf(0) }
    var simMiss   by remember { mutableIntStateOf(0) }

    val simAttended  = subject.attended + simAttend
    val simTotal     = subject.total + simAttend + simMiss
    val simPct       = if (simTotal == 0) 0f else simAttended.toFloat() / simTotal * 100f
    val simColor = when {
        simPct >= 85f -> GreenSafe
        simPct >= 75f -> YellowWarn
        else          -> RedDanger
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 0.dp)
            .padding(bottom = 40.dp)
    ) {
        Text(subject.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(subject.code, fontSize = 13.sp, color = AcnRed)
        Spacer(Modifier.height(20.dp))

        // Stats row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBox("Attended", "${subject.attended}")
            StatBox("Total", "${subject.total}")
            StatBox("Required", "75%")
            StatBox("Buffer", "${subject.canMiss} classes")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF333333))
        Spacer(Modifier.height(20.dp))

        // What-if simulator
        Text("What-If Simulator", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = TextPrimary)
        Text("Adjust future classes to see attendance impact",
            fontSize = 12.sp, color = TextSec)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Attend adjuster
            SimAdjuster(
                label   = "Classes to attend",
                value   = simAttend,
                onMinus = { if (simAttend > 0) simAttend-- },
                onPlus  = { simAttend++ },
                color   = GreenSafe,
                modifier = Modifier.weight(1f)
            )
            // Miss adjuster
            SimAdjuster(
                label   = "Classes to skip",
                value   = simMiss,
                onMinus = { if (simMiss > 0) simMiss-- },
                onPlus  = { simMiss++ },
                color   = RedDanger,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Result
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = simColor.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Projected attendance", fontSize = 13.sp, color = TextSec)
                    Text("$simAttended / $simTotal classes", fontSize = 12.sp, color = TextSec)
                }
                Text(
                    "${"%.1f".format(simPct)}%",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = simColor
                )
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextSec)
    }
}

@Composable
private fun SimAdjuster(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcnCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = TextSec, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMinus,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, null, tint = color, modifier = Modifier.size(16.dp))
                }
                Text("$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color,
                    modifier = Modifier.width(30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(
                    onClick = onPlus,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Sample data (replace with ViewModel) ─────────────────────────────────────
private fun sampleSubjects() = listOf(
    SubjectUi("Distributed Systems and Cloud Computing", "20CYS402", attended = 25, total = 26),
    SubjectUi("Internet of Things",                     "19CSE446", attended = 19, total = 26),
    SubjectUi("Web Application Security",               "20CYS403", attended = 21, total = 25),
    SubjectUi("Android Application Development",        "20CYS404", attended = 18, total = 22),
    SubjectUi("Information Security Risk Management",   "20MNG331", attended = 21, total = 22),
    SubjectUi("Secure Software Engineering",            "20CYS401", attended = 23, total = 30),
    SubjectUi("Indian Constitution",                    "19LAW300", attended = 0,  total = 0),
    SubjectUi("Project - Phase 1 / Seminar",            "20CYS495", attended = 0,  total = 0),
)

