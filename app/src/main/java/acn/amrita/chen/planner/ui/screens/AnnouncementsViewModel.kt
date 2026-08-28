package acn.amrita.chen.planner.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import acn.amrita.chen.planner.data.AcnRepository
import acn.amrita.chen.planner.data.AppDatabase
import acn.amrita.chen.planner.data.AnnouncementPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import java.util.Date

// ── UI Models ───────────────────────────────────────────
data class AnnouncementUi(
    val id: String,
    val title: String,
    val body: String,
    val author: String,
    val timeAgo: String,
    val priority: AnnPriority = AnnPriority.NORMAL,
    val isPinned: Boolean = false,
    val audience: String = "All Students"
)

enum class AnnPriority { URGENT, IMPORTANT, NORMAL }

val AnnPriority.color: Color get() = when (this) {
    AnnPriority.URGENT    -> Color(0xFFF44336)
    AnnPriority.IMPORTANT -> Color(0xFFFFC107)
    AnnPriority.NORMAL    -> Color(0xFF42A5F5)
}

val AnnPriority.label: String get() = when (this) {
    AnnPriority.URGENT    -> "URGENT"
    AnnPriority.IMPORTANT -> "IMPORTANT"
    AnnPriority.NORMAL    -> "NOTICE"
}

class AnnouncementsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = AcnRepository(db)
    
    private val _announcements = MutableStateFlow<List<AnnouncementUi>>(emptyList())
    val announcements: StateFlow<List<AnnouncementUi>> = _announcements

    init {
        viewModelScope.launch {
            repository.getAllAnnouncements().collectLatest { anns ->
                val now = System.currentTimeMillis()
                _announcements.value = anns.map {
                    val daysAgo = (now - it.postedAtMillis) / (1000 * 60 * 60 * 24)
                    val timeString = if (daysAgo == 0L) "Today" else "d ago"
                    // #region agent log
                    acn.amrita.chen.planner.debug.DebugAgentLog.log(
                        "AnnouncementsViewModel.kt:map",
                        "Announcement timeAgo built",
                        "E",
                        mapOf("daysAgo" to daysAgo, "timeString" to timeString, "title" to it.title)
                    )
                    // #endregion
                    
                    AnnouncementUi(
                        id = it.id.toString(),
                        title = it.title,
                        body = it.body,
                        author = it.authorName,
                        timeAgo = timeString,
                        priority = when (it.priority) {
                            AnnouncementPriority.URGENT -> AnnPriority.URGENT
                            AnnouncementPriority.IMPORTANT -> AnnPriority.IMPORTANT
                            AnnouncementPriority.NORMAL -> AnnPriority.NORMAL
                        },
                        isPinned = it.priority == AnnouncementPriority.URGENT || it.isPinned,
                        audience = it.targetAudience
                    )
                }
            }
        }
    }
}
