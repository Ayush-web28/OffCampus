package com.offcampus.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.offcampus.app.ui.auth.AuthViewModel
import com.offcampus.app.ui.navigation.OffCampusNavHost
import com.offcampus.app.ui.theme.OffCampusTheme

class MainActivity : ComponentActivity() {
    // `by viewModels()` and the no-key `viewModel()` call inside OffCampusNavHost both resolve
    // against this same Activity's ViewModelStore, so this is the exact same AuthViewModel
    // instance the nav host and AuthScreen observe — that's what lets a magic-link tap handled
    // here actually show up as a state change over there.
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OffCampusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OffCampusNavHost()
                }
            }
        }
        handleIntent(intent)
    }

    // launchMode="singleTop" in the manifest routes a re-tap of the magic link here instead of
    // spinning up a second MainActivity, since by the time the user checks their email this
    // Activity is usually still alive in the background.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        authViewModel.handleEmailLinkIntent(intent.data?.toString())
    }
}
