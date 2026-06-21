package com.anilocal.app.data.auth

import com.anilocal.app.domain.auth.AuthRepository
import com.anilocal.app.domain.auth.AuthUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val a = auth
        if (a == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        a.addAuthStateListener(listener)
        awaitClose { a.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = runCatching {
        val a = auth ?: error("Firebase not configured — add google-services.json")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = a.signInWithCredential(credential).await()
        result.user?.toAuthUser() ?: error("Sign-in returned no user")
    }

    override fun signOut() {
        runCatching { auth?.signOut() }
    }

    private fun FirebaseUser.toAuthUser() =
        AuthUser(uid = uid, displayName = displayName, email = email, photoUrl = photoUrl?.toString())
}
