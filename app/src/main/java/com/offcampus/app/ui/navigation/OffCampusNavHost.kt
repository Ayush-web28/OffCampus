package com.offcampus.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offcampus.app.ui.auth.AuthScreen
import com.offcampus.app.ui.auth.AuthUiState
import com.offcampus.app.ui.auth.AuthViewModel
import com.offcampus.app.ui.chat.FriendChatScreen
import com.offcampus.app.ui.chat.LobbyChatScreen
import com.offcampus.app.ui.friends.FriendsScreen
import com.offcampus.app.ui.friends.FriendsViewModel
import com.offcampus.app.ui.lobby.LobbyBrowseScreen
import com.offcampus.app.ui.lobby.LobbyDetailScreen
import com.offcampus.app.ui.lobby.PostTripScreen
import com.offcampus.app.ui.payment.PaymentNotificationViewModel
import com.offcampus.app.ui.payment.PaymentSplitScreen
import com.offcampus.app.ui.payment.PostFareScreen
import com.offcampus.app.ui.payment.TripHistoryScreen
import com.offcampus.app.ui.profile.AvatarPickerScreen
import com.offcampus.app.ui.profile.ProfileScreen
import com.offcampus.app.ui.rating.RateRiderScreen
import com.offcampus.app.ui.report.ReportScreen

/**
 * Single top-level nav host, gated entirely by [AuthViewModel.uiState]. Rather than each screen
 * deciding on its own where to go next, this is the one place that maps auth state to a route —
 * so "signed out" always means the auth screen and "signed in" always means the Lobbies home (or,
 * right after a fresh signup, the avatar picker first), no matter how that state was reached.
 */
@Composable
fun OffCampusNavHost() {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    // Shared with FriendsScreen below so the bottom-nav badge and the screen itself read the
    // same live incoming-requests list rather than each running their own Firestore listener.
    val friendsViewModel: FriendsViewModel = viewModel()
    val incomingRequests by friendsViewModel.incomingRequests.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Global, not screen-scoped like the friend-lobby Snackbar: "you owe money" is relevant no
    // matter which tab is open, unlike "a friend posted a lobby" which only matters while browsing.
    val paymentNotificationViewModel: PaymentNotificationViewModel = viewModel()
    val owedNotification by paymentNotificationViewModel.owedNotification.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(owedNotification) {
        owedNotification?.let {
            snackbarHostState.showSnackbar(it)
            paymentNotificationViewModel.dismiss()
        }
    }

    // startDestination is a fixed literal, not derived from authState: NavHost rebuilds (and
    // resets the back stack of) its graph via remember(startDestination) whenever that value
    // changes, so deriving it from mutable state raced this LaunchedEffect and clobbered its
    // navigation — e.g. a signup's "go to avatar picker first" got reset back to profile mid-flight.
    // AUTH is a safe first frame either way: if a session is already persisted, the effect below
    // redirects away from it before the user perceives it.
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthUiState.SignedIn -> {
                navController.navigate(Routes.LOBBIES) { popUpTo(0) }
                // Avatar picker is pushed ON TOP of Lobbies (not instead of it) so its own
                // "done" action can just pop back, the same way "change avatar" from Profile does.
                if (state.justSignedUp) navController.navigate(Routes.AVATAR_PICKER)
            }
            // A magic-link tap that hasn't collected a name yet — stays on AUTH, where
            // AuthScreen itself renders the "what's your name" step for this state.
            is AuthUiState.NeedsProfile -> navController.navigate(Routes.AUTH) { popUpTo(0) }
            AuthUiState.SignedOut -> navController.navigate(Routes.AUTH) { popUpTo(0) }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.LOBBIES || currentRoute == Routes.FRIENDS || currentRoute == Routes.PROFILE

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                OffCampusBottomBar(
                    currentRoute = currentRoute,
                    pendingFriendRequests = incomingRequests.size,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.LOBBIES) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.AUTH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.AUTH) { AuthScreen(authViewModel) }

            composable(Routes.AVATAR_PICKER) {
                AvatarPickerScreen(onDone = { navController.popBackStack() })
            }

            composable(Routes.LOBBIES) {
                LobbyBrowseScreen(
                    onCreateLobby = { navController.navigate(Routes.POST_TRIP) },
                    onOpenLobby = { lobbyId -> navController.navigate(Routes.lobbyDetail(lobbyId)) }
                )
            }

            composable(Routes.FRIENDS) {
                FriendsScreen(
                    viewModel = friendsViewModel,
                    onOpenChat = { friendId -> navController.navigate(Routes.friendChat(friendId)) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSignOut = authViewModel::signOut,
                    onChangeAvatar = { navController.navigate(Routes.AVATAR_PICKER) },
                    onOpenTripHistory = { navController.navigate(Routes.TRIP_HISTORY) }
                )
            }

            composable(Routes.POST_TRIP) {
                PostTripScreen(
                    onPosted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.LOBBY_DETAIL_PATTERN,
                arguments = listOf(navArgument("lobbyId") { type = NavType.StringType })
            ) { entry ->
                val lobbyId = entry.arguments?.getString("lobbyId")
                if (lobbyId != null) {
                    LobbyDetailScreen(
                        lobbyId = lobbyId,
                        onBack = { navController.popBackStack() },
                        onOpenChat = { navController.navigate(Routes.lobbyChat(lobbyId)) },
                        onOpenPostFare = { navController.navigate(Routes.postFare(lobbyId)) },
                        onOpenPaymentSplit = { navController.navigate(Routes.paymentSplit(lobbyId)) }
                    )
                }
            }

            composable(
                route = Routes.POST_FARE_PATTERN,
                arguments = listOf(navArgument("lobbyId") { type = NavType.StringType })
            ) { entry ->
                val lobbyId = entry.arguments?.getString("lobbyId")
                if (lobbyId != null) {
                    PostFareScreen(
                        lobbyId = lobbyId,
                        onPosted = { navController.navigate(Routes.paymentSplit(lobbyId)) { popUpTo(Routes.lobbyDetail(lobbyId)) } },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Routes.PAYMENT_SPLIT_PATTERN,
                arguments = listOf(navArgument("lobbyId") { type = NavType.StringType })
            ) { entry ->
                val lobbyId = entry.arguments?.getString("lobbyId")
                if (lobbyId != null) {
                    PaymentSplitScreen(
                        lobbyId = lobbyId,
                        onBack = { navController.popBackStack() },
                        onReport = { reportedUserId -> navController.navigate(Routes.report(lobbyId, reportedUserId)) },
                        onRate = { ratedUserId -> navController.navigate(Routes.rate(lobbyId, ratedUserId)) }
                    )
                }
            }

            composable(
                route = Routes.REPORT_PATTERN,
                arguments = listOf(
                    navArgument("lobbyId") { type = NavType.StringType },
                    navArgument("reportedUserId") { type = NavType.StringType }
                )
            ) { entry ->
                val lobbyId = entry.arguments?.getString("lobbyId")
                val reportedUserId = entry.arguments?.getString("reportedUserId")
                if (lobbyId != null && reportedUserId != null) {
                    ReportScreen(
                        lobbyId = lobbyId,
                        reportedUserId = reportedUserId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Routes.RATE_PATTERN,
                arguments = listOf(
                    navArgument("lobbyId") { type = NavType.StringType },
                    navArgument("ratedUserId") { type = NavType.StringType }
                )
            ) { entry ->
                val lobbyId = entry.arguments?.getString("lobbyId")
                val ratedUserId = entry.arguments?.getString("ratedUserId")
                if (lobbyId != null && ratedUserId != null) {
                    RateRiderScreen(
                        lobbyId = lobbyId,
                        ratedUserId = ratedUserId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Routes.TRIP_HISTORY) {
                TripHistoryScreen(
                    onOpenSplit = { lobbyId -> navController.navigate(Routes.paymentSplit(lobbyId)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.LOBBY_CHAT_PATTERN,
                arguments = listOf(navArgument("lobbyId") { type = NavType.StringType })
            ) { entry ->
                val lobbyId = entry.arguments?.getString("lobbyId")
                if (lobbyId != null) {
                    LobbyChatScreen(lobbyId = lobbyId, onBack = { navController.popBackStack() })
                }
            }

            composable(
                route = Routes.FRIEND_CHAT_PATTERN,
                arguments = listOf(navArgument("friendId") { type = NavType.StringType })
            ) { entry ->
                val friendId = entry.arguments?.getString("friendId")
                if (friendId != null) {
                    FriendChatScreen(friendId = friendId, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
