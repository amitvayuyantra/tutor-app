package com.tutordiary.app.data.cloud

import android.app.Activity
import com.tutordiary.app.domain.StudentSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class AuthUser(val uid: String, val email: String?, val name: String?)

sealed interface SignInResult {
    data object Ok : SignInResult
    data object Cancelled : SignInResult
    data object NoGoogleAccount : SignInResult
    data object NoInternet : SignInResult
    data object NotConfigured : SignInResult
    data class Failed(val reason: String) : SignInResult
}

/** Google sign-in. Both tutors and students/parents use it. */
interface AuthService {
    /** False when this build has no Firebase project file. The app then works offline only. */
    val isConfigured: Boolean
    val user: StateFlow<AuthUser?>
    suspend fun signInWithGoogle(activity: Activity): SignInResult
    suspend fun signOut()
}

sealed interface ClaimResult {
    data object Ok : ClaimResult
    data object NotFound : ClaimResult
    data object AlreadyUsed : ClaimResult
    data object NoInternet : ClaimResult
    data class Failed(val reason: String) : ClaimResult
}

/** The online part. The tutor writes summaries; a linked student/parent can only read their own. */
interface CloudStore {
    // ----- Tutor side -----
    /**
     * Creates a new invite code and the shared record together. If [oldCode] is given it stops working,
     * but people who already joined stay linked. Throws if it could not be saved.
     */
    suspend fun createInvite(tutorUid: String, studentKey: String, code: String, summary: StudentSummary, oldCode: String?)
    suspend fun pushSummary(summary: StudentSummary)
    /** Removes the invite and unlinks everyone who joined with it. */
    suspend fun removeAccess(studentKey: String, code: String?)
    /** Deletes the shared record completely (used when a student is deleted for good). */
    suspend fun deleteShared(studentKey: String, code: String?)
    /** How many students/parents have joined with this record's code. */
    fun observeLinkedCount(studentKey: String): Flow<Int>

    // ----- Student/parent side -----
    suspend fun claimInvite(code: String, uid: String): ClaimResult
    /** Every record this account has joined, kept up to date live. */
    fun observeMyStudents(uid: String): Flow<List<StudentSummary>>
    /** Stops seeing one record (e.g. a sibling who left tuition). */
    suspend fun leave(studentKey: String, uid: String)
}
