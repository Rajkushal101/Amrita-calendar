package acn.amrita.chen.planner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import acn.amrita.chen.planner.ui.theme.AcnPlannerTheme
import acn.amrita.chen.planner.workspace.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogScreenshotCaptureTest {

    @get:Rule
    val compose = createComposeRule()

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

    private val serverConflictData = JSONObject()
        .put("revision", 2L)
        .put("data", JSONObject()
            .put("title", "Computer Networks (Honors Section - Faculty Approved)")
            .put("date", "2026-09-20")
            .put("content", "Faculty updated course syllabus and room assignment AB-304.")
        )

    @Test
    fun captureConflictDialogLight() {
        compose.setContent {
            AcnPlannerTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    ConflictResolutionDialog(
                        write = conflictingWrite,
                        server = serverConflictData,
                        reviewedPendingIds = setOf("conflict_write_1"),
                        records = listOf(course),
                        onDismiss = {},
                        onResolve = {}
                    )
                }
            }
        }
        capture("conflict_review_light")
    }

    @Test
    fun captureConflictDialogDark() {
        compose.setContent {
            AcnPlannerTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ConflictResolutionDialog(
                        write = conflictingWrite,
                        server = serverConflictData,
                        reviewedPendingIds = setOf("conflict_write_1"),
                        records = listOf(course),
                        onDismiss = {},
                        onResolve = {}
                    )
                }
            }
        }
        capture("conflict_review_dark")
    }
}
