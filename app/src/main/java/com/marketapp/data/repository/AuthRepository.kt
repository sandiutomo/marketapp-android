package com.marketapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    val currentUser: Flow<FirebaseUser?>
    /** Synchronous — returns the uid of the currently signed-in user, or null if not signed in. */
    val currentUserId: String?
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun registerWithEmail(email: String, password: String, name: String): Result<FirebaseUser>
    suspend fun signOut()
    suspend fun updateDisplayName(name: String): Result<Unit>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    // Independent scope for background Firestore/FCM operations.
    // These must NEVER block the auth result — FCM token fetches can hang indefinitely
    // on emulators or offline devices, silently preventing auth events from firing.
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user!!
            bgScope.launch { upsertFirestoreUser(user) }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user!!
            bgScope.launch { upsertFirestoreUser(user) }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun registerWithEmail(email: String, password: String, name: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            runCatching { user.updateProfile(profileUpdates).await() }
            bgScope.launch { upsertFirestoreUser(user, name) }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun updateDisplayName(name: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Not signed in"))
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            user.updateProfile(profileUpdates).await()
            bgScope.launch { upsertFirestoreUser(user, name) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun upsertFirestoreUser(user: FirebaseUser, displayName: String? = null) {
        // withTimeoutOrNull caps the FCM fetch — it can hang indefinitely on emulators or
        // when FCM servers are unreachable, which was the root cause of auth events not firing.
        val fcmToken = withTimeoutOrNull(5_000) {
            runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        }
        val data = buildMap<String, Any> {
            put("uid", user.uid)
            put("displayName", displayName ?: user.displayName ?: "")
            put("email", user.email ?: "")
            fcmToken?.let { put("fcmToken", it) }
        }
        runCatching { firestore.collection("users").document(user.uid).set(data, SetOptions.merge()).await() }
    }
}
