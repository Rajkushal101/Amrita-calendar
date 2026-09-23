package acn.amrita.chen.planner

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import acn.amrita.chen.planner.data.AppDatabase
import acn.amrita.chen.planner.workspace.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(800)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/$name.png")
        val stream = java.io.FileInputStream(pfd.fileDescriptor)
        stream.readBytes()
        stream.close()
        pfd.close()
        Thread.sleep(300)
    }

    private fun clickTab(name: String) {
        compose.onNode(hasText(name) and hasClickAction()).performClick()
    }

    private fun setFontScale(scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale")
        val stream = java.io.FileInputStream(pfd.fileDescriptor)
        stream.readBytes()
        stream.close()
        pfd.close()
        Thread.sleep(600)
    }

    @After
    fun tearDown() {
        setFontScale(1.0f)
    }

    private val owner = "legacy"

    private val course = AcademicRecord(
        owner = owner,
        id = "course_cys",
        kind = "COURSE",
        payload = JSONObject()
            .put("title", "Computer Networks")
            .put("code", "23CYS201")
            .put("semester", 3)
            .put("faculty", "Dr. Priya R.")
            .toString(),
        revision = 1
    )

    private val attendance = AcademicRecord(
        owner = owner,
        id = "attendance_cys",
        kind = "ATTENDANCE",
        courseId = "course_cys",
        payload = JSONObject()
            .put("attended", 24)
            .put("total", 30)
            .put("subjectCode", "23CYS201")
            .toString(),
        revision = 1
    )

    private val syllabus = AcademicRecord(
        owner = owner,
        id = "syllabus_cys",
        kind = "SYLLABUS",
        courseId = "course_cys",
        payload = JSONObject()
            .put("title", "Computer Networks Syllabus")
            .put("topics", JSONArray()
                .put(JSONObject().put("id", "t1").put("unit", "Unit 1").put("title", "Physical Layer & Transmission Media"))
                .put(JSONObject().put("id", "t2").put("unit", "Unit 2").put("title", "Data Link Layer & Framing Protocols"))
                .put(JSONObject().put("id", "t3").put("unit", "Unit 3").put("title", "Network Layer, IPv4/IPv6 & Routing Algorithms"))
                .put(JSONObject().put("id", "t4").put("unit", "Unit 4").put("title", "Transport Layer: TCP Congestion Control & UDP"))
            ).toString(),
        revision = 1
    )

    private val quiz = AcademicRecord(
        owner = owner,
        id = "quiz_cys",
        kind = "QUIZ",
        courseId = "course_cys",
        payload = JSONObject()
            .put("title", "Quiz 1: Subnetting & Addressing")
            .put("date", "2026-09-18")
            .put("time", "10:30")
            .put("coverage", JSONArray().put("t1").put("t2"))
            .toString(),
        revision = 1
    )

    private val assign = AcademicRecord(
        owner = owner,
        id = "assign_cys",
        kind = "ASSIGNMENT",
        courseId = "course_cys",
        payload = JSONObject()
            .put("title", "Packet Tracer Lab: Multi-Router Topology")
            .put("date", "2026-09-22")
            .put("time", "23:59")
            .toString(),
        revision = 1
    )

    private val session = AcademicRecord(
        owner = owner,
        id = "session_cys",
        kind = "STUDY_SESSION",
        courseId = "course_cys",
        payload = JSONObject()
            .put("title", "Group revision: Routing Protocols")
            .put("date", "2026-09-15")
            .put("time", "17:00")
            .toString(),
        revision = 1
    )

    private val timetable = AcademicRecord(
        owner = owner,
        id = "tt_cys",
        kind = "TIMETABLE",
        courseId = "course_cys",
        payload = JSONObject()
            .put("title", "Networks Lecture")
            .put("day", 2) // Tuesday
            .put("time", "09:00")
            .put("endTime", "10:00")
            .put("room", "AB-304")
            .toString(),
        revision = 1
    )

    private val proposal = AcademicRecord(
        owner = owner,
        id = "prop_cys",
        kind = "PROPOSAL",
        payload = JSONObject()
            .put("status", "pending")
            .put("answer", "Extract syllabus update and lab assignment from class circular")
            .put("changes", JSONArray()
                .put(JSONObject()
                    .put("id", "prop_assign_1")
                    .put("kind", "ASSIGNMENT")
                    .put("action", "CREATE")
                    .put("data", JSONObject()
                        .put("title", "Wireshark Packet Analysis Lab")
                        .put("date", "2026-09-25")
                        .put("time", "14:00")
                    )
                )
            ).toString(),
        revision = 1
    )

    private val conflictingWrite = PendingWrite(
        owner = owner,
        id = "conflict_write_1",
        operation = "commitRecords",
        payload = JSONObject()
            .put("changes", JSONArray().put(
                JSONObject()
                    .put("id", "course_cys")
                    .put("kind", "COURSE")
                    .put("action", "UPDATE")
                    .put("expectedRevision", 1L)
                    .put("data", JSONObject()
                        .put("title", "Computer Networks (Honors Section)")
                        .put("code", "23CYS201")
                        .put("semester", 3)
                    )
            )).toString(),
        error = "[CONFLICT:ABORTED] Proposal or source changed; review again"
    )

    @Before
    fun seedSampleData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        val dao = db.workspaceDao()

        // Clear existing tables to ensure clean state
        db.clearAllTables()

        // Set default appearance to light
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("dark", false).commit()

        dao.putAll(listOf(course, attendance, syllabus, quiz, assign, session, timetable, proposal))
        dao.enqueue(conflictingWrite)

        // Populate groups and messages in ViewModel repository
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val vm = ViewModelProvider(compose.activity)[WorkspaceViewModel::class.java]
            vm.repo.groups.value = listOf(
                JSONObject()
                    .put("id", "cys_group")
                    .put("name", "23CYS201 Class Discussion")
                    .put("role", "owner")
                    .put("status", "approved")
            )
            vm.repo.messages.value = listOf(
                JSONObject()
                    .put("id", "m1")
                    .put("sender", "prof1")
                    .put("senderName", "Dr. Priya R.")
                    .put("text", "Midterm syllabus has been announced. Units 1 and 2 will be covered.")
                    .put("sequence", 1L),
                JSONObject()
                    .put("id", "m2")
                    .put("sender", "legacy")
                    .put("senderName", "You")
                    .put("text", "Thank you Ma'am! Will Packet Tracer topology be included in Quiz 1 as well?")
                    .put("sequence", 2L),
                JSONObject()
                    .put("id", "m3")
                    .put("sender", "prof1")
                    .put("senderName", "Dr. Priya R.")
                    .put("text", "No, Packet Tracer lab topology will be submitted separately as Lab Assignment 1.")
                    .put("replyTo", "m2")
                    .put("pinned", true)
                    .put("sequence", 3L)
            )
        }
    }

    @Test
    fun captureAllScreensBothThemes() {
        setFontScale(1.0f)

        // ─── 1. LIGHT THEME CAPTURES ───
        // Today (Populated)
        capture("today_light")

        // Subjects (Populated)
        clickTab("Subjects")
        capture("subjects_light")

        // Course Workspace (Populated Syllabus)
        compose.onAllNodes(hasText("Computer Networks") and hasClickAction())[0].performScrollTo().performClick()
        capture("course_workspace_light")
        compose.onNodeWithContentDescription("Back").performClick()

        // Planner
        clickTab("Planner")
        capture("planner_light")

        // Groups
        clickTab("Groups")
        capture("groups_light")

        // Group Chat (Light - Unobstructed Send)
        compose.onNode(hasText("23CYS201 Class Discussion") and hasClickAction()).performClick()
        capture("chat_light")

        // Keyboard-open Chat (Light)
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Ready for the quiz!")
        capture("chat_keyboard_light")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Back").performClick()

        // Inbox
        clickTab("Inbox")
        capture("inbox_light")

        // Assistant Sheet (via top bar labelled button)
        compose.onNodeWithContentDescription("Ask academic assistant").performClick()
        capture("assistant_light")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()

        // Settings (Light)
        compose.onNodeWithContentDescription("Settings").performClick()
        capture("settings_light")

        // ─── 2. SWITCH TO DARK THEME ───
        compose.onNodeWithText("Dark theme").performScrollTo().performClick()
        capture("settings_dark")
        compose.onNodeWithContentDescription("Back").performClick()

        // Today (Dark - Populated)
        clickTab("Today")
        capture("today_dark")

        // Subjects (Dark - Populated)
        clickTab("Subjects")
        capture("subjects_dark")

        // Course Workspace (Dark - Populated Syllabus)
        compose.onAllNodes(hasText("Computer Networks") and hasClickAction())[0].performScrollTo().performClick()
        capture("course_workspace_dark")
        compose.onNodeWithContentDescription("Back").performClick()

        // Planner (Dark)
        clickTab("Planner")
        capture("planner_dark")

        // Groups (Dark)
        clickTab("Groups")
        capture("groups_dark")

        // Group Chat (Dark - Unobstructed Send)
        compose.onNode(hasText("23CYS201 Class Discussion") and hasClickAction()).performClick()
        capture("chat_dark")

        // Keyboard-open Chat (Dark)
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Packet Tracer topology verified.")
        capture("chat_keyboard_dark")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Back").performClick()

        // Inbox (Dark)
        clickTab("Inbox")
        capture("inbox_dark")

        // Assistant Sheet (Dark)
        compose.onNodeWithContentDescription("Ask academic assistant").performClick()
        capture("assistant_dark")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()

        // ─── 3. LARGE TEXT VERIFICATION (font_scale = 1.35) ───
        setFontScale(1.35f)

        // Today (Large Text - Dark)
        clickTab("Today")
        capture("today_large_text_dark")

        // Course Workspace / Syllabus (Large Text - Dark)
        clickTab("Subjects")
        compose.onAllNodes(hasText("Computer Networks") and hasClickAction())[0].performScrollTo().performClick()
        capture("course_workspace_large_text_dark")
        compose.onNodeWithContentDescription("Back").performClick()

        // Chat (Large Text - Dark)
        clickTab("Groups")
        compose.onNode(hasText("23CYS201 Class Discussion") and hasClickAction()).performClick()
        capture("chat_large_text_dark")
        compose.onNodeWithContentDescription("Back").performClick()

        // Switch to Light for Large Text verification
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Dark theme").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        // Today (Large Text - Light)
        clickTab("Today")
        capture("today_large_text_light")

        // Course Workspace / Syllabus (Large Text - Light)
        clickTab("Subjects")
        compose.onAllNodes(hasText("Computer Networks") and hasClickAction())[0].performScrollTo().performClick()
        capture("course_workspace_large_text_light")
        compose.onNodeWithContentDescription("Back").performClick()

        // Chat (Large Text - Light)
        clickTab("Groups")
        compose.onNode(hasText("23CYS201 Class Discussion") and hasClickAction()).performClick()
        capture("chat_large_text_light")
        compose.onNodeWithContentDescription("Back").performClick()

        // Reset font scale
        setFontScale(1.0f)
    }
}
