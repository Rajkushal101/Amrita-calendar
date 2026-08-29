package acn.amrita.chen.planner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "USER", "AI", "TOOL_CALL", "EVENT_CARD", "TASK_CARD"
    val content: String, // text for user/ai, toolName for toolCall, title for cards
    val extra1: String = "", // attachmentCount for user, description for toolCall, date/due for cards
    val extra2: String = "", // type/priority for cards
    val timestampMillis: Long = System.currentTimeMillis()
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestampMillis ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}
