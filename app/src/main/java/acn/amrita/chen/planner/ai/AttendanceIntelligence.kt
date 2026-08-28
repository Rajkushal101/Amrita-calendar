package acn.amrita.chen.planner.ai

import acn.amrita.chen.planner.data.Subject

enum class AttendanceStatus {
    SAFE,       // >= 85%
    WARNING,    // 75–84%
    DANGER      // < 75%
}

data class AttendanceAnalysis(
    val subject: Subject,
    val currentPercentage: Float,
    val status: AttendanceStatus,
    val classesCanAffordToMiss: Int,
    val classesNeededToRecover: Int,
    val projectedPercentage: Float
)

object AttendanceIntelligence {
    fun analyze(subject: Subject): AttendanceAnalysis {
        val total = subject.totalClasses
        val attended = subject.attendedClasses
        val currentPercentage = if (total > 0) (attended.toFloat() / total) * 100 else 100f
        
        val status = when {
            currentPercentage >= 85f -> AttendanceStatus.SAFE
            currentPercentage >= 75f -> AttendanceStatus.WARNING
            else -> AttendanceStatus.DANGER
        }

        val canMiss = if (currentPercentage > 75f) {
            ((attended - 0.75f * total) / 0.75f).toInt()
        } else 0

        val needed = if (currentPercentage < 75f) {
            val req = (0.75f * total - attended) / 0.25f
            if (req > 0) kotlin.math.ceil(req.toDouble()).toInt() else 0
        } else 0

        return AttendanceAnalysis(
            subject = subject,
            currentPercentage = currentPercentage,
            status = status,
            classesCanAffordToMiss = canMiss,
            classesNeededToRecover = needed,
            projectedPercentage = currentPercentage 
        )
    }

    fun simulateAttendance(
        totalClasses: Int,
        attended: Int,
        futureClasses: Int,
        attendNext: Int
    ): Float {
        val newTotal = totalClasses + futureClasses
        val newAttended = attended + attendNext
        return if (newTotal > 0) (newAttended.toFloat() / newTotal) * 100 else 100f
    }
}
