package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val subjectId: Int,
    val dateMillis: Long,
    val isPresent: Boolean,
    val markedByFaculty: String = "",
    val firestoreId: String = ""
)
