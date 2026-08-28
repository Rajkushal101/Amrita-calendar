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
import kotlinx.coroutines.launch

// ── UI Models ───────────────────────────────────────────
data class TimetableSession(
    val periodNumber: Int,          // 1–8
    val subject: String,
    val subjectCode: String,
    val room: String,
    val faculty: String,
    val timeLabel: String,          // "09:00–10:00"
    val type: SessionType = SessionType.THEORY,
    val status: SessionStatus = SessionStatus.SCHEDULED
)

enum class SessionType   { THEORY, LAB, TUTORIAL }

class WeekViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = AcnRepository(db)
    
    private val _timetable = MutableStateFlow<Map<String, List<TimetableSession>>>(emptyMap())
    val timetable: StateFlow<Map<String, List<TimetableSession>>> = _timetable

    val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    init {
        viewModelScope.launch {
            // In a real app we might load all week schedule by dayOfWeek
            // Let's create a map for each day
            for (day in 1..7) {
                launch {
                    repository.getScheduleForDay(day).collectLatest { sessions ->
                        val dayName = DAYS[day - 1]
                        val uiSessions = sessions.mapIndexed { index, s ->
                            // #region agent log
                            if (index == 0) {
                                acn.amrita.chen.planner.debug.DebugAgentLog.log(
                                    "WeekViewModel.kt:map",
                                    "Mapped timetable day",
                                    "C",
                                    mapOf(
                                        "dayName" to dayName,
                                        "count" to sessions.size,
                                        "subjectPlaceholder" to "Subject ID ",
                                        "codePlaceholder" to "Code "
                                    )
                                )
                            }
                            // #endregion
                            val startH = s.startTimeMinutes / 60
                            val startM = s.startTimeMinutes % 60
                            val endH = s.endTimeMinutes / 60
                            val endM = s.endTimeMinutes % 60
                            
                            TimetableSession(
                                periodNumber = index + 1,
                                subject = "Subject ID ",
                                subjectCode = "Code ",
                                room = s.overrideRoom ?: s.room,
                                faculty = s.facultyId,
                                timeLabel = String.format("%02d:%02d - %02d:%02d", startH, startM, endH, endM),
                                type = if (s.section.contains("L")) SessionType.LAB else SessionType.THEORY,
                                status = s.status
                            )
                        }
                        
                        _timetable.value = _timetable.value.toMutableMap().apply {
                            put(dayName, uiSessions)
                        }
                    }
                }
            }
        }
    }
}
