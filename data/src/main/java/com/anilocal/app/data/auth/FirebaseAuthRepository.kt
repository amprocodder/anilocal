package com.anilocal.app.data.auth

import com.anilocal.app.domain.auth.AuthRepository
import com.anilocal.app.domain.auth.AuthUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed Google Sign-In. Works against YOUR Firebase project (drop in
 * app/google-services.json + your SHA-1 + set GOOGLE_WEB_CLIENT_ID). Because the signing
 * cert is registered in your own project, sign-in succeeds — unlike a re-signed mod.
 *
 * Everything is lazy/guarded so the app still builds and runs before Firebase is configured;
 * tapping Sign-In without config simply fails gracefully.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor() : AuthRepository {

    private val auth: FirebaseAuth? by lazy { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val a = auth
        if (a == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        a.addAuthStateListener(listener)
        awaitClose { a.removeAuthStateListener(listener) }
    }.conflate().distinctUntilChanged().shareIn(
        scope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
        replay = 1,
    )

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = try {
        val a = auth ?: error("Firebase not configured — add google-services.json")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = a.signInWithCredential(credential).await()
        Result.success(result.user?.toAuthUser() ?: error("Sign-in returned no user"))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    override fun signOut() {
        runCatching { auth?.signOut() }
    }

    private fun FirebaseUser.toAuthUser() =
        AuthUser(uid = uid, displayName = displayName, email = email, photoUrl = photoUrl?.toString())
}
