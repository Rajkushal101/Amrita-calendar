package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
data class Announcement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String,
    val authorName: String,
    val postedAtMillis: Long,
    val isPinned: Boolean = false,
    val urgencyLevel: String = "normal",
    val targetAudience: String = "ALL",           // ALL | DEPARTMENT | BATCH | SECTION
    val priority: AnnouncementPriority = AnnouncementPriority.NORMAL,
    val expiresAt: Long? = null,
    val authorRole: String = "FACULTY",
    val firestoreId: String = ""
)

enum class AnnouncementPriority { NORMAL, IMPORTANT, URGENT }
