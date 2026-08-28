package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val dateMillis: Long,
    val type: String = "academic", // "holiday", "academic", "exam", "personal", "meeting", "deadline"
    val timeString: String? = null,
    val notes: String? = null,
    val hasReminder: Boolean = false,
    val reminderTimeMillis: Long? = null,
    val reminderType: String = "none"
)
