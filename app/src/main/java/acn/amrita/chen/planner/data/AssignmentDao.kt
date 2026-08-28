package acn.amrita.chen.planner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments ORDER BY dueDateMillis ASC")
    fun getAllAssignments(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments WHERE status != 'SUBMITTED' ORDER BY dueDateMillis ASC")
    fun getPendingAssignments(): Flow<List<Assignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: Assignment)

    @Query("UPDATE assignments SET status = :status WHERE id = :assignmentId")
    suspend fun updateAssignmentStatus(assignmentId: Int, status: AssignmentStatus)
}
