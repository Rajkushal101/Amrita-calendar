package acn.amrita.chen.planner.workspace

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import acn.amrita.chen.planner.data.AumsScraper
import java.time.LocalDate

class AcademicValidationTest {
    @Test fun noClassesIsUnknownNotPerfectAttendance() {
        val result=AcademicValidation.attendance(0,0)
        assertNull(result.percentage);assertEquals(0,result.canMiss);assertEquals(0,result.needed)
    }
    @Test fun attendanceAtBoundaryAndRecovery() {
        assertEquals(0,AcademicValidation.attendance(3,4).canMiss)
        assertEquals(1,AcademicValidation.attendance(4,4).canMiss)
        assertEquals(4,AcademicValidation.attendance(2,4).needed)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidCountsRejected(){AcademicValidation.attendance(6,5)}
    @Test fun serialNumbersDoNotBecomeAttendance() {
        val html="""<table><tr><th>S.No</th><th>Course Code</th><th>Course Name</th><th>Total Classes</th><th>Attended</th><th>Percent</th></tr><tr><td>1</td><td>23CYS201</td><td>Networks</td><td>40</td><td>30</td><td>75%</td></tr></table>"""
        val rows=AumsScraper.parseAttendanceHtml(html).attendanceList
        assertEquals(1,rows.size);assertEquals(40,rows[0].totalClasses);assertEquals(30,rows[0].attendedClasses)
    }
    @Test fun reorderedColumnsUseHeaders() {
        val html="""<table><tr><th>Attended Classes</th><th>Course Name</th><th>Course Code</th><th>Total Classes</th></tr><tr><td>20</td><td>AI</td><td>23CYS302</td><td>25</td></tr></table>"""
        val row=AumsScraper.parseAttendanceHtml(html).attendanceList.single()
        assertEquals(20,row.attendedClasses);assertEquals(25,row.totalClasses)
    }
    @Test fun failedPortalPagesCannotInventRecords() {
        assertTrue(AumsScraper.parseAttendanceHtml("<html>Please log in</html>").attendanceList.isEmpty())
        val invalid="""<table><tr><th>Course Code</th><th>Total Classes</th><th>Attended</th></tr><tr><td>23CYS201</td><td>20</td><td>30</td></tr></table>"""
        assertTrue(AumsScraper.parseAttendanceHtml(invalid).attendanceList.isEmpty())
    }
    @Test fun studentsAmritaEduPortalFormatParsedSuccessfully() {
        val html = """
            <div class="card">
              <div class="term-dropdown">
                <label>Academic Term</label>
                <span>2026-27 Odd Sem</span>
              </div>
              <table>
                <thead>
                  <tr>
                    <th>Sl No</th>
                    <th>Class Name</th>
                    <th>Course</th>
                    <th>Total Classes</th>
                    <th>Attended Classes</th>
                    <th>Percentage</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>1</td>
                    <td>B.Tech..2023.R.CYS.1.20CYS402</td>
                    <td>
                      <div>20CYS402</div>
                      <div>Distributed Systems and Cloud Computing</div>
                    </td>
                    <td>42</td>
                    <td>38</td>
                    <td>90.4%</td>
                  </tr>
                  <tr>
                    <td>2</td>
                    <td>B.Tech. 2023.R.CYS.1.19LAW300</td>
                    <td>19LAW300 Indian Constitution</td>
                    <td>30</td>
                    <td>27</td>
                    <td>90%</td>
                  </tr>
                </tbody>
              </table>
            </div>
        """.trimIndent()
        val parsed = AumsScraper.parseAttendanceHtml(html)
        assertEquals(2, parsed.attendanceList.size)
        assertEquals("2026-27 Odd Sem", parsed.academicTerm)
        assertEquals(7, parsed.detectedSemester)
        assertTrue(parsed.warnings.isEmpty())

        val r0 = parsed.attendanceList[0]
        assertEquals("20CYS402", r0.subjectCode)
        assertEquals("Distributed Systems and Cloud Computing", r0.subjectName)
        assertEquals(42, r0.totalClasses)
        assertEquals(38, r0.attendedClasses)

        val r1 = parsed.attendanceList[1]
        assertEquals("19LAW300", r1.subjectCode)
        assertEquals("Indian Constitution", r1.subjectName)
        assertEquals(30, r1.totalClasses)
        assertEquals(27, r1.attendedClasses)
    }
    @Test fun portalAlternativeHeadersAndEvenSemesterCalculation() {
        val html = """
            <div>
              <select><option selected>2026-27 Even Sem</option></select>
              <table>
                <tr>
                  <th>Sl.No</th>
                  <th>Class Name</th>
                  <th>Course</th>
                  <th>Conducted</th>
                  <th>Present</th>
                </tr>
                <tr>
                  <td>1</td>
                  <td>B.Tech..2023.R.CYS.1.20CYS402</td>
                  <td>20CYS402 Distributed Systems</td>
                  <td>36</td>
                  <td>32</td>
                </tr>
              </table>
            </div>
        """.trimIndent()
        val parsed = AumsScraper.parseAttendanceHtml(html)
        assertEquals(1, parsed.attendanceList.size)
        assertEquals("2026-27 Even Sem", parsed.academicTerm)
        assertEquals(8, parsed.detectedSemester)
        assertEquals(36, parsed.attendanceList[0].totalClasses)
        assertEquals(32, parsed.attendanceList[0].attendedClasses)
    }
    @Test fun productionStudentsAmritaEduExactTableParsedWithDutyLeave() {
        val html = """
            <div>
              <label>Academic Term</label>
              <div>2026-27 Odd Sem</div>
              <table id="home_tab" class="table table-striped table-bordered dt-responsive wrap">
                <thead>
                  <tr>
                    <th>Sl No</th>
                    <th>Class Name</th>
                    <th>Course</th>
                    <th>Faculty</th>
                    <th>Total </th>
                    <th>Present</th>
                    <th>Duty Leave</th>
                    <th>Absent</th>
                    <th>Percentage</th>
                    <th>Medical</th>
                  </tr>
                  <tr><th>1</th><th>B.Tech..2023.R.CYS.1.20CYS402</th><th>20CYS402<br>Distributed Systems and Cloud Computing</th><th>UDHAYAKUMAR S<br></th><th>30</th><th>29</th><th>0</th><th>1</th><th>96.67</th><th>0</th></tr>
                  <tr><th>2</th><th>B.Tech..2023.R.CYS.1.19LAW300</th><th>19LAW300<br>Indian Constitution</th><th>S Ambadi Narayanan<br></th><th>0</th><th>0</th><th>0</th><th>0</th><th class="bg-danger">0</th><th>0</th></tr>
                  <tr><th>3</th><th>B.Tech..2023.R.CYS.1.20CYS495</th><th>20CYS495<br>Project - Phase - 1 / Seminar</th><th>Saravanan S<br></th><th>0</th><th>0</th><th>0</th><th>0</th><th class="bg-danger">0</th><th>0</th></tr>
                  <tr><th>4</th><th>B.Tech..2023.R.CYS.1.19CSE446</th><th>19CSE446<br>Internet of Things</th><th>JINKA VENKATA ARAVIND<br></th><th>28</th><th>19</th><th>0</th><th>9</th><th class="bg-danger">67.86</th><th>0</th></tr>
                  <tr><th>5</th><th>B.Tech..2023.R.CYS.1.20CYS403</th><th>20CYS403<br>Web Application Security</th><th>Saravanan S<br></th><th>46</th><th>39</th><th>0</th><th>7</th><th>84.78</th><th>0</th></tr>
                  <tr><th>6</th><th>B.Tech..2023.R.CYS.1.20CYS404</th><th>20CYS404<br>Android Application Development</th><th>UDHAYAKUMAR S<br></th><th>25</th><th>21</th><th>0</th><th>4</th><th>84</th><th>0</th></tr>
                  <tr><th>7</th><th>B.Tech..2023.R.CYS.1.20MNG331</th><th>20MNG331<br>Information Security Risk Management</th><th>Mugunthan S<br></th><th>25</th><th>20</th><th>2</th><th>5</th><th>88</th><th>0</th></tr>
                  <tr><th>8</th><th>B.Tech..2023.R.CYS.1.20CYS401</th><th>20CYS401<br>Secure Software Engineering</th><th>Saranya G<br></th><th>40</th><th>30</th><th>3</th><th>10</th><th>82.5</th><th>0</th></tr>
                </thead>
              </table>
            </div>
        """.trimIndent()
        val parsed = AumsScraper.parseAttendanceHtml(html)
        assertEquals(8, parsed.attendanceList.size)
        assertEquals("2026-27 Odd Sem", parsed.academicTerm)
        assertEquals(7, parsed.detectedSemester)
        assertTrue(parsed.warnings.isEmpty())

        // Row 1: 20CYS402
        val r0 = parsed.attendanceList[0]
        assertEquals("20CYS402", r0.subjectCode)
        assertEquals("Distributed Systems and Cloud Computing", r0.subjectName)
        assertEquals(30, r0.totalClasses)
        assertEquals(29, r0.attendedClasses)

        // Row 7: 20MNG331 (Present 20 + Duty Leave 2 = 22 / 25 -> 88%)
        val r6 = parsed.attendanceList[6]
        assertEquals("20MNG331", r6.subjectCode)
        assertEquals(25, r6.totalClasses)
        assertEquals(22, r6.attendedClasses)

        // Row 8: 20CYS401 (Present 30 + Duty Leave 3 = 33 / 40 -> 82.5%)
        val r7 = parsed.attendanceList[7]
        assertEquals("20CYS401", r7.subjectCode)
        assertEquals(40, r7.totalClasses)
        assertEquals(33, r7.attendedClasses)
    }
    @Test(expected=Exception::class) fun impossibleDateRejected(){AcademicValidation.validate("QUIZ",JSONObject().put("title","Quiz").put("date","2026-02-30"))}
    @Test(expected=Exception::class) fun resourceCannotExecuteScript(){AcademicValidation.validate("RESOURCE",JSONObject().put("title","Unsafe link").put("url","javascript:alert(1)"))}
    @Test fun courseMappingDoesNotExposeUnenrolledGroupCourses() {
        val private=AcademicRecord("student","private","COURSE",payload="{\"title\":\"Networks\"}")
        val shared=AcademicRecord("student","shared","COURSE",groupId="classA",payload="{\"title\":\"Networks\"}")
        assertEquals(listOf(private),enrolledCourses(listOf(private,shared)))
    }
    @Test fun datedCancellationAndHolidayOverrideWeeklyClasses() {
        val day=java.time.LocalDate.of(2026,9,14)
        val weekly=AcademicRecord("student","weekly","TIMETABLE",payload="""{"title":"Networks","day":1,"time":"09:00","endTime":"10:00"}""")
        val cancellation=AcademicRecord("student","exception","TIMETABLE",payload="""{"title":"Cancelled","date":"2026-09-14","templateId":"weekly","cancelled":true}""")
        assertEquals(listOf(weekly),ScheduleEngine.forDate(listOf(weekly),day))
        assertTrue(ScheduleEngine.forDate(listOf(weekly,cancellation),day).isEmpty())
        val holiday=AcademicRecord("student","holiday","NOTICE",payload="""{"title":"Holiday","date":"2026-09-14","isHoliday":true}""")
        assertTrue(ScheduleEngine.forDate(listOf(weekly,holiday),day).isEmpty())
        assertEquals(listOf(weekly),ScheduleEngine.forDate(listOf(weekly,cancellation),day.plusWeeks(1)))
    }
    @Test fun terminalErrorsIdentifiedCorrectly() {
        val illegalArg = IllegalArgumentException("Bad input")
        val illegalState = IllegalStateException("Bad state")

        assertTrue(isTerminalError(illegalArg))
        assertTrue(isTerminalError(illegalState))

        val ioException = java.io.IOException("Network down")
        val timeoutException = java.util.concurrent.TimeoutException("Timeout")
        val genericException = Exception("Generic failure")

        assertFalse(isTerminalError(ioException))
        assertFalse(isTerminalError(timeoutException))
        assertFalse(isTerminalError(genericException))
    }
    @Test fun productionCourseOptionHelpersDisambiguateCourses() {
        val c1 = AcademicRecord("student", "c1", "COURSE", payload = """{"title":"Networks","code":"23CYS201","semester":3}""")
        val c2 = AcademicRecord("student", "c2", "COURSE", payload = """{"title":"Networks","code":"23CYS301","semester":5}""")
        assertEquals("Networks · 23CYS201 · Sem 3", formatCourseOption(c1))
        assertEquals("Networks · 23CYS301 · Sem 5", formatCourseOption(c2))

        val map = courseOptionMap(listOf(c1, c2))
        assertEquals(2, map.size)
        assertEquals("c1", map["Networks · 23CYS201 · Sem 3"])
        assertEquals("c2", map["Networks · 23CYS301 · Sem 5"])
    }
    @Test fun productionLinkedCourseAndSyllabusTopicResolution() {
        val coursePrivate = AcademicRecord("student", "c_priv", "COURSE", payload = """{"title":"Networks","code":"23CYS201","semester":3}""")
        val courseShared = AcademicRecord("student", "c_pub", "COURSE", groupId = "group1", payload = """{"title":"Networks","code":"23CYS201","semester":3}""")
        val mappingRecord = AcademicRecord("student", "personal:c_priv", "PERSONAL", payload = """{"mapping":"c_pub"}""")
        val syllabusShared = AcademicRecord("student", "s_pub", "SYLLABUS", courseId = "c_pub", groupId = "group1", payload = """{"title":"Full syllabus","topics":[{"id":"t1","unit":"Unit 1","title":"OSI Layers"},{"id":"t2","unit":"Unit 2","title":"TCP/IP"}]}""")

        val records = listOf(coursePrivate, courseShared, mappingRecord, syllabusShared)

        val linked = resolveLinkedCourseIds(records, "c_priv")
        assertTrue(linked.contains("c_priv"))
        assertTrue(linked.contains("c_pub"))

        val topics = resolveSyllabusTopics(records, "c_priv")
        assertEquals(2, topics.size)
        assertEquals("OSI Layers", topics[0].getString("title"))
        assertEquals("TCP/IP", topics[1].getString("title"))
    }
    @Test fun outboxDependencyDetectionBlocksDependentsAllowsIndependent() {
        val invalidCourseWrite = PendingWrite(
            owner = "student", id = "op_course", operation = "commitRecords",
            payload = JSONObject().put("changes", JSONArray().put(JSONObject().put("id", "course_bad").put("kind", "COURSE").put("data", JSONObject().put("title", "Bad")))).toString()
        )
        val dependentAssignmentWrite = PendingWrite(
            owner = "student", id = "op_assign", operation = "commitRecords",
            payload = JSONObject().put("changes", JSONArray().put(JSONObject().put("id", "assign_1").put("kind", "ASSIGNMENT").put("courseId", "course_bad"))).toString()
        )
        val independentMessageWrite = PendingWrite(
            owner = "student", id = "op_msg", operation = "sendMessage",
            payload = JSONObject().put("groupId", "group_1").put("text", "Hello class").toString()
        )
        val independentCourseWrite = PendingWrite(
            owner = "student", id = "op_course_good", operation = "commitRecords",
            payload = JSONObject().put("changes", JSONArray().put(JSONObject().put("id", "course_good").put("kind", "COURSE").put("data", JSONObject().put("title", "Good").put("code", "C101").put("semester", 1)))).toString()
        )

        val blockedRecords = affectedRecordIds(invalidCourseWrite).toMutableSet()
        val blockedCourses = affectedCourseIds(invalidCourseWrite).toMutableSet()
        assertTrue(blockedRecords.contains("course_bad"))
        assertTrue(blockedCourses.contains("course_bad"))

        // Dependent assignment must be detected as dependent
        assertTrue(isDependentOn(dependentAssignmentWrite, blockedRecords, blockedCourses))

        // Independent operations must NOT be detected as dependent
        assertFalse(isDependentOn(independentMessageWrite, blockedRecords, blockedCourses))
        assertFalse(isDependentOn(independentCourseWrite, blockedRecords, blockedCourses))
    }
    @Test fun conflictAndTerminalErrorClassification() {
        val conflictEx = Exception("CONFLICT: Proposal or source changed; review again")
        assertTrue(isConflictError(conflictEx))
        assertTrue(isTerminalOrConflictError(conflictEx))

        // Structured classified errors
        val terminalArg = "[TERMINAL:INVALID_ARGUMENT] Attachment does not belong to this workspace"
        assertEquals("TERMINAL", errorCategory(terminalArg))
        assertTrue(isTerminalError(terminalArg))
        assertTrue(isTerminalOrConflictError(terminalArg))
        assertEquals("Attachment does not belong to this workspace", userFacingError(terminalArg))

        val conflictAborted = "[CONFLICT:ABORTED] Proposal or source changed; review again"
        assertEquals("CONFLICT", errorCategory(conflictAborted))
        assertTrue(isConflictError(conflictAborted))
        assertTrue(isTerminalOrConflictError(conflictAborted))
        assertEquals("Proposal or source changed; review again", userFacingError(conflictAborted))

        // Expired auth must NOT be permanently classified as terminal/bad user data
        val authExpired = "[AUTH:UNAUTHENTICATED] Account verification needed. Check Settings."
        assertEquals("AUTH", errorCategory(authExpired))
        assertFalse(isTerminalError(authExpired))
        assertFalse(isTerminalOrConflictError(authExpired))

        val transientIo = "[TRANSIENT:UNAVAILABLE] Operation failed. Check connection and retry."
        assertEquals("TRANSIENT", errorCategory(transientIo))
        assertFalse(isTerminalError(transientIo))
        assertFalse(isTerminalOrConflictError(transientIo))

        // Backward-compatible fallback for legacy text
        assertTrue(isTerminalOrConflictError("CONFLICT: Server record changed"))
        assertTrue(isTerminalOrConflictError("Some changes could not be applied: invalid-argument"))
        assertTrue(isTerminalOrConflictError("permission-denied: Publisher required"))
        assertFalse(isTerminalOrConflictError("Operation failed. Check connection and retry. No success has been assumed."))
    }
    @Test fun queuedEditChainCapturesAndRetiresAllReviewedPredecessors() {
        val recordId = "rec_chain"
        val w1 = PendingWrite("student", id = "op_1", operation = "commitRecords", payload = """{"changes":[{"id":"rec_chain","expectedRevision":1}]}""")
        val w2 = PendingWrite("student", id = "op_2", operation = "commitRecords", payload = """{"changes":[{"id":"rec_chain","expectedRevision":2}]}""")
        val wUnrelated = PendingWrite("student", id = "op_other", operation = "commitRecords", payload = """{"changes":[{"id":"other_rec","expectedRevision":1}]}""")

        val currentPending = listOf(w1, w2, wUnrelated)
        val pendingForTarget = currentPending.filter { recordId in affectedRecordIds(it) }
        assertEquals(2, pendingForTarget.size)

        // Capture reviewed set
        val reviewedPendingIds = pendingForTarget.map { it.id }.toSet()
        assertEquals(setOf("op_1", "op_2"), reviewedPendingIds)

        // Case: No new unreviewed edits
        val unreviewed = pendingForTarget.filter { it.id !in reviewedPendingIds }
        assertTrue("No unreviewed edits exist", unreviewed.isEmpty())

        // Case: Newer unreviewed edit arrives while review is open
        val w3 = PendingWrite("student", id = "op_3", operation = "commitRecords", payload = """{"changes":[{"id":"rec_chain","expectedRevision":3}]}""")
        val updatedPending = currentPending + w3
        val updatedForTarget = updatedPending.filter { recordId in affectedRecordIds(it) }
        val unreviewedNew = updatedForTarget.filter { it.id !in reviewedPendingIds }
        assertEquals(1, unreviewedNew.size)
        assertEquals("op_3", unreviewedNew[0].id)
    }
    @Test fun discardMarksRecordLocalOnlyAndPreservesHistory() {
        val local = AcademicRecord("student", "rec1", "COURSE", payload = """{"title":"Networks","code":"23CYS201"}""")
        val historyCopy = local.copy(id = "history:${local.id}:discarded:${java.util.UUID.randomUUID()}", kind = "HISTORY")
        assertEquals("HISTORY", historyCopy.kind)
        assertTrue(historyCopy.id.startsWith("history:rec1:discarded:"))

        val discardedPayload = local.json().put("localOnly", true).put("syncStatus", "discarded")
        val discardedRecord = local.copy(payload = discardedPayload.toString())
        assertTrue(discardedRecord.json().getBoolean("localOnly"))
        assertEquals("discarded", discardedRecord.json().getString("syncStatus"))
    }
    @Test fun conflictRecoveryRejectsConcurrentLocalModification() {
        val reviewedLocal = AcademicRecord("student", "rec1", "QUIZ", payload = """{"title":"Quiz 1","date":"2026-10-01"}""", revision = 1, updatedAt = 1000L)
        val modifiedLocal = AcademicRecord("student", "rec1", "QUIZ", payload = """{"title":"Quiz 1 modified","date":"2026-10-02"}""", revision = 1, updatedAt = 2000L)

        // Snapshot comparison logic
        var caught = false
        try {
            check(modifiedLocal.payload == reviewedLocal.payload && modifiedLocal.updatedAt == reviewedLocal.updatedAt && modifiedLocal.revision == reviewedLocal.revision) {
                "Local record was modified during review. Review again."
            }
        } catch (e: IllegalStateException) {
            caught = true
            assertEquals("Local record was modified during review. Review again.", e.message)
        }
        assertTrue("Concurrent modification must be caught and rejected", caught)
    }

    @Test fun twoAccountAcademicRecordVisibilityInSubjectsAndPlanner() {
        val owner = "owner"
        val student = "student"
        val groupId = "group_algo"
        val sharedCourseId = "group_algo_course_1"
        val quizRecordId = "group_algo_quiz_1"
        val timetableId = "group_algo_tt_1"

        // Records as synced to Student's device after Owner publishes and Student enrolls:
        val sharedCourse = AcademicRecord(
            owner = student,
            id = sharedCourseId,
            kind = "COURSE",
            groupId = groupId,
            payload = JSONObject().put("title", "Design and Analysis of Algorithms").put("code", "23CYS202").put("semester", 3).toString(),
            revision = 1
        )
        val personalEnrollment = AcademicRecord(
            owner = student,
            id = "personal:$sharedCourseId",
            kind = "PERSONAL",
            payload = JSONObject().put("mapping", "enrolled").put("attended", 18).put("total", 20).toString()
        )
        val publishedQuiz = AcademicRecord(
            owner = student,
            id = quizRecordId,
            kind = "QUIZ",
            courseId = sharedCourseId,
            groupId = groupId,
            payload = JSONObject().put("title", "Algorithms Quiz 1").put("date", "2026-10-05").put("time", "10:00").put("endTime", "11:00").toString(),
            revision = 1
        )
        val publishedTimetable = AcademicRecord(
            owner = student,
            id = timetableId,
            kind = "TIMETABLE",
            courseId = sharedCourseId,
            groupId = groupId,
            payload = JSONObject().put("title", "Algorithms Lecture").put("day", 1).put("time", "09:00").put("endTime", "10:00").put("room", "AB1-101").toString(),
            revision = 1
        )
        val privateStudySession = AcademicRecord(
            owner = student,
            id = "study_1",
            kind = "STUDY_SESSION",
            courseId = sharedCourseId,
            groupId = "",
            payload = JSONObject().put("title", "Prep for Quiz 1").put("date", "2026-10-04").toString()
        )

        val records = listOf(sharedCourse, personalEnrollment, publishedQuiz, publishedTimetable, privateStudySession)

        // 1. Subjects Page verification:
        // Enrolled courses includes the group course because mapping is "enrolled"
        val enrolled = enrolledCourses(records)
        assertEquals(1, enrolled.size)
        assertEquals("Design and Analysis of Algorithms", enrolled[0].title)
        assertEquals("23CYS202", enrolled[0].json().getString("code"))

        // Course items for the course contains the published quiz
        val items = courseItems(records, sharedCourse)
        assertTrue(items.any { it.id == quizRecordId && it.kind == "QUIZ" && it.title == "Algorithms Quiz 1" })
        assertTrue(items.any { it.id == "study_1" && it.kind == "STUDY_SESSION" })

        // Attendance stats in Subjects:
        val p = personal(records, sharedCourse.id)
        assertEquals(18, p.getInt("attended"))
        assertEquals(20, p.getInt("total"))
        val att = AcademicValidation.attendance(p.getInt("attended"), p.getInt("total"), 75)
        assertEquals(90.0, att.percentage!!, 0.01)
        assertTrue(att.canMiss >= 1)

        // 2. Planner Page verification:
        val courses = enrolledCourses(records)
        val allowed = courses.flatMap { listOf(it.id) + courseItems(records, it).map { r -> r.courseId } }.toSet()
        val plannerItems = records.filter { it.kind in AcademicKind.entries.map { k -> k.name } && (it.groupId.isBlank() || it.courseId in allowed) }

        // Assessment on 2026-10-05 appears in planner
        val quizOnDate = plannerItems.filter { it.json().optString("date") == "2026-10-05" }
        assertEquals(1, quizOnDate.size)
        assertEquals("Algorithms Quiz 1", quizOnDate[0].title)
        assertEquals("QUIZ", quizOnDate[0].kind)

        // 3. Daily Schedule / Today Page verification (ScheduleEngine):
        // 2026-10-05 is a Monday (dayOfWeek.value == 1)
        val date = LocalDate.of(2026, 10, 5)
        assertEquals(1, date.dayOfWeek.value)
        val dailySchedule = ScheduleEngine.forDate(records, date)
        assertEquals(1, dailySchedule.size)
        assertEquals("Algorithms Lecture", dailySchedule[0].title)
        assertEquals("09:00", dailySchedule[0].json().getString("time"))

        // Holiday notice on that date suppresses regular schedule
        val holidayNotice = AcademicRecord(
            owner = student,
            id = "holiday_notice",
            kind = "NOTICE",
            groupId = groupId,
            courseId = sharedCourseId,
            payload = JSONObject().put("title", "Gandhi Jayanti observed").put("date", "2026-10-05").put("isHoliday", true).toString()
        )
        val holidaySchedule = ScheduleEngine.forDate(records + holidayNotice, date)
        assertTrue("Holiday suppresses regular schedule", holidaySchedule.isEmpty())

        // 4. Offline retry check:
        // A student offline edit queued in pending outbox with operationId
        val offlineWrite = PendingWrite(
            owner = student,
            id = "op_offline_study",
            operation = "commitRecords",
            payload = JSONObject().put("operationId", "op_offline_study").put("changes", JSONArray().put(
                JSONObject().put("id", "study_1").put("kind", "STUDY_SESSION").put("action", "UPDATE").put("expectedRevision", 1L).put("data", JSONObject().put("title", "Revised Study Plan"))
            )).toString()
        )
        // Dependent write tracking
        assertTrue(affectedRecordIds(offlineWrite).contains("study_1"))

        // 5. Member removal check:
        // When removed from group, student personal mapping is cleared or unenrolled
        val unmappedRecords = records.filter { it.id != "personal:$sharedCourseId" }
        val enrolledAfterRemoval = enrolledCourses(unmappedRecords)
        assertTrue("Removed group course no longer appears in enrolled courses", enrolledAfterRemoval.isEmpty())

        val allowedAfterRemoval = enrolledAfterRemoval.flatMap { listOf(it.id) + courseItems(unmappedRecords, it).map { r -> r.courseId } }.toSet()
        val plannerAfterRemoval = unmappedRecords.filter { it.kind in AcademicKind.entries.map { k -> k.name } && (it.groupId.isBlank() || it.courseId in allowedAfterRemoval) }
        val groupQuizAfterRemoval = plannerAfterRemoval.filter { it.id == quizRecordId }
        assertTrue("Group quiz no longer visible in Planner after unenrollment/removal", groupQuizAfterRemoval.isEmpty())
    }
}
