package com.tutordiary.app.data.sync

import com.tutordiary.app.data.cloud.AuthService
import com.tutordiary.app.data.cloud.CloudStore
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.domain.InviteCodes
import com.tutordiary.app.domain.StudentSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InviteResult {
    data class Ok(val code: String) : InviteResult
    data object NotSignedIn : InviteResult
    data object NoInternet : InviteResult
}

/**
 * Tutor side. Keeps each shared student's online summary up to date with what is on this phone.
 * Writes only when something actually changed, to stay well inside the free plan.
 */
@Singleton
class TutorSync @Inject constructor(
    private val repo: TutorRepository,
    private val auth: AuthService,
    private val cloud: CloudStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val lastSent = HashMap<String, Map<String, Any?>>()

    private fun uid(): String? = auth.user.value?.uid

    @OptIn(FlowPreview::class)
    fun start() {
        if (job?.isActive == true || !auth.isConfigured) return
        job = scope.launch {
            combine(repo.observeFeeData(), repo.observeProfile(), repo.observeAllAttendance(), auth.user) { data, profile, att, user ->
                if (user == null) emptyList() else SummaryBuilder.build(user.uid, profile, data, att, repo.today())
            }.debounce(DEBOUNCE_MS).collect { summaries ->
                for (s in summaries) {
                    val map = s.toMap() - "updatedOn" // the date alone is not worth a write
                    if (lastSent[s.key] != map) {
                        runCatching { cloud.pushSummary(s) }.onSuccess { lastSent[s.key] = map }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastSent.clear()
    }

    private suspend fun summaryFor(studentId: Long, tutorUid: String): StudentSummary? {
        val data = repo.observeFeeData().first()
        val forced = data.copy(students = data.students.map { if (it.id == studentId) it.copy(inviteCode = it.inviteCode ?: "PENDING") else it })
        return SummaryBuilder.build(tutorUid, repo.observeProfile().first(), forced, repo.observeAllAttendance().first(), repo.today())
            .firstOrNull { it.key == StudentSummary.keyFor(tutorUid, studentId) }
    }

    /** Makes a new invite code for a student (replacing any old code). Needs internet. */
    suspend fun invite(studentId: Long): InviteResult {
        val tutorUid = uid() ?: return InviteResult.NotSignedIn
        val student = repo.getStudent(studentId) ?: return InviteResult.NotSignedIn
        val summary = summaryFor(studentId, tutorUid) ?: return InviteResult.NotSignedIn
        val code = InviteCodes.generate()
        return try {
            withTimeout(INVITE_TIMEOUT_MS) {
                cloud.createInvite(tutorUid, summary.key, code, summary, student.inviteCode)
            }
            repo.setInviteCode(studentId, code)
            lastSent[summary.key] = summary.toMap() - "updatedOn"
            InviteResult.Ok(code)
        } catch (e: Exception) {
            InviteResult.NoInternet
        }
    }

    suspend fun removeAccess(studentId: Long) {
        val student = repo.getStudent(studentId) ?: return
        val tutorUid = uid()
        if (tutorUid != null) runCatching { cloud.removeAccess(StudentSummary.keyFor(tutorUid, studentId), student.inviteCode) }
        repo.setInviteCode(studentId, null)
    }

    /** Call before deleting a student for good, so their online record goes too. */
    suspend fun forget(studentId: Long) {
        val student = repo.getStudent(studentId) ?: return
        val tutorUid = uid() ?: return
        if (student.inviteCode != null) runCatching { cloud.deleteShared(StudentSummary.keyFor(tutorUid, studentId), student.inviteCode) }
    }

    fun linkedCount(studentId: Long): Flow<Int> {
        val tutorUid = uid() ?: return flowOf(0)
        return cloud.observeLinkedCount(StudentSummary.keyFor(tutorUid, studentId))
    }

    companion object {
        var DEBOUNCE_MS = 1_500L
        var INVITE_TIMEOUT_MS = 20_000L
    }
}
