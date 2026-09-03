package acn.amrita.chen.planner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY dateMillis ASC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE dateMillis >= :startOfDay AND dateMillis < :endOfDay ORDER BY dateMillis ASC")
    fun getEventsForDay(startOfDay: Long, endOfDay: Long): Flow<List<Event>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<Event>)
    
    @Update
    suspend fun updateEvent(event: Event)
    
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Int)
}
