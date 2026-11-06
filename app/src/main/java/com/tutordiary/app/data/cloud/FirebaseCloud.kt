package com.tutordiary.app.data.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.tutordiary.app.domain.StudentSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/* Firestore layout:
 *   invites/{CODE}      { tutorUid, studentKey, claimedBy: uid|null }
 *   students/{KEY}      summary fields + { inviteCode, linkedUids: [uid] }
 * Who may do what is enforced on the server by firestore.rules (in the repo root).
 */
private const val INVITES = "invites"
private const val STUDENTS = "students"

fun isFirebaseConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

class FirebaseAuthService(private val context: Context) : AuthService {
    private val auth = FirebaseAuth.getInstance()
    private val _user = MutableStateFlow(auth.currentUser?.toAuthUser())
    override val isConfigured = true
    override val user: StateFlow<AuthUser?> = _user

    init {
        auth.addAuthStateListener { _user.value = it.currentUser?.toAuthUser() }
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(uid, email, displayName)

    /** The web client ID is generated from google-services.json, so it is looked up by name. */
    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    override suspend fun signInWithGoogle(activity: Activity): SignInResult {
        val clientId = webClientId() ?: return SignInResult.NotConfigured
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Failed("unexpected credential")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            SignInResult.Ok
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.NoGoogleAccount
        } catch (e: GetCredentialException) {
            SignInResult.Failed(e.type)
        } catch (e: FirebaseNetworkException) {
            SignInResult.NoInternet
        } catch (e: Exception) {
            SignInResult.Failed(e.javaClass.simpleName)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Not important: only affects which account is suggested next time.
        }
    }
}

class FirestoreCloud : CloudStore {
    private val db = FirebaseFirestore.getInstance()

    override suspend fun createInvite(tutorUid: String, studentKey: String, code: String, summary: StudentSummary, oldCode: String?) {
        val batch = db.batch()
        if (oldCode != null && oldCode != code) batch.delete(db.collection(INVITES).document(oldCode))
        batch.set(
            db.collection(STUDENTS).document(studentKey),
            summary.toMap() + mapOf("inviteCode" to code, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
        batch.set(db.collection(INVITES).document(code), mapOf("tutorUid" to tutorUid, "studentKey" to studentKey, "claimedBy" to null))
        batch.commit().await()
    }

    override suspend fun pushSummary(summary: StudentSummary) {
        // Fire-and-forget on purpose: Firestore keeps the write and sends it when the phone is online.
        db.collection(STUDENTS).document(summary.key)
            .set(summary.toMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    override suspend fun removeAccess(studentKey: String, code: String?) {
        val batch = db.batch()
        batch.set(db.collection(STUDENTS).document(studentKey), mapOf("linkedUids" to emptyList<String>(), "inviteCode" to null), SetOptions.merge())
        if (code != null) batch.delete(db.collection(INVITES).document(code))
        batch.commit()
    }

    override suspend fun deleteShared(studentKey: String, code: String?) {
        val batch = db.batch()
        batch.delete(db.collection(STUDENTS).document(studentKey))
        if (code != null) batch.delete(db.collection(INVITES).document(code))
        batch.commit()
    }

    override fun observeLinkedCount(studentKey: String): Flow<Int> = callbackFlow {
        val reg = db.collection(STUDENTS).document(studentKey).addSnapshotListener { snap, _ ->
            trySend((snap?.get("linkedUids") as? List<*>)?.size ?: 0)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun claimInvite(code: String, uid: String): ClaimResult = try {
        db.runTransaction { tx ->
            val inviteRef = db.collection(INVITES).document(code)
            val invite = tx.get(inviteRef)
            if (!invite.exists()) return@runTransaction ClaimResult.NotFound
            val claimedBy = invite.getString("claimedBy")
            if (claimedBy != null && claimedBy != uid) return@runTransaction ClaimResult.AlreadyUsed
            val studentKey = invite.getString("studentKey") ?: return@runTransaction ClaimResult.NotFound
            if (claimedBy == null) tx.update(inviteRef, "claimedBy", uid)
            tx.update(db.collection(STUDENTS).document(studentKey), "linkedUids", FieldValue.arrayUnion(uid))
            ClaimResult.Ok
        }.await()
    } catch (e: FirebaseFirestoreException) {
        when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> ClaimResult.NoInternet
            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.NOT_FOUND -> ClaimResult.NotFound
            else -> ClaimResult.Failed(e.code.name)
        }
    } catch (e: Exception) {
        ClaimResult.Failed(e.javaClass.simpleName)
    }

    override fun observeMyStudents(uid: String): Flow<List<StudentSummary>> = callbackFlow {
        val reg = db.collection(STUDENTS).whereArrayContains("linkedUids", uid).addSnapshotListener { snap, error ->
            if (error != null) { trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map { StudentSummary.fromMap(it.id, it.data.orEmpty()) }.sortedBy { it.studentName.lowercase() })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun leave(studentKey: String, uid: String) {
        db.collection(STUDENTS).document(studentKey).update("linkedUids", FieldValue.arrayRemove(uid)).await()
    }
}

/** Used when the build has no Firebase project file. */
class OfflineAuthService : AuthService {
    override val isConfigured = false
    override val user: StateFlow<AuthUser?> = MutableStateFlow(null)
    override suspend fun signInWithGoogle(activity: Activity) = SignInResult.NotConfigured
    override suspend fun signOut() = Unit
}

class OfflineCloud : CloudStore {
    private fun off(): Nothing = throw IllegalStateException("Online features are not set up in this build")
    override suspend fun createInvite(tutorUid: String, studentKey: String, code: String, summary: StudentSummary, oldCode: String?) = off()
    override suspend fun pushSummary(summary: StudentSummary) = Unit
    override suspend fun removeAccess(studentKey: String, code: String?) = Unit
    override suspend fun deleteShared(studentKey: String, code: String?) = Unit
    override fun observeLinkedCount(studentKey: String): Flow<Int> = MutableStateFlow(0)
    override suspend fun claimInvite(code: String, uid: String): ClaimResult = ClaimResult.Failed("offline build")
    override fun observeMyStudents(uid: String): Flow<List<StudentSummary>> = MutableStateFlow(emptyList())
    override suspend fun leave(studentKey: String, uid: String) = Unit
}
