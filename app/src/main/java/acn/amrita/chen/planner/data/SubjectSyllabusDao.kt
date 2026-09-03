package acn.amrita.chen.planner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectSyllabusDao {

    // --- Subject Unit ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: SubjectUnit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<SubjectUnit>): List<Long>

    @Query("SELECT * FROM subject_units WHERE subjectId = :subjectId ORDER BY unitNumber ASC")
    fun getUnitsForSubject(subjectId: Int): Flow<List<SubjectUnit>>

    @Query("SELECT * FROM subject_units WHERE subjectId = :subjectId ORDER BY unitNumber ASC")
    suspend fun getUnitsForSubjectSync(subjectId: Int): List<SubjectUnit>

    @Delete
    suspend fun deleteUnit(unit: SubjectUnit)

    @Query("DELETE FROM subject_units WHERE subjectId = :subjectId")
    suspend fun deleteUnitsForSubject(subjectId: Int)

    // --- Subject Topic ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: SubjectTopic): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<SubjectTopic>)

    @Query("SELECT * FROM subject_topics WHERE unitId = :unitId")
    fun getTopicsForUnit(unitId: Int): Flow<List<SubjectTopic>>

    @Query("SELECT * FROM subject_topics WHERE unitId IN (:unitIds)")
    suspend fun getTopicsForUnitsSync(unitIds: List<Int>): List<SubjectTopic>

    @Delete
    suspend fun deleteTopic(topic: SubjectTopic)

    // --- Subject Project ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: SubjectProject): Long

    @Query("SELECT * FROM subject_projects WHERE subjectId = :subjectId")
    fun getProjectForSubject(subjectId: Int): Flow<SubjectProject?>

    @Query("SELECT * FROM subject_projects WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getProjectForSubjectSync(subjectId: Int): SubjectProject?

    @Delete
    suspend fun deleteProject(project: SubjectProject)
}
