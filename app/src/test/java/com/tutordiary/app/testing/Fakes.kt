package com.tutordiary.app.testing

import android.app.Activity
import com.tutordiary.app.data.cloud.AuthService
import com.tutordiary.app.data.cloud.AuthUser
import com.tutordiary.app.data.cloud.ClaimResult
import com.tutordiary.app.data.cloud.CloudStore
import com.tutordiary.app.data.cloud.SignInResult
import com.tutordiary.app.di.CloudModule
import com.tutordiary.app.domain.StudentSummary
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/** Pretend Google sign-in. Each sign-in uses [nextUser]. */
class FakeAuth : AuthService {
    override val isConfigured = true
    private val _user = MutableStateFlow<AuthUser?>(null)
    override val user: StateFlow<AuthUser?> = _user
    var nextUser = AuthUser("tutor-uid", "priya@gmail.com", "Priya Sharma")

    override suspend fun signInWithGoogle(activity: Activity): SignInResult {
        _user.value = nextUser
        return SignInResult.Ok
    }

    override suspend fun signOut() { _user.value = null }
}

/** In-memory stand-in for Firestore that applies the same rules as firestore.rules. */
class FakeCloud : CloudStore {
    data class Invite(val tutorUid: String, val studentKey: String, val claimedBy: String?)
    data class Doc(val summary: StudentSummary, val inviteCode: String?, val linkedUids: Set<String>)

    val invites = HashMap<String, Invite>()
    private val docs = MutableStateFlow<Map<String, Doc>>(emptyMap())
    val docsNow: Map<String, Doc> get() = docs.value
    var online = true
    var pushCount = 0

    private fun update(key: String, f: (Doc?) -> Doc?) {
        val m = docs.value.toMutableMap()
        val d = f(m[key])
        if (d == null) m.remove(key) else m[key] = d
        docs.value = m
    }

    override suspend fun createInvite(tutorUid: String, studentKey: String, code: String, summary: StudentSummary, oldCode: String?) {
        check(online) { "offline" }
        if (oldCode != null) invites.remove(oldCode)
        invites[code] = Invite(tutorUid, studentKey, null)
        update(studentKey) { Doc(summary, code, it?.linkedUids.orEmpty()) }
    }

    override suspend fun pushSummary(summary: StudentSummary) {
        pushCount++
        update(summary.key) { Doc(summary, it?.inviteCode, it?.linkedUids.orEmpty()) }
    }

    override suspend fun removeAccess(studentKey: String, code: String?) {
        code?.let { invites.remove(it) }
        update(studentKey) { it?.copy(inviteCode = null, linkedUids = emptySet()) }
    }

    override suspend fun deleteShared(studentKey: String, code: String?) {
        code?.let { invites.remove(it) }
        update(studentKey) { null }
    }

    override fun observeLinkedCount(studentKey: String): Flow<Int> = docs.map { it[studentKey]?.linkedUids?.size ?: 0 }

    override suspend fun claimInvite(code: String, uid: String): ClaimResult {
        if (!online) return ClaimResult.NoInternet
        val inv = invites[code] ?: return ClaimResult.NotFound
        if (inv.claimedBy != null && inv.claimedBy != uid) return ClaimResult.AlreadyUsed
        val doc = docs.value[inv.studentKey] ?: return ClaimResult.NotFound
        if (doc.inviteCode != code) return ClaimResult.NotFound
        invites[code] = inv.copy(claimedBy = uid)
        update(inv.studentKey) { it?.copy(linkedUids = it.linkedUids + uid) }
        return ClaimResult.Ok
    }

    override fun observeMyStudents(uid: String): Flow<List<StudentSummary>> =
        docs.map { m -> m.values.filter { uid in it.linkedUids }.map { it.summary }.sortedBy { it.studentName.lowercase() } }

    override suspend fun leave(studentKey: String, uid: String) {
        update(studentKey) { it?.copy(linkedUids = it.linkedUids - uid) }
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CloudModule::class])
object FakeCloudModule {
    @Provides @Singleton fun auth(): AuthService = FakeAuth()
    @Provides @Singleton fun cloud(): CloudStore = FakeCloud()
}
