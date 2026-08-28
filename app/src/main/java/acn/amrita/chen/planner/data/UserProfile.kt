package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val role: String = "STUDENT",
    val name: String = "",
    val batch: String = "",
    val department: String = "CYS",
    val semester: Int = 3,
    val section: String = "A",
    val rollNumber: String = "",
    val firestoreUid: String = ""
)
