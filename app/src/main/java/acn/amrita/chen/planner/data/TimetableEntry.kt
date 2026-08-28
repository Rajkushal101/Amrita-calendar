package acn.amrita.chen.planner.data

data class TimetableEntry(
    val day: Int,
    val startTime: String,
    val endTime: String,
    val subjectCode: String,
    val subjectName: String,
    val room: String
)
