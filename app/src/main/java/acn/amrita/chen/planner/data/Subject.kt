package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val code: String,
    val faculty: String,
    val credits: Int = 3,
    val totalClasses: Int = 0,
    val attendedClasses: Int = 0,
    val attendancePercentage: Float = 0f,
    val firestoreId: String = "",
    val semester: Int = 0
)
