package com.offcampus.app.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AuthMode { SIGN_IN, SIGN_UP }

sealed interface AuthUiState {
    data object SignedOut : AuthUiState
    /** [justSignedUp] is true only right after a fresh signup, so the nav host can route
     * through the avatar picker once before landing on the profile screen. */
    data class SignedIn(val uid: String, val justSignedUp: Boolean = false) : AuthUiState
}

data class AuthFormState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow<AuthUiState>(
        auth.currentUser?.let { AuthUiState.SignedIn(it.uid) } ?: AuthUiState.SignedOut
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    init {
        // Firebase Auth persists the session on disk itself; this listener is what makes that
        // persistence show up in our own state — on a cold start with a cached session it fires
        // immediately with the cached user, which is what makes login survive an app restart.
        //
        // It also fires again, asynchronously, right after signUp() creates a new user — which
        // races the explicit `justSignedUp = true` we set below once the Rider doc is written.
        // Only touch state here when the signed-in identity actually changes, so a same-uid
        // callback can't clobber a justSignedUp flag we already set for the current session.
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val current = _uiState.value
            _uiState.value = when {
                user == null -> AuthUiState.SignedOut
                current is AuthUiState.SignedIn && current.uid == user.uid -> current
                else -> AuthUiState.SignedIn(user.uid)
            }
        }
    }

    fun onModeChange(mode: AuthMode) = _formState.update { it.copy(mode = mode, errorMessage = null) }
    fun onNameChange(value: String) = _formState.update { it.copy(name = value, errorMessage = null) }
    fun onEmailChange(value: String) = _formState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _formState.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val form = _formState.value

        if (form.mode == AuthMode.SIGN_UP && form.name.isBlank()) {
            _formState.update { it.copy(errorMessage = "Enter your name.") }
            return
        }
        // Real domain restriction (e.g. must end in @yourcollege.edu) would go here once a real
        // college domain is configured — for now this just checks it's a well-formed email.
        if (!Patterns.EMAIL_ADDRESS.matcher(form.email).matches()) {
            _formState.update { it.copy(errorMessage = "Enter a valid college email.") }
            return
        }
        if (form.password.length < 6) {
            _formState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (form.mode == AuthMode.SIGN_UP) {
                    val result = auth.createUserWithEmailAndPassword(form.email, form.password).await()
                    val uid = result.user!!.uid
                    val rider = Rider(name = form.name, email = form.email)
                    FirebaseRefs.riders.document(uid).set(rider).await()
                    _uiState.value = AuthUiState.SignedIn(uid, justSignedUp = true)
                } else {
                    auth.signInWithEmailAndPassword(form.email, form.password).await()
                }
                _formState.value = AuthFormState(mode = form.mode)
            } catch (e: Exception) {
                _formState.update { it.copy(isSubmitting = false, errorMessage = friendlyAuthError(e)) }
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _formState.value = AuthFormState()
    }

    private fun friendlyAuthError(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "That password is too weak — try a longer one."
        is FirebaseAuthUserCollisionException -> "An account with that email already exists."
        is FirebaseAuthInvalidCredentialsException -> "That email or password looks wrong."
        else -> e.localizedMessage ?: "Something went wrong. Try again."
    }
}
