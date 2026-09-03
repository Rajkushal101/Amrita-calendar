package acn.amrita.chen.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.launch

@Database(
    entities = [
        Event::class, Subject::class, Announcement::class, UserProfile::class,
        ClassSession::class, Assignment::class, AttendanceRecord::class, ChatMessageEntity::class,
        SubjectUnit::class, SubjectTopic::class, SubjectProject::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun subjectDao(): SubjectDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun classSessionDao(): ClassSessionDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun attendanceRecordDao(): AttendanceRecordDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun subjectSyllabusDao(): SubjectSyllabusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "amrita_calendar_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // #region agent log
                        acn.amrita.chen.planner.debug.DebugAgentLog.log(
                            "AppDatabase.kt:onCreate",
                            "Room onCreate fired",
                            "A",
                            mapOf("instanceNullAtCallback" to (INSTANCE == null))
                        )
                        // #endregion
                        // Launch a coroutine to populate the database
                        kotlinx.coroutines.GlobalScope.launch {
                            val inst = INSTANCE
                            // #region agent log
                            acn.amrita.chen.planner.debug.DebugAgentLog.log(
                                "AppDatabase.kt:onCreate.seed",
                                "Seed coroutine running",
                                "A",
                                mapOf("instanceNullInCoroutine" to (inst == null))
                            )
                            // #endregion
                            inst?.let { database ->
                                database.eventDao().insertEvents(InitialData.getAcademicEventsAndHolidays())
                                database.classSessionDao().insertSessions(InitialData.getDummySessions())
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
