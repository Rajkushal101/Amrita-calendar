package acn.amrita.chen.planner.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.FirebaseAuth

/**
 * Central repository — single source of truth for all data.
 * Room is the offline cache; Firestore sync will be added in Phase 2 backend.
 */
class AcnRepository(private val db: AppDatabase) {

    private val eventDao = db.eventDao()
    private val subjectDao = db.subjectDao()
    private val classSessionDao = db.classSessionDao()
    private val assignmentDao = db.assignmentDao()
    private val attendanceRecordDao = db.attendanceRecordDao()
    private val announcementDao = db.announcementDao()
    private val userProfileDao = db.userProfileDao()

    private val firestore = FirebaseFirestore.getInstance()
    private var announcementsListener: ListenerRegistration? = null
    private var classSessionsListener: ListenerRegistration? = null

    // ── Assignments ─────────────────────────────────────────────
    // Moved below.

    // ── User Profiles ────────────────────────────────────────────────

    // ── Schedule ────────────────────────────────────────────────

    fun getTodaySchedule(): Flow<List<ClassSession>> {
        val today = LocalDate.now().dayOfWeek.value
        return classSessionDao.getSessionsForDay(today)
    }

    fun getScheduleForDay(dayOfWeek: Int): Flow<List<ClassSession>> =
        classSessionDao.getSessionsForDay(dayOfWeek)

    fun getNextClass(): Flow<ClassSession?> {
        val today = LocalDate.now().dayOfWeek.value
        val nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        return classSessionDao.getSessionsForDay(today).map { sessions ->
            sessions.firstOrNull { it.startTimeMinutes > nowMinutes }
        }
    }

    suspend fun insertSessions(sessions: List<ClassSession>) =
        classSessionDao.insertSessions(sessions)

    suspend fun saveTimetable(entries: List<TimetableEntry>) {
        // Clear all existing class sessions first so we don't duplicate
        classSessionDao.deleteAllSessions()

        val newSessions = mutableListOf<ClassSession>()
        for (entry in entries) {
            // Find or create subject
            var subject = subjectDao.getSubjectByCode(entry.subjectCode)
            val subjectId: Int
            if (subject == null) {
                val newSubject = Subject(
                    name = entry.subjectName,
                    code = entry.subjectCode,
                    faculty = ""
                )
                subjectId = subjectDao.insertSubject(newSubject).toInt()
            } else {
                subjectId = subject.id
            }

            // Convert HH:mm to minutes from midnight
            val startParts = entry.startTime.split(":")
            val startMinutes = if (startParts.size == 2) {
                startParts[0].toIntOrNull()?.times(60)?.plus(startParts[1].toIntOrNull() ?: 0) ?: 0
            } else 0
            
            val endParts = entry.endTime.split(":")
            val endMinutes = if (endParts.size == 2) {
                endParts[0].toIntOrNull()?.times(60)?.plus(endParts[1].toIntOrNull() ?: 0) ?: 0
            } else 0

            newSessions.add(
                ClassSession(
                    subjectId = subjectId,
                    facultyId = "",
                    room = entry.room,
                    dayOfWeek = entry.day,
                    startTimeMinutes = startMinutes,
                    endTimeMinutes = endMinutes,
                    section = "",
                    semester = 1,
                    batch = ""
                )
            )
        }
        classSessionDao.insertSessions(newSessions)
    }

    fun startClassSessionsSync() {
        classSessionsListener?.remove()
        classSessionsListener = firestore.collection("class_sessions")
            .addSnapshotListener { snapshot, e ->
                // #region agent log
                acn.amrita.chen.planner.debug.DebugAgentLog.log(
                    "AcnRepository.kt:startClassSessionsSync",
                    "Firestore class_sessions snapshot",
                    "B",
                    mapOf(
                        "error" to (e?.message),
                        "snapshotNull" to (snapshot == null),
                        "docCount" to (snapshot?.size() ?: -1)
                    )
                )
                // #endregion
                if (e != null || snapshot == null) return@addSnapshotListener
                
                val sessionsList = snapshot.documents.mapNotNull { doc ->
                    try {
                        val subjectId = doc.getLong("subjectId")?.toInt() ?: return@mapNotNull null
                        val facultyId = doc.getString("facultyId") ?: ""
                        val room = doc.getString("room") ?: ""
                        val dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: 1
                        val startTimeMinutes = doc.getLong("startTimeMinutes")?.toInt() ?: 0
                        val endTimeMinutes = doc.getLong("endTimeMinutes")?.toInt() ?: 0
                        val section = doc.getString("section") ?: ""
                        val semester = doc.getLong("semester")?.toInt() ?: 1
                        val batch = doc.getString("batch") ?: ""
                        val statusString = doc.getString("status") ?: "SCHEDULED"
                        val status = try { SessionStatus.valueOf(statusString) } catch (e: Exception) { SessionStatus.SCHEDULED }
                        val cancelledBy = doc.getString("cancelledBy")
                        val cancelledAt = doc.getLong("cancelledAt")
                        val overrideRoom = doc.getString("overrideRoom")
                        
                        ClassSession(
                            subjectId = subjectId,
                            facultyId = facultyId,
                            room = room,
                            dayOfWeek = dayOfWeek,
                            startTimeMinutes = startTimeMinutes,
                            endTimeMinutes = endTimeMinutes,
                            section = section,
                            semester = semester,
                            batch = batch,
                            status = status,
                            cancelledBy = cancelledBy,
                            cancelledAt = cancelledAt,
                            overrideRoom = overrideRoom,
                            firestoreId = doc.id
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    classSessionDao.deleteAllSessions()
                    classSessionDao.insertSessions(sessionsList)
                }
            }
    }

    fun stopClassSessionsSync() {
        classSessionsListener?.remove()
        classSessionsListener = null
    }

    // ── Attendance ──────────────────────────────────────────────

    fun getAttendanceForSubject(subjectId: Int): Flow<List<AttendanceRecord>> =
        attendanceRecordDao.getRecordsForSubject(subjectId)

    suspend fun markAttendance(record: AttendanceRecord) {
        val generatedId = attendanceRecordDao.insertRecord(record)
        
        // Sync to firestore if logged in
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            val recordRef = firestore.collection("attendance").document(uid)
                .collection("records").document()
                
            val recordWithId = record.copy(id = generatedId.toInt(), firestoreId = recordRef.id)
            attendanceRecordDao.insertRecord(recordWithId) // update with firestore id
            
            recordRef.set(recordWithId)
        }
    }

    data class AttendanceAnalysis(
        val subject: Subject,
        val currentPercentage: Float,
        val status: AttendanceStatus,
        val classesCanAffordToMiss: Int,
        val classesNeededToRecover: Int
    )

    enum class AttendanceStatus { SAFE, WARNING, DANGER }

    fun analyzeAttendance(subject: Subject): AttendanceAnalysis {
        val pct = if (subject.totalClasses > 0)
            (subject.attendedClasses.toFloat() / subject.totalClasses) * 100f
        else 0f

        val status = when {
            pct >= 85f -> AttendanceStatus.SAFE
            pct >= 75f -> AttendanceStatus.WARNING
            else -> AttendanceStatus.DANGER
        }

        // How many more classes can the student miss and stay >= 75%?
        val canMiss = if (subject.totalClasses > 0) {
            var missed = 0
            var total = subject.totalClasses
            var attended = subject.attendedClasses
            while ((attended.toFloat() / (total + 1)) >= 0.75f) {
                total++
                missed++
            }
            missed
        } else 0

        // How many consecutive classes needed to recover to 75%?
        val needToRecover = if (pct < 75f && subject.totalClasses > 0) {
            var total = subject.totalClasses
            var attended = subject.attendedClasses
            var needed = 0
            while ((attended.toFloat() / total) < 0.75f) {
                total++
                attended++
                needed++
            }
            needed
        } else 0

        return AttendanceAnalysis(subject, pct, status, canMiss, needToRecover)
    }

    fun simulateAttendance(totalClasses: Int, attended: Int, futureClasses: Int, attendNext: Int): Float {
        return (attended + attendNext).toFloat() / (totalClasses + futureClasses) * 100f
    }

    // ── Assignments ─────────────────────────────────────────────

    fun getPendingAssignments(): Flow<List<Assignment>> =
        assignmentDao.getPendingAssignments()

    fun getAllAssignments(): Flow<List<Assignment>> =
        assignmentDao.getAllAssignments()

    suspend fun addAssignment(assignment: Assignment) =
        assignmentDao.insertAssignment(assignment)

    suspend fun updateAssignmentStatus(id: Int, status: AssignmentStatus) =
        assignmentDao.updateAssignmentStatus(id, status)

    // ── Events ──────────────────────────────────────────────────

    fun getAllEvents(): Flow<List<Event>> = eventDao.getAllEvents()
    
    suspend fun addEvent(event: Event) = eventDao.insertEvent(event)

    fun getUpcomingExams(): Flow<List<Event>> = eventDao.getAllEvents().map { events ->
        val nowMillis = System.currentTimeMillis()
        events.filter { it.type == "exam" && it.dateMillis >= nowMillis }
    }

    // ── Subjects ────────────────────────────────────────────────

    fun getAllSubjects(): Flow<List<Subject>> = subjectDao.getAllSubjects()

    suspend fun insertSubject(subject: Subject) = subjectDao.insertSubject(subject)
    
    suspend fun syncScrapedAttendance(html: String) {
        val parsedList = AumsScraper.parseAttendanceHtml(html)
        val existingSubjects = subjectDao.getAllSubjectsSync()
        
        for (parsed in parsedList) {
            val percentage = if (parsed.totalClasses > 0) {
                (parsed.attendedClasses.toFloat() / parsed.totalClasses) * 100f
            } else 0f
            
            val exists = existingSubjects.any { it.code == parsed.subjectCode }
            if (exists) {
                subjectDao.updateAttendanceByCode(
                    code = parsed.subjectCode,
                    total = parsed.totalClasses,
                    attended = parsed.attendedClasses,
                    percentage = percentage
                )
            } else {
                // If subject doesn't exist, maybe we can create it minimally
                val newSubject = Subject(
                    id = 0,
                    code = parsed.subjectCode,
                    name = parsed.subjectName,
                    faculty = "Unknown",
                    credits = 3,
                    totalClasses = parsed.totalClasses,
                    attendedClasses = parsed.attendedClasses,
                    attendancePercentage = percentage
                )
                subjectDao.insertSubject(newSubject)
            }
        }
    }

    // ── Announcements ───────────────────────────────────────────

    fun getAllAnnouncements(): Flow<List<Announcement>> = announcementDao.getAllAnnouncements()

    suspend fun addAnnouncement(announcement: Announcement) =
        announcementDao.insertAnnouncement(announcement)
        
    fun startAnnouncementsSync() {
        announcementsListener?.remove()
        announcementsListener = firestore.collection("announcements")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
                val announcementsList = snapshot.documents.mapNotNull { doc ->
                    try {
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val body = doc.getString("body") ?: ""
                        val authorName = doc.getString("authorName") ?: "Admin"
                        val postedAtMillis = doc.getLong("postedAtMillis") ?: System.currentTimeMillis()
                        val isPinned = doc.getBoolean("isPinned") ?: false
                        val urgencyLevel = doc.getString("urgencyLevel") ?: "normal"
                        val targetAudience = doc.getString("targetAudience") ?: "ALL"
                        val priorityString = doc.getString("priority") ?: "NORMAL"
                        val priority = try { AnnouncementPriority.valueOf(priorityString) } catch (e: Exception) { AnnouncementPriority.NORMAL }
                        val expiresAt = doc.getLong("expiresAt")
                        val authorRole = doc.getString("authorRole") ?: "FACULTY"
                        
                        Announcement(
                            title = title,
                            body = body,
                            authorName = authorName,
                            postedAtMillis = postedAtMillis,
                            isPinned = isPinned,
                            urgencyLevel = urgencyLevel,
                            targetAudience = targetAudience,
                            priority = priority,
                            expiresAt = expiresAt,
                            authorRole = authorRole,
                            firestoreId = doc.id
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                // Launch coroutine in a background thread to update Room
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    // To avoid duplicates, we can clear and insert, or use upsert if we implement it.
                    // For now, we clear the existing firestore ones and insert new ones
                    announcementDao.deleteAllAnnouncements()
                    announcementDao.insertAnnouncements(announcementsList)
                }
            }
    }

    fun stopAnnouncementsSync() {
        announcementsListener?.remove()
        announcementsListener = null
    }

    // ── Semester Progress ───────────────────────────────────────

    data class SemesterProgress(
        val totalDays: Int,
        val elapsedDays: Int,
        val percentComplete: Float,
        val nextMilestone: String,
        val daysToMilestone: Long
    )

    fun getSemesterProgress(): SemesterProgress {
        val now = LocalDate.now()
        val zone = ZoneId.systemDefault()

        // Determine which semester we're in
        val oddStart = LocalDate.of(2026, 6, 10)
        val oddEnd = LocalDate.of(2026, 10, 16)
        val evenStart = LocalDate.of(2026, 11, 18)
        val evenEnd = LocalDate.of(2027, 3, 31)

        val (semStart, semEnd) = when {
            now.isBefore(evenStart) -> oddStart to oddEnd
            else -> evenStart to evenEnd
        }

        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(semStart, semEnd).toInt()
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(semStart, now).toInt().coerceIn(0, totalDays)
        val pct = if (totalDays > 0) (elapsed.toFloat() / totalDays) * 100f else 0f

        // Find next milestone
        val milestones = listOf(
            "Midterm Exam" to LocalDate.of(2026, 8, 7),
            "End Sem Practical Exam" to LocalDate.of(2026, 10, 5),
            "Last Instruction Day" to LocalDate.of(2026, 10, 16),
            "End Semester Exam" to LocalDate.of(2026, 10, 22),
            "Even Sem Midterm" to LocalDate.of(2027, 1, 27),
            "Even Sem End Practical" to LocalDate.of(2027, 3, 17),
            "Even Sem Last Day" to LocalDate.of(2027, 3, 31),
            "Even Sem End Exam" to LocalDate.of(2027, 4, 2)
        )

        val next = milestones.firstOrNull { it.second.isAfter(now) }
        val nextName = next?.first ?: "Semester Complete"
        val daysTo = next?.let { java.time.temporal.ChronoUnit.DAYS.between(now, it.second) } ?: 0

        return SemesterProgress(totalDays, elapsed, pct, nextName, daysTo)
    }
}
