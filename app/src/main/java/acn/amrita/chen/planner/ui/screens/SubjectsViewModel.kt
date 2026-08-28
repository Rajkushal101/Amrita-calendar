package acn.amrita.chen.planner.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import acn.amrita.chen.planner.data.AcnRepository
import acn.amrita.chen.planner.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ── UI Models ───────────────────────────────────────────
data class SubjectUi(
    val name: String,
    val code: String,
    val faculty: String = "",
    val attended: Int,
    val total: Int,
    val requiredPct: Float = 75f
) {
    val percentage: Float get() = if (total == 0) 100f else attended.toFloat() / total * 100f
    val status: AttStatus get() = when {
        percentage >= 85f -> AttStatus.SAFE
        percentage >= 75f -> AttStatus.WARN
        else              -> AttStatus.DANGER
    }
    val canMiss: Int get() {
        if (status == AttStatus.DANGER) return 0
        var miss = 0
        while ((attended.toFloat() / (total + miss + 1)) * 100f >= requiredPct) miss++
        return miss
    }
    val classesToRecover: Int get() {
        if (percentage >= requiredPct) return 0
        var extra = 0
        while ((attended + extra).toFloat() / (total + extra) * 100f < requiredPct) extra++
        return extra
    }
}

class SubjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = AcnRepository(db)
    
    private val _subjects = MutableStateFlow<List<SubjectUi>>(emptyList())
    val subjects: StateFlow<List<SubjectUi>> = _subjects

    init {
        viewModelScope.launch {
            repository.getAllSubjects().collectLatest { subs ->
                _subjects.value = subs.map {
                    SubjectUi(
                        name = it.name,
                        code = it.code,
                        faculty = it.faculty,
                        attended = it.attendedClasses,
                        total = it.totalClasses
                    )
                }
            }
        }
    }
}
