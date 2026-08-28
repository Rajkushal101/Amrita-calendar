package acn.amrita.chen.planner.ui

import androidx.compose.runtime.*
import androidx.navigation.compose.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import acn.amrita.chen.planner.ui.components.BottomNavigationBar

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()

    // For simplicity, we check a Flow in the viewModel to see if user has picked a role.
    // If not, start at onboarding, else start at main.
    val hasRole by viewModel.hasRole.collectAsState()
    
    val startDestination = if (hasRole) "main" else "onboarding"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(onRoleSelected = { role -> 
                viewModel.saveRole(role)
                navController.navigate("main") {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        composable("main") {
            MainScaffold(viewModel = viewModel)
        }
    }
}
