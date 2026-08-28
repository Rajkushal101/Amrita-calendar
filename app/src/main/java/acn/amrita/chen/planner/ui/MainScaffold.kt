package acn.amrita.chen.planner.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import acn.amrita.chen.planner.ui.components.BottomNavigationBar
import acn.amrita.chen.planner.ui.screens.*

@Composable
fun MainScaffold(viewModel: MainViewModel) {
    val bottomNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = bottomNavController)
        }
    ) { paddingValues ->
        NavHost(
            navController = bottomNavController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                // #region agent log
                acn.amrita.chen.planner.debug.DebugAgentLog.log(
                    "MainScaffold.kt:nav",
                    "Home route composed; week/assignments not in bottom nav",
                    "F",
                    mapOf("routes" to "home,assistant,calendar,subjects,announcements,aums_login,assignments")
                )
                // #endregion
                HomeScreen(viewModel = viewModel)
            }
            composable("assistant") {
                AssistantScreen(viewModel = viewModel)
            }
            composable("calendar") {
                // The original CalendarScreen with FAB etc.
                CalendarScreen(
                    viewModel = viewModel,
                    onAddEventClick = { viewModel.showAddEventDialog() }
                )
            }
            composable("subjects") {
                SubjectsScreen(
                    viewModel = viewModel,
                    onNavigateToAums = { bottomNavController.navigate("aums_login") }
                )
            }
            composable("announcements") {
                AnnouncementsScreen(viewModel = viewModel)
            }
            composable("aums_login") {
                AumsLoginScreen(
                    viewModel = viewModel,
                    onHtmlExtracted = { html ->
                        viewModel.syncAumsAttendance(html)
                        bottomNavController.popBackStack()
                    }
                )
            }
            composable("timetable") {
                TimetableScreen(viewModel = viewModel)
            }
            composable("assignments") {
                AssignmentsScreen(viewModel = viewModel)
            }
        }
    }
}
