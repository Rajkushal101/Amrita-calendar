package acn.amrita.chen.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import acn.amrita.chen.planner.ui.AppNavigation
import acn.amrita.chen.planner.ui.MainViewModel
import acn.amrita.chen.planner.ui.AddEventDialog
import acn.amrita.chen.planner.ui.theme.AmritaCalendar2627Theme
import acn.amrita.chen.planner.worker.NotificationBriefingWorker
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupDailyBriefingWorker()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        checkNotificationPermission()
        
        setContent {
            val themeConfig by viewModel.appPreferences.themeConfig.collectAsState()

            AmritaCalendar2627Theme(themeConfig = themeConfig) {
                val showAddDialog by viewModel.showAddEventDialog.collectAsState()
                val selectedDate by viewModel.selectedDate.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)

                    if (showAddDialog) {
                        AddEventDialog(
                            selectedDate = selectedDate,
                            onDismiss = { viewModel.hideAddEventDialog() },
                            onAdd = { title, type, timeString, notes, reminderType ->
                                viewModel.addEvent(title, selectedDate, type, timeString, notes, reminderType)
                                viewModel.hideAddEventDialog()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                setupDailyBriefingWorker()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            setupDailyBriefingWorker()
        }
    }

    private fun setupDailyBriefingWorker() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationBriefingWorker>(24, TimeUnit.HOURS)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBriefing",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
