package acn.amrita.chen.planner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects")
    suspend fun getAllSubjectsSync(): List<Subject>

    @Query("SELECT * FROM subjects WHERE code = :code LIMIT 1")
    suspend fun getSubjectByCode(code: String): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long
    
    @Query("UPDATE subjects SET totalClasses = :total, attendedClasses = :attended, attendancePercentage = :percentage WHERE code = :code")
    suspend fun updateAttendanceByCode(code: String, total: Int, attended: Int, percentage: Float)
}
