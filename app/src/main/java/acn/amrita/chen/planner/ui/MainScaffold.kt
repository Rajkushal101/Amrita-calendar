package acn.amrita.chen.planner.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.*
import acn.amrita.chen.planner.ui.components.BottomNavigationBar
import acn.amrita.chen.planner.ui.screens.*
import androidx.lifecycle.viewmodel.compose.viewModel
import acn.amrita.chen.planner.ai.AssistantViewModel
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(viewModel: MainViewModel) {
    val bottomNavController = rememberNavController()
    val assistantVm: AssistantViewModel = viewModel()
    
    var showAssistant by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Observe navigation events from the AI
    LaunchedEffect(Unit) {
        assistantVm.navigationEvents.collect { route ->
            showAssistant = false
            bottomNavController.navigate(route) {
                bottomNavController.graph.startDestinationRoute?.let { r ->
                    popUpTo(r) { saveState = true }
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    
    // Track current route to inform the AI
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route
        if (route != null) {
            assistantVm.currentRoute = route
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = bottomNavController)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAssistant = true },
                containerColor = Color(0xFFC62828), // AcnRed
                contentColor = Color.White
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Open AI Assistant")
            }
        }
    ) { paddingValues ->
        // Handle Assistant Bottom Sheet
        if (showAssistant) {
            ModalBottomSheet(
                onDismissRequest = { showAssistant = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1A1A1A) // AcnSurface
            ) {
                // We'll reuse the AssistantScreen logic but passed the vm
                val messages by assistantVm.messages.collectAsState()
                val isApiKeySet by assistantVm.isApiKeySet.collectAsState()
                val isLoading by assistantVm.isLoading.collectAsState()

                AssistantScreenContent(
                    hasApiKey = isApiKeySet,
                    messages = messages,
                    isThinking = isLoading,
                    onSend = { text, uris -> assistantVm.sendMessage(text, uris) },
                    onSavePdfAnalysis = { assistantVm.simulatePdfImport() },
                    onOpenKeyDialog = { },
                    onApiKeySave = { key -> assistantVm.setApiKey(key) }
                )
            }
        }

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
