package acn.amrita.chen.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class CourseWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun createCourseAndOpenSyllabusFirst() {
        val name="Networks ${System.currentTimeMillis()}"
        compose.onNodeWithText("Subjects").performClick()
        compose.onNodeWithText("Add course manually").performScrollTo().performClick()
        compose.onNodeWithText("Title").performTextInput(name)
        compose.onNodeWithText("Course code").performTextInput("23CYS201")
        compose.onNodeWithText("Save details").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithText("Full Syllabus").assertIsDisplayed()
        compose.onNodeWithText("Your syllabus belongs here").assertIsDisplayed()
        compose.onNodeWithText("Add syllabus").performScrollTo().performClick()
        compose.onNodeWithText("Title").performTextInput("Networks syllabus")
        compose.onNodeWithText("One topic per line: Unit | Topic").performScrollTo().performTextInput("Unit 1 | Routing\nUnit 2 | Transport")
        compose.onNodeWithText("Save details").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Routing").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Routing").performScrollTo().assertIsDisplayed()
    }
}
