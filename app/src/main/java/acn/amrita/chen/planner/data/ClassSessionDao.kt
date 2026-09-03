package acn.amrita.chen.planner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassSessionDao {
    @Query("SELECT * FROM class_sessions")
    fun getAllSessions(): Flow<List<ClassSession>>

    @Query("SELECT * FROM class_sessions WHERE dayOfWeek = :dayOfWeek ORDER BY startTimeMinutes ASC")
    fun getSessionsForDay(dayOfWeek: Int): Flow<List<ClassSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ClassSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ClassSession>)

    @Query("UPDATE class_sessions SET status = :status, cancelledBy = :cancelledBy, cancelledAt = :cancelledAt WHERE id = :sessionId")
    suspend fun cancelSession(sessionId: Int, status: SessionStatus = SessionStatus.CANCELLED, cancelledBy: String, cancelledAt: Long = System.currentTimeMillis())
    @Update
    suspend fun updateSession(session: ClassSession)

    @Query("DELETE FROM class_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Int)
    
    @Query("DELETE FROM class_sessions")
    suspend fun deleteAllSessions()
}
