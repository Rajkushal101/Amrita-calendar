package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val title: String,
    val description: String = "",
    val dueDateMillis: Long,
    val priority: AssignmentPriority = AssignmentPriority.MEDIUM,
    val status: AssignmentStatus = AssignmentStatus.NOT_STARTED,
    val createdByRole: String = "STUDENT",
    val firestoreId: String = ""
)

enum class AssignmentPriority { LOW, MEDIUM, HIGH, URGENT }
enum class AssignmentStatus { NOT_STARTED, IN_PROGRESS, SUBMITTED, OVERDUE }
