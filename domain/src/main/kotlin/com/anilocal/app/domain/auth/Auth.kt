package com.anilocal.app.domain.auth

import kotlinx.coroutines.flow.Flow

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

interface AuthRepository {
    /** Emits the signed-in user, or null when signed out / not configured. */
    val currentUser: Flow<AuthUser?>

    /** Exchange a Google ID token for a Firebase session. */
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    fun signOut()
}
