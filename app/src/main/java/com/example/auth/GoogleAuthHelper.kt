package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private suspend fun signInWithGoogleCredential(credential: Credential): Result<Unit> {
    if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return Result.failure(IllegalStateException("Tipo de credencial inesperado"))
    }
    return try {
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        Result.success(Unit)
    } catch (e: GoogleIdTokenParsingException) {
        Log.e(AUTH_TAG, "Invalid Google ID token response", e)
        Result.failure(IllegalStateException("No se pudo validar el identificador de Google.", e))
    }
}

private fun buildGoogleIdRequest(
    clientId: String,
    filterByAuthorizedAccounts: Boolean,
    autoSelectEnabled: Boolean
): GetCredentialRequest {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(autoSelectEnabled)
        .build()
    return GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
}

private fun buildSignInWithGoogleRequest(clientId: String): GetCredentialRequest {
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    return GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
}

private fun mapCredentialError(e: Exception): String = when (e) {
    is NoCredentialException ->
        "No se encontró una cuenta de Google disponible en el dispositivo. Verifica tus cuentas en Ajustes."
    is GetCredentialProviderConfigurationException ->
        "Google Play Services no tiene configurado el proveedor de credenciales en este dispositivo."
    is GetCredentialInterruptedException ->
        "El inicio de sesión fue interrumpido. Inténtalo de nuevo."
    is GetCredentialException ->
        e.localizedMessage ?: "No se pudo completar el inicio de sesión con Google."
    else ->
        e.localizedMessage ?: "No se pudo iniciar sesión con Google."
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
    allowAccountSelectionSheet: Boolean = true
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val activity = context as? Activity
    val requestContext: Context = activity?.let(::MutableContextWrapper) ?: context
    val authorizedRequest = buildGoogleIdRequest(
        clientId = clientId,
        filterByAuthorizedAccounts = true,
        autoSelectEnabled = true
    )

    scope.launch {
        try {
            val cred = credentialManager.getCredential(requestContext, authorizedRequest).credential
            signInWithGoogleCredential(cred)
                .onSuccess { onAuthSuccess() }
                .onFailure { onUnauthenticated() }
        } catch (_: NoCredentialException) {
            if (!allowAccountSelectionSheet || activity == null) {
                onUnauthenticated()
                return@launch
            }
            val oneTapRequest = buildGoogleIdRequest(
                clientId = clientId,
                filterByAuthorizedAccounts = false,
                autoSelectEnabled = true
            )
            try {
                val cred = credentialManager.getCredential(
                    MutableContextWrapper(activity),
                    oneTapRequest
                ).credential
                signInWithGoogleCredential(cred)
                    .onSuccess { onAuthSuccess() }
                    .onFailure { onUnauthenticated() }
            } catch (e: GetCredentialCancellationException) {
                Log.w(AUTH_TAG, "Startup Google ID sheet cancelled or dismissed: ${e.message}", e)
                onUnauthenticated()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onUnauthenticated()
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Auto Google ID sign-in cancelled or dismissed: ${e.message}", e)
            onUnauthenticated()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleIdSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Configuración de Google Sign-In incompleta: falta default_web_client_id")
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("No se pudo obtener la actividad actual para iniciar sesión")
        return
    }
    val requestContext = MutableContextWrapper(activity)
    val googleIdRequest = buildGoogleIdRequest(
        clientId = clientId,
        filterByAuthorizedAccounts = false,
        autoSelectEnabled = true
    )

    scope.launch {
        try {
            val cred = credentialManager.getCredential(requestContext, googleIdRequest).credential
            signInWithGoogleCredential(cred)
                .onSuccess { onAuthSuccess() }
                .onFailure { err -> onAuthError(err.localizedMessage ?: "Tipo de credencial inesperado") }
        } catch (_: NoCredentialException) {
            // Si el selector rápido de ID de Google no tiene credenciales activas, respaldar en otra petición con GetSignInWithGoogleOption.
            val fallbackRequest = buildSignInWithGoogleRequest(clientId)
            try {
                val fallbackCred = credentialManager.getCredential(
                    MutableContextWrapper(activity),
                    fallbackRequest
                ).credential
                signInWithGoogleCredential(fallbackCred)
                    .onSuccess { onAuthSuccess() }
                    .onFailure { err -> onAuthError(err.localizedMessage ?: "Tipo de credencial inesperado") }
            } catch (e: GetCredentialCancellationException) {
                Log.w(AUTH_TAG, "Google Sign-In fallback cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AUTH_TAG, "Google ID fallback sign-in failed", e)
                onAuthError(mapCredentialError(e))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google ID Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google ID Sign-In failed", e)
            onAuthError(mapCredentialError(e))
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Configuración de Google Sign-In incompleta: falta default_web_client_id")
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("No se pudo obtener la actividad actual para iniciar sesión")
        return
    }
    val request = buildSignInWithGoogleRequest(clientId)

    scope.launch {
        try {
            val cred = credentialManager.getCredential(
                MutableContextWrapper(activity),
                request
            ).credential
            signInWithGoogleCredential(cred)
                .onSuccess { onAuthSuccess() }
                .onFailure { err -> onAuthError(err.localizedMessage ?: "Tipo de credencial inesperado") }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(mapCredentialError(e))
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
