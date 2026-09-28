package com.offcampus.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

/**
 * Runs [onUidChange] with whichever rider is signed in right now, and again every time the
 * signed-in rider actually changes — including a direct switch from one account to another
 * without the app restarting in between.
 *
 * This replaces the pattern several ViewModels used to follow — reading
 * `Firebase.auth.currentUser?.uid` once inside their own `init {}` — which only ever sees
 * whoever was signed in the moment the ViewModel was first created. For a ViewModel shared at
 * the `OffCampusNavHost` level (`FriendsViewModel`, `PaymentNotificationViewModel`,
 * `UnreadActivityViewModel`, ...) that's the whole Activity's lifetime: signing out and back in
 * as someone else on the same still-running app left them holding the *previous* account's
 * listeners and data, since nothing ever told them to look again. See CLAUDE.md's Fix 13.
 *
 * [onUidChange] is responsible for tearing down whatever it set up for the previous uid before
 * setting up anything new for the new one — this helper only reports that the uid changed, not
 * what to do about it, since that's different for every caller.
 *
 * Returns the underlying [FirebaseAuth.AuthStateListener] so the caller can remove it in
 * `onCleared()`, the same way every other Firestore `ListenerRegistration` in this app is torn down.
 */
fun observeSignedInUid(onUidChange: (uid: String?) -> Unit): FirebaseAuth.AuthStateListener {
    // Distinct from any real uid (including null), so the very first callback — which fires
    // immediately on registration with whatever the current state already is — always runs
    // onUidChange rather than being skipped as "no change".
    var lastUid: Any? = Unit
    val listener = FirebaseAuth.AuthStateListener { auth ->
        val uid = auth.currentUser?.uid
        if (uid != lastUid) {
            lastUid = uid
            onUidChange(uid)
        }
    }
    Firebase.auth.addAuthStateListener(listener)
    return listener
}
