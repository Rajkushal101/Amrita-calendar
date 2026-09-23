package acn.amrita.chen.planner.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

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
    private val subjectSyllabusDao = db.subjectSyllabusDao()

    private val firestore = FirebaseFirestore.getInstance()
    private var announcementsListener: ListenerRegistration? = null
    private var classSessionsListener: ListenerRegistration? = null

    // ── Assignments ─────────────────────────────────────────────
    // Moved below.

    // ── User Profiles ────────────────────────────────────────────────
    fun getUserProfile(): Flow<UserProfile?> = userProfileDao.getUserProfile()

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

    // Legacy collections have no cohort ownership; never synchronize them into personal data.
    fun startClassSessionsSync() = Unit
    fun stopClassSessionsSync() = Unit

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

    suspend fun overrideAttendance(code: String, attended: Int, total: Int) {
        val pct = if (total > 0) (attended.toFloat() / total) * 100f else 0f
        subjectDao.updateAttendanceByCode(code, total, attended, pct)
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

    suspend fun deleteEvent(eventId: Int) = eventDao.deleteEvent(eventId)

    fun getUpcomingExams(): Flow<List<Event>> = eventDao.getAllEvents().map { events ->
        val nowMillis = System.currentTimeMillis()
        events.filter { it.type == "exam" && it.dateMillis >= nowMillis }
    }

    // ── Subjects ────────────────────────────────────────────────

    fun getAllSubjects(): Flow<List<Subject>> = subjectDao.getAllSubjects()

    suspend fun insertSubject(subject: Subject) = subjectDao.insertSubject(subject)

    // --- Subject Syllabus & Project ---
    fun getUnitsForSubject(subjectId: Int): Flow<List<SubjectUnit>> = subjectSyllabusDao.getUnitsForSubject(subjectId)
    suspend fun insertUnit(unit: SubjectUnit): Long = subjectSyllabusDao.insertUnit(unit)
    suspend fun deleteUnit(unit: SubjectUnit) = subjectSyllabusDao.deleteUnit(unit)

    fun getTopicsForUnit(unitId: Int): Flow<List<SubjectTopic>> = subjectSyllabusDao.getTopicsForUnit(unitId)
    suspend fun insertTopic(topic: SubjectTopic): Long = subjectSyllabusDao.insertTopic(topic)
    suspend fun deleteTopic(topic: SubjectTopic) = subjectSyllabusDao.deleteTopic(topic)
    
    suspend fun getTopicsForUnitsSync(unitIds: List<Int>): List<SubjectTopic> = subjectSyllabusDao.getTopicsForUnitsSync(unitIds)
    suspend fun getUnitsForSubjectSync(subjectId: Int): List<SubjectUnit> = subjectSyllabusDao.getUnitsForSubjectSync(subjectId)

    fun getProjectForSubject(subjectId: Int): Flow<SubjectProject?> = subjectSyllabusDao.getProjectForSubject(subjectId)
    suspend fun insertProject(project: SubjectProject): Long = subjectSyllabusDao.insertProject(project)
    suspend fun deleteProject(project: SubjectProject) = subjectSyllabusDao.deleteProject(project)
    suspend fun getProjectForSubjectSync(subjectId: Int): SubjectProject? = subjectSyllabusDao.getProjectForSubjectSync(subjectId)
    
    suspend fun syncScrapedAttendance(html: String, semester: Int = 0) {
        val parsedData = AumsScraper.parseAttendanceHtml(html)
        val existingSubjects = subjectDao.getAllSubjectsSync()
        
        // Update UserProfile if name was found
        parsedData.userName?.let { name ->
            val profile = userProfileDao.getProfileSync()
            if (profile != null) {
                userProfileDao.insertUserProfile(profile.copy(name = name))
            } else {
                userProfileDao.insertUserProfile(UserProfile(name = name, semester = semester))
            }
        }
        
        for (parsed in parsedData.attendanceList) {
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
                val newSubject = Subject(
                    id = 0,
                    code = parsed.subjectCode,
                    name = parsed.subjectName,
                    faculty = "Unknown",
                    credits = 3,
                    totalClasses = parsed.totalClasses,
                    attendedClasses = parsed.attendedClasses,
                    attendancePercentage = percentage,
                    semester = semester
                )
                subjectDao.insertSubject(newSubject)
            }
        }
    }

    // ── Announcements ───────────────────────────────────────────

    fun getAllAnnouncements(): Flow<List<Announcement>> = announcementDao.getAllAnnouncements()

    suspend fun addAnnouncement(announcement: Announcement) {
        announcementDao.insertAnnouncement(announcement)
    }
    suspend fun deleteAllAnnouncements() {
        // This legacy action only clears the local notice cache, never shared documents.
        announcementDao.deleteAllAnnouncements()
    }
    fun startAnnouncementsSync() = Unit
    fun stopAnnouncementsSync() = Unit

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

    suspend fun getSubjectByCodeSynchronously(code: String): Subject? {
        return subjectDao.getSubjectByCode(code)
    }

    suspend fun saveSubjectSyllabus(subjectId: Int, units: List<Pair<SubjectUnit, List<String>>>) {
        subjectSyllabusDao.deleteUnitsForSubject(subjectId)
        for ((unit, topics) in units) {
            val unitId = subjectSyllabusDao.insertUnit(unit.copy(subjectId = subjectId)).toInt()
            val topicEntities = topics.map { SubjectTopic(unitId = unitId, title = it) }
            subjectSyllabusDao.insertTopics(topicEntities)
        }
    }

    suspend fun saveSubjectProject(project: SubjectProject) {
        val existing = subjectSyllabusDao.getProjectForSubjectSync(project.subjectId)
        if (existing != null) {
            subjectSyllabusDao.insertProject(project.copy(id = existing.id))
        } else {
            subjectSyllabusDao.insertProject(project)
        }
    }

    suspend fun getAllSessionsSynchronously(): List<ClassSession> = classSessionDao.getAllSessions().first()
    suspend fun updateSession(session: ClassSession) = classSessionDao.insertSession(session)
    suspend fun deleteSession(id: Int) = classSessionDao.deleteSession(id)
    
    suspend fun getAllEventsSynchronously(): List<Event> = eventDao.getAllEvents().first()
    suspend fun updateEvent(event: Event) = eventDao.updateEvent(event)
}
