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
        SubjectUnit::class, SubjectTopic::class, SubjectProject::class,
        acn.amrita.chen.planner.workspace.AcademicRecord::class, acn.amrita.chen.planner.workspace.PendingWrite::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceDao(): acn.amrita.chen.planner.workspace.WorkspaceDao
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
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS workspace_records (owner TEXT NOT NULL, id TEXT NOT NULL, kind TEXT NOT NULL, courseId TEXT NOT NULL, groupId TEXT NOT NULL, payload TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(owner, id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS workspace_outbox (owner TEXT NOT NULL, id TEXT NOT NULL, operation TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, error TEXT NOT NULL, PRIMARY KEY(owner, id))")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "amrita_calendar_database"
                )
                .addMigrations(MIGRATION_7_8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
