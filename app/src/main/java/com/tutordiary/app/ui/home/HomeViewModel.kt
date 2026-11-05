package com.tutordiary.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.data.BackupPrefs
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.data.repo.ledgers
import com.tutordiary.app.domain.FeeStatus
import com.tutordiary.app.domain.LedgerSummary
import com.tutordiary.app.domain.MonthKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class UnpaidRow(val student: StudentEntity, val ledger: LedgerSummary)

data class HomeState(
    val loading: Boolean = true,
    val hasStudents: Boolean = false,
    val expectedThisMonth: Long = 0,
    val collectedThisMonth: Long = 0,
    val totalUnpaid: Long = 0,
    val overdueCount: Int = 0,
    val unpaid: List<UnpaidRow> = emptyList(),
    val profile: TutorProfileEntity? = null,
    val showBackupWarning: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TutorRepository,
    backupPrefs: BackupPrefs,
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        repo.observeFeeData(), repo.observeProfile(), backupPrefs.lastBackupEpochDay,
    ) { data, profile, lastBackup ->
        val today = repo.today()
        val month = MonthKey.of(today)
        val ledgers = data.ledgers(today)
        val expected = data.charges.filter { it.month == month }.sumOf { it.amount } -
            data.waivers.filter { it.month == month }.sumOf { it.amount }
        val monthStart = today.withDayOfMonth(1).toEpochDay()
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()
        val collected = data.payments.filter { it.date in monthStart..monthEnd }.sumOf { it.amount }
        val unpaid = data.students
            .mapNotNull { s -> ledgers[s.id]?.takeIf { it.amountDue > 0 }?.let { UnpaidRow(s, it) } }
            .sortedWith(compareBy<UnpaidRow> { it.ledger.oldestDueDate ?: LocalDate.MAX }.thenBy { it.student.name.lowercase() })
        HomeState(
            loading = false,
            hasStudents = data.students.isNotEmpty(),
            expectedThisMonth = expected,
            collectedThisMonth = collected,
            totalUnpaid = unpaid.sumOf { it.ledger.amountDue },
            overdueCount = unpaid.count { it.ledger.status == FeeStatus.OVERDUE },
            unpaid = unpaid,
            profile = profile,
            showBackupWarning = data.students.isNotEmpty() &&
                (lastBackup == null || today.toEpochDay() - lastBackup > 30),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun markReminderOpened(studentId: Long) {
        viewModelScope.launch { repo.markReminderOpened(studentId) }
    }
}
