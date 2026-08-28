package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_sessions")
data class ClassSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val facultyId: String,
    val room: String,
    val dayOfWeek: Int,           // 1=Mon ... 7=Sun
    val startTimeMinutes: Int,    // minutes from midnight
    val endTimeMinutes: Int,
    val section: String,
    val semester: Int,
    val batch: String,
    val status: SessionStatus = SessionStatus.SCHEDULED,
    val cancelledBy: String? = null,
    val cancelledAt: Long? = null,
    val overrideRoom: String? = null,   // for room changes
    val firestoreId: String = ""
)

enum class SessionStatus { SCHEDULED, CANCELLED, ROOM_CHANGED, POSTPONED }
