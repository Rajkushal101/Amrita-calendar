package acn.amrita.chen.planner.data

data class CampusChange(
    val type: ChangeType,
    val description: String,
    val timestamp: Long
)

enum class ChangeType {
    CLASS_CANCELLED, 
    ROOM_CHANGED, 
    NEW_ANNOUNCEMENT,
    ASSIGNMENT_DEADLINE_CHANGED, 
    ACADEMIC_DATE_CHANGED
}
