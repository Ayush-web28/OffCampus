package com.offcampus.app.ui.auth

import android.app.Application
import android.content.SharedPreferences
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.ActionCodeSettings
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
enum class AuthMethod { PASSWORD, MAGIC_LINK }

sealed interface AuthUiState {
    data object SignedOut : AuthUiState
    /** [justSignedUp] is true only right after a fresh signup, so the nav host can route
     * through the avatar picker once before landing on the profile screen. */
    data class SignedIn(val uid: String, val justSignedUp: Boolean = false) : AuthUiState
    /** Firebase Auth has a session (magic link sign-in creates the auth user automatically,
     * unlike password sign-up), but there's no Rider document yet — password sign-up always
     * collects a name up front, but a magic link is just an email, so a first-time magic-link
     * user needs one extra step before they're really "signed in" from the app's point of view. */
    data class NeedsProfile(val uid: String, val email: String) : AuthUiState
}

data class AuthFormState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val method: AuthMethod = AuthMethod.PASSWORD,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val magicLinkSent: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

// Firebase Dynamic Links (the service that used to turn this URL into something that could
// hand off straight to a mobile app) shut down in 2025, so this now has to be a real page we
// host ourselves — see public/index.html and the App Links intent-filter in AndroidManifest.xml.
private const val MAGIC_LINK_URL = "https://offcampus-5f370.web.app/finishSignIn"
private const val PREFS_NAME = "auth_prefs"
private const val PREF_PENDING_EMAIL = "pending_magic_link_email"

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = Firebase.auth
    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

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
    fun onMethodChange(method: AuthMethod) =
        _formState.update { it.copy(method = method, magicLinkSent = false, errorMessage = null) }
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

    fun sendMagicLink() {
        val form = _formState.value
        // Same well-formed-email check as the password path — no real college-domain
        // restriction configured yet.
        if (!Patterns.EMAIL_ADDRESS.matcher(form.email).matches()) {
            _formState.update { it.copy(errorMessage = "Enter a valid college email.") }
            return
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val actionCodeSettings = ActionCodeSettings.newBuilder()
                    .setUrl(MAGIC_LINK_URL)
                    .setHandleCodeInApp(true)
                    .build()
                auth.sendSignInLinkToEmail(form.email, actionCodeSettings).await()
                // The link itself carries no email — Firebase's own completion call needs it
                // back, so it's stashed here and read again in handleEmailLinkIntent() once the
                // user taps the link. This only works if the link is opened on this same device,
                // in this same app install — a deliberate limitation, see CLAUDE.md.
                prefs.edit().putString(PREF_PENDING_EMAIL, form.email).apply()
                _formState.update { it.copy(isSubmitting = false, magicLinkSent = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isSubmitting = false, errorMessage = friendlyAuthError(e)) }
            }
        }
    }

    /** Called from MainActivity with whatever URI launched or re-launched it — most links Android
     * hands the app have nothing to do with auth, so this quietly no-ops unless it's actually the
     * magic-link sign-in URL Firebase generated in [sendMagicLink]. */
    fun handleEmailLinkIntent(link: String?) {
        if (link == null || !auth.isSignInWithEmailLink(link)) return

        val email = prefs.getString(PREF_PENDING_EMAIL, null)
        if (email == null) {
            _formState.update {
                it.copy(errorMessage = "Open the sign-in link on the same device and app you requested it from.")
            }
            return
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailLink(email, link).await()
                val uid = result.user!!.uid
                prefs.edit().remove(PREF_PENDING_EMAIL).apply()

                // Unlike password sign-up, Firebase creates the auth user the moment the link is
                // tapped — there's no separate step where we'd have collected a name, so a Rider
                // document might not exist yet even though Firebase Auth now has a session.
                val riderSnapshot = FirebaseRefs.riders.document(uid).get().await()
                _uiState.value = if (riderSnapshot.exists()) {
                    AuthUiState.SignedIn(uid)
                } else {
                    AuthUiState.NeedsProfile(uid, email)
                }
                _formState.value = AuthFormState()
            } catch (e: Exception) {
                _formState.update { it.copy(isSubmitting = false, errorMessage = friendlyAuthError(e)) }
            }
        }
    }

    /** Closes the one gap a magic-link sign-in leaves open: Firebase Auth already has a session
     * (see [handleEmailLinkIntent]), but no name was ever collected for the Rider document. */
    fun completeProfile(name: String) {
        val state = _uiState.value as? AuthUiState.NeedsProfile ?: return
        if (name.isBlank()) {
            _formState.update { it.copy(errorMessage = "Enter your name.") }
            return
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val rider = Rider(name = name, email = state.email)
                FirebaseRefs.riders.document(state.uid).set(rider).await()
                _uiState.value = AuthUiState.SignedIn(state.uid, justSignedUp = true)
                _formState.value = AuthFormState()
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
