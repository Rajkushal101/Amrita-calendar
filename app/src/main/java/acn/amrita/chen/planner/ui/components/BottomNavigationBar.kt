package acn.amrita.chen.planner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.compose.material.icons.filled.AutoAwesome

sealed class BottomNavItem(var title: String, var icon: ImageVector, var route: String) {
    object Home : BottomNavItem("Home", Icons.Filled.Home, "home")
    object Calendar : BottomNavItem("Calendar", Icons.Filled.DateRange, "calendar")
    object Assistant : BottomNavItem("AI", Icons.Filled.AutoAwesome, "assistant")
    object Timetable : BottomNavItem("Timetable", Icons.Filled.List, "timetable")
    object Subjects : BottomNavItem("Subjects", Icons.Filled.Star, "subjects")
    object Announcements : BottomNavItem("Notices", Icons.Filled.Notifications, "announcements")
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Calendar,
        BottomNavItem.Assistant,
        BottomNavItem.Timetable,
        BottomNavItem.Subjects,
        BottomNavItem.Announcements
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(text = item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        navController.graph.startDestinationRoute?.let { route ->
                            popUpTo(route) { saveState = true }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
