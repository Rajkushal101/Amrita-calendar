package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "subject_units",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class SubjectUnit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val unitNumber: Int,
    val title: String,
    val description: String = ""
)

@Entity(
    tableName = "subject_topics",
    foreignKeys = [
        ForeignKey(
            entity = SubjectUnit::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unitId")]
)
data class SubjectTopic(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val unitId: Int,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false
)

@Entity(
    tableName = "subject_projects",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class SubjectProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val title: String,
    val description: String = "",
    val deadlineMillis: Long = 0L,
    val status: String = "NOT_STARTED" // NOT_STARTED, IN_PROGRESS, COMPLETED
)
