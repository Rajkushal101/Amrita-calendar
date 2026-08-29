package acn.amrita.chen.planner.ai

import acn.amrita.chen.planner.data.AcnRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import acn.amrita.chen.planner.data.Event
import acn.amrita.chen.planner.data.Assignment
import acn.amrita.chen.planner.data.AssignmentPriority
import acn.amrita.chen.planner.data.AssignmentStatus
import acn.amrita.chen.planner.data.Subject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Executes AI tool calls against the local repository.
 * AI determines intent and selects tools. Deterministic Kotlin code validates and executes.
 * AI never touches the database directly.
 */
class AiToolExecutor(
    private val context: Context,
    private val repo: AcnRepository,
    private val userRole: String
) {
    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return try {
            when (toolName) {
                "get_today_schedule" -> executeGetTodaySchedule()
                "get_next_class" -> executeGetNextClass()
                "get_attendance" -> executeGetAttendance(args["subjectCode"])
                "get_assignments" -> executeGetAssignments(args["daysAhead"]?.toIntOrNull() ?: 30)
                "get_upcoming_exams" -> executeGetUpcomingExams()
                "get_announcements" -> executeGetAnnouncements(args["limit"]?.toIntOrNull() ?: 5)
                "get_semester_progress" -> executeGetSemesterProgress()
                "get_attendance_what_if" -> executeAttendanceWhatIf(
                    args["subjectCode"] ?: return error("subjectCode required"),
                    args["attend"]?.toBoolean() ?: true
                )
                "add_task" -> executeAddTask(
                    args["subjectId"]?.toIntOrNull() ?: 1,
                    args["title"] ?: return error("title required"),
                    args["dueDateString"] ?: return error("dueDateString required"),
                    args["priority"] ?: "MEDIUM"
                )
                "mark_task_done" -> {
                    val id = args["assignmentId"]?.toIntOrNull() ?: return error("assignmentId required")
                    repo.updateAssignmentStatus(id, AssignmentStatus.SUBMITTED)
                    """{"success": true, "message": "Task marked as done."}"""
                }
                "create_event" -> executeCreateEvent(
                    args["title"] ?: return error("title required"),
                    args["dateString"] ?: return error("dateString required (YYYY-MM-DD)"),
                    args["type"] ?: "PERSONAL",
                    args["timeString"]
                )
                "delete_event" -> {
                    val id = args["eventId"]?.toIntOrNull() ?: return error("eventId required")
                    repo.deleteEvent(id)
                    """{"success": true, "message": "Event deleted."}"""
                }
                "add_subject" -> executeAddSubject(
                    args["code"] ?: return error("code required"),
                    args["name"] ?: return error("name required")
                )
                "override_attendance" -> {
                    repo.overrideAttendance(
                        args["code"] ?: return error("code required"),
                        args["attended"]?.toIntOrNull() ?: return error("attended required"),
                        args["total"]?.toIntOrNull() ?: return error("total required")
                    )
                    """{"success": true, "message": "Attendance overriden."}"""
                }
                "clear_all_notifications" -> {
                    repo.deleteAllAnnouncements()
                    """{"success": true, "message": "All notifications cleared."}"""
                }
                "cancel_class" -> {
                    if (userRole != "FACULTY") return error("Permission denied. Faculty only.")
                    // Simulating for Phase 5 demo
                    """{"success": true, "message": "Class cancelled and students notified."}"""
                }
                "post_announcement" -> {
                    if (userRole != "FACULTY") return error("Permission denied. Faculty only.")
                    // Simulating for Phase 5 demo
                    """{"success": true, "message": "Announcement posted."}"""
                }
                "generate_study_plan" -> executeGenerateStudyPlan()
                else -> error("Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            error(e.message ?: "Tool execution failed")
        }
    }

    private suspend fun executeGetTodaySchedule(): String {
        val sessions = repo.getTodaySchedule().first()
        if (sessions.isEmpty()) return """{"result": "No classes scheduled for today"}"""
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(JSONObject().apply {
                put("subjectId", s.subjectId)
                put("room", s.overrideRoom ?: s.room)
                put("time", formatMinutes(s.startTimeMinutes) + " - " + formatMinutes(s.endTimeMinutes))
                put("status", s.status.name)
            })
        }
        return JSONObject().put("schedule", arr).toString()
    }

    private suspend fun executeGetNextClass(): String {
        val next = repo.getNextClass().first()
            ?: return """{"result": "No upcoming classes today"}"""
        return JSONObject().apply {
            put("subjectId", next.subjectId)
            put("room", next.overrideRoom ?: next.room)
            put("time", formatMinutes(next.startTimeMinutes) + " - " + formatMinutes(next.endTimeMinutes))
            put("status", next.status.name)
        }.toString()
    }

    private suspend fun executeGetAttendance(subjectCode: String?): String {
        val subjects = repo.getAllSubjects().first()
        val filtered = if (subjectCode != null) {
            subjects.filter { it.code.equals(subjectCode, ignoreCase = true) }
        } else subjects

        if (filtered.isEmpty()) return """{"result": "No subjects found"}"""

        val arr = JSONArray()
        filtered.forEach { s ->
            val analysis = repo.analyzeAttendance(s)
            arr.put(JSONObject().apply {
                put("subject", s.name)
                put("code", s.code)
                put("attended", s.attendedClasses)
                put("total", s.totalClasses)
                put("percentage", "%.1f%%".format(analysis.currentPercentage))
                put("status", analysis.status.name)
                put("canMiss", analysis.classesCanAffordToMiss)
                put("needToRecover", analysis.classesNeededToRecover)
            })
        }
        return JSONObject().put("attendance", arr).toString()
    }

    private suspend fun executeGetAssignments(daysAhead: Int): String {
        val assignments = repo.getPendingAssignments().first()
        val cutoff = System.currentTimeMillis() + (daysAhead.toLong() * 86400000L)
        val filtered = assignments.filter { it.dueDateMillis <= cutoff }

        if (filtered.isEmpty()) return """{"result": "No pending assignments"}"""

        val arr = JSONArray()
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        filtered.forEach { a ->
            arr.put(JSONObject().apply {
                put("title", a.title)
                put("due", Instant.ofEpochMilli(a.dueDateMillis).atZone(ZoneId.systemDefault()).format(formatter))
                put("priority", a.priority.name)
                put("status", a.status.name)
            })
        }
        return JSONObject().put("assignments", arr).toString()
    }

    private suspend fun executeGetUpcomingExams(): String {
        val exams = repo.getUpcomingExams().first()
        if (exams.isEmpty()) return """{"result": "No upcoming exams found"}"""

        val arr = JSONArray()
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        exams.forEach { e ->
            arr.put(JSONObject().apply {
                put("title", e.title)
                put("date", Instant.ofEpochMilli(e.dateMillis).atZone(ZoneId.systemDefault()).format(formatter))
            })
        }
        return JSONObject().put("exams", arr).toString()
    }

    private suspend fun executeGetAnnouncements(limit: Int): String {
        val all = repo.getAllAnnouncements().first()
        val limited = all.take(limit)
        if (limited.isEmpty()) return """{"result": "No announcements"}"""

        val arr = JSONArray()
        limited.forEach { a ->
            arr.put(JSONObject().apply {
                put("title", a.title)
                put("body", a.body)
                put("author", a.authorName)
                put("priority", a.priority.name)
            })
        }
        return JSONObject().put("announcements", arr).toString()
    }

    private fun executeGetSemesterProgress(): String {
        val progress = repo.getSemesterProgress()
        return JSONObject().apply {
            put("percentComplete", "%.1f%%".format(progress.percentComplete))
            put("elapsedDays", progress.elapsedDays)
            put("totalDays", progress.totalDays)
            put("nextMilestone", progress.nextMilestone)
            put("daysToMilestone", progress.daysToMilestone)
        }.toString()
    }

    private suspend fun executeAttendanceWhatIf(subjectCode: String, attend: Boolean): String {
        val subjects = repo.getAllSubjects().first()
        val subject = subjects.find { it.code.equals(subjectCode, ignoreCase = true) }
            ?: return error("Subject '$subjectCode' not found")

        val futureAttend = if (attend) 1 else 0
        val simPct = repo.simulateAttendance(subject.totalClasses, subject.attendedClasses, 1, futureAttend)

        return JSONObject().apply {
            put("subject", subject.name)
            put("currentPercentage", "%.1f%%".format(
                if (subject.totalClasses > 0) (subject.attendedClasses.toFloat() / subject.totalClasses) * 100f else 0f
            ))
            put("afterAction", if (attend) "attend" else "skip")
            put("projectedPercentage", "%.1f%%".format(simPct))
            put("recommendation", if (simPct < 75f) "NOT RECOMMENDED — will drop below 75%" else "Safe to proceed")
        }.toString()
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val amPm = if (h >= 12) "PM" else "AM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, amPm)
    }
    
    private fun executeCreateReminder(title: String, timeString: String): String {
        try {
            val time = LocalTime.parse(timeString)
            val now = LocalTime.now()
            var date = LocalDate.now()
            if (time.isBefore(now)) {
                date = date.plusDays(1)
            }
            val timeMillis = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, acn.amrita.chen.planner.receiver.ReminderReceiver::class.java).apply {
                putExtra("event_title", title)
                putExtra("event_id", (Math.random() * 1000).toInt())
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeMillis,
                pendingIntent
            )
            return """{"success": true, "message": "Reminder set for $timeString"}"""
        } catch (e: Exception) {
            return error("Failed to parse time or set reminder: ${e.message}")
        }
    }
    
    private suspend fun executeAddTask(subjectId: Int, title: String, dueDateString: String, priorityStr: String): String {
        try {
            val date = LocalDate.parse(dueDateString)
            val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val priority = try { AssignmentPriority.valueOf(priorityStr) } catch(e: Exception) { AssignmentPriority.MEDIUM }
            
            repo.addAssignment(
                Assignment(
                    subjectId = subjectId,
                    title = title,
                    dueDateMillis = dateMillis,
                    priority = priority,
                    status = AssignmentStatus.NOT_STARTED
                )
            )
            return """{"success": true, "message": "Task '$title' added."}"""
        } catch (e: Exception) {
            return error("Failed to add task: ${e.message}")
        }
    }
    
    private suspend fun executeCreateEvent(title: String, dateString: String, type: String, timeString: String?): String {
        try {
            val date = LocalDate.parse(dateString)
            val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val event = Event(
                title = title,
                dateMillis = dateMillis,
                type = type,
                timeString = timeString,
                notes = "Created via Assistant",
                hasReminder = false,
                reminderTimeMillis = null,
                reminderType = "none"
            )
            repo.addEvent(event)
            return """{"success": true, "message": "Event '$title' added on $dateString"}"""
        } catch (e: Exception) {
            return error("Failed to create event: ${e.message}")
        }
    }

    private suspend fun executeAddSubject(code: String, name: String): String {
        try {
            repo.insertSubject(
                Subject(
                    code = code,
                    name = name,
                    faculty = "Unknown"
                )
            )
            return """{"success": true, "message": "Subject $code added."}"""
        } catch (e: Exception) {
            return error("Failed to add subject: ${e.message}")
        }
    }
    
    private suspend fun executeGenerateStudyPlan(): String {
        val exams = repo.getUpcomingExams().first()
        if (exams.isEmpty()) return """{"result": "No upcoming exams to plan for"}"""
        
        // Mock structured study plan generation (since we can't easily chain Gemini calls inside here without passing the client)
        val arr = JSONArray()
        exams.forEach { e ->
            val date = Instant.ofEpochMilli(e.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            arr.put(JSONObject().apply {
                put("task", "Review modules for ${e.title}")
                put("suggestedDate", date.minusDays(2).toString())
            })
            arr.put(JSONObject().apply {
                put("task", "Mock test for ${e.title}")
                put("suggestedDate", date.minusDays(1).toString())
            })
        }
        return JSONObject().put("studyPlan", arr).toString()
    }

    private fun error(msg: String): String = """{"error": "$msg"}"""
}
