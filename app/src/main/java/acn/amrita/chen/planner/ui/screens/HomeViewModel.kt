package acn.amrita.chen.planner.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import acn.amrita.chen.planner.data.AcnRepository
import acn.amrita.chen.planner.data.AppDatabase
import acn.amrita.chen.planner.data.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── UI Models ───────────────────────────────────────────
data class ClassSessionUi(
    val subject: String,
    val room: String,
    val timeLabel: String,
    val startMinutes: Int,      // minutes from midnight
    val endMinutes: Int,
    val isCancelled: Boolean = false,
    val isRoomChanged: Boolean = false
)

data class AttendanceAlertUi(
    val subject: String,
    val percentage: Float,
    val status: AttStatus
)

enum class AttStatus { SAFE, WARN, DANGER }

data class AssignmentDueUi(
    val title: String,
    val subject: String,
    val dueLabel: String,
    val isUrgent: Boolean
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = AcnRepository(db)
    
    private val _todayClasses = MutableStateFlow<List<ClassSessionUi>>(emptyList())
    val todayClasses: StateFlow<List<ClassSessionUi>> = _todayClasses

    private val _attendanceAlerts = MutableStateFlow<List<AttendanceAlertUi>>(emptyList())
    val attendanceAlerts: StateFlow<List<AttendanceAlertUi>> = _attendanceAlerts

    private val _assignmentsDue = MutableStateFlow<List<AssignmentDueUi>>(emptyList())
    val assignmentsDue: StateFlow<List<AssignmentDueUi>> = _assignmentsDue

    private val _userName = MutableStateFlow("Raj") // Default fallback
    val userName: StateFlow<String> = _userName

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    init {
        viewModelScope.launch {
            repository.getUserProfile()?.collectLatest { profile ->
                if (profile != null && profile.name.isNotBlank()) {
                    _userName.value = profile.name.split(" ").firstOrNull()?.capitalize() ?: "Student"
                }
            }
        }
        
        viewModelScope.launch {
            repository.getTodaySchedule().collectLatest { classes ->
                val subjects = repository.getAllSubjects().first()
                // #region agent log
                acn.amrita.chen.planner.debug.DebugAgentLog.log(
                    "HomeViewModel.kt:todayClasses",
                    "Mapped today classes",
                    "C",
                    mapOf(
                        "classCount" to classes.size,
                        "subjectCount" to subjects.size,
                        "subjectIds" to classes.map { it.subjectId }.joinToString(),
                        "dbSubjectIds" to subjects.map { it.id }.joinToString()
                    )
                )
                // #endregion
                _todayClasses.value = classes.map {
                    val startH = it.startTimeMinutes / 60
                    val startM = it.startTimeMinutes % 60
                    val endH = it.endTimeMinutes / 60
                    val endM = it.endTimeMinutes % 60
                    
                    ClassSessionUi(
                        subject = "Subject " + it.subjectId,
                        room = it.overrideRoom ?: it.room,
                        timeLabel = String.format("%02d:%02d - %02d:%02d", startH, startM, endH, endM),
                        startMinutes = it.startTimeMinutes,
                        endMinutes = it.endTimeMinutes,
                        isCancelled = it.status == SessionStatus.CANCELLED,
                        isRoomChanged = it.status == SessionStatus.ROOM_CHANGED
                    )
                }
            }
        }
        
        viewModelScope.launch {
            repository.getAllSubjects().collectLatest { subjects ->
                _attendanceAlerts.value = subjects.map {
                    val percentage = it.attendancePercentage
                    val status = when {
                        percentage < 75f -> AttStatus.DANGER
                        percentage < 80f -> AttStatus.WARN
                        else -> AttStatus.SAFE
                    }
                    
                    AttendanceAlertUi(
                        subject = it.code,
                        percentage = percentage,
                        status = status
                    )
                }
            }
        }
        
        viewModelScope.launch {
            repository.getPendingAssignments().collectLatest { assignments ->
                val now = System.currentTimeMillis()
                // #region agent log
                acn.amrita.chen.planner.debug.DebugAgentLog.log(
                    "HomeViewModel.kt:assignments",
                    "Mapped assignments",
                    "C",
                    mapOf(
                        "count" to assignments.size,
                        "subjectLabels" to assignments.map { "Subject ID " }.joinToString()
                    )
                )
                // #endregion
                _assignmentsDue.value = assignments.map {
                    val daysUntilDue = (it.dueDateMillis - now) / (1000 * 60 * 60 * 24)
                    AssignmentDueUi(
                        title = it.title,
                        subject = "Subject ID ", 
                        dueLabel = dateFormatter.format(Date(it.dueDateMillis)),
                        isUrgent = daysUntilDue <= 3
                    )
                }
            }
        }
    }
}
