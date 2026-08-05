package com.hanifedma.streak.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Google sign-in through Credential Manager — the current API; the old
 * GoogleSignInClient/startActivityForResult flow is deprecated.
 *
 * Two passes on purpose: first ask only for accounts already used with this
 * app (no chooser if there's exactly one, which makes returning instant), then
 * fall back to showing every Google account on the device.
 */
class AuthManager(private val auth: FirebaseAuth, private val webClientId: String) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun addAuthListener(onChange: (FirebaseUser?) -> Unit): FirebaseAuth.AuthStateListener {
        val l = FirebaseAuth.AuthStateListener { onChange(it.currentUser) }
        auth.addAuthStateListener(l)
        return l
    }

    fun removeAuthListener(l: FirebaseAuth.AuthStateListener) = auth.removeAuthStateListener(l)

    /**
     * @return null on success, otherwise a Strings key describing what went wrong.
     */
    suspend fun signIn(context: Context): String? {
        if (webClientId.isBlank()) return "err.auth.generic"
        val manager = CredentialManager.create(context)

        val token = try {
            requestToken(manager, context, filterByAuthorized = true)
                ?: requestToken(manager, context, filterByAuthorized = false)
        } catch (e: GetCredentialCancellationException) {
            return "err.auth.cancelled"
        } catch (e: NoCredentialException) {
            return "err.auth.noAccount"
        } catch (e: IOException) {
            return "err.auth.network"
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager failed", e)
            return "err.auth.generic"
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            return "err.auth.generic"
        } ?: return "err.auth.noAccount"

        return try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-in failed", e)
            // Firebase reports a dropped connection as a generic exception, so
            // sniff the message rather than showing "try again" to someone who
            // simply has no signal.
            if (e.message?.contains("network", ignoreCase = true) == true) "err.auth.network"
            else "err.auth.generic"
        }
    }

    private suspend fun requestToken(
        manager: CredentialManager,
        context: Context,
        filterByAuthorized: Boolean,
    ): String? {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorized)
            // Skip the "one tap" auto-select so switching accounts stays possible.
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val result = manager.getCredential(context, request)
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(cred.data).idToken
            } else {
                null
            }
        } catch (e: NoCredentialException) {
            // Expected on the first pass when no account has been used here
            // before — let the caller try again unfiltered.
            if (filterByAuthorized) null else throw e
        }
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        try {
            // Also clear the credential state, so the next sign-in shows the
            // account chooser instead of silently reusing the same account.
            CredentialManager.create(context).clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest(
                    androidx.credentials.ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't clear credential state", e)
        }
    }

    private companion object { const val TAG = "AuthManager" }
}
