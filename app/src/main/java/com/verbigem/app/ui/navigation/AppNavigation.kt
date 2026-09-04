package com.verbigem.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.PhoneVerificationRepository
import com.verbigem.app.data.repository.SyncManager
import com.verbigem.app.ui.components.BottomNav
import com.verbigem.app.ui.screens.auth.AuthViewModel
import com.verbigem.app.ui.screens.auth.LoginScreen
import com.verbigem.app.ui.screens.chat.ChatListScreen
import com.verbigem.app.ui.screens.chat.ChatListViewModel
import com.verbigem.app.ui.screens.chat.ChatThreadScreen
import com.verbigem.app.ui.screens.chat.ChatThreadViewModel
import com.verbigem.app.ui.screens.chat.ContactCardScreen
import com.verbigem.app.ui.screens.chat.ContactCardViewModel
import com.verbigem.app.ui.screens.contacts.ContactsScreen
import com.verbigem.app.ui.screens.contacts.ContactsViewModel
import com.verbigem.app.ui.screens.contacts.ExternalThreadScreen
import com.verbigem.app.ui.screens.contacts.ExternalThreadViewModel
import com.verbigem.app.ui.screens.conversation.ConversationScreen
import com.verbigem.app.ui.screens.conversation.ConversationViewModel
import com.verbigem.app.ui.screens.ocr.OcrScreen
import com.verbigem.app.ui.screens.ocr.OcrViewModel
import com.verbigem.app.ui.screens.phone.PhoneVerificationScreen
import com.verbigem.app.ui.screens.phone.PhoneVerificationViewModel
import com.verbigem.app.ui.screens.profile.ProfileScreen
import com.verbigem.app.ui.screens.profile.ProfileViewModel
import com.verbigem.app.ui.screens.translator.TranslatorScreen
import com.verbigem.app.ui.screens.translator.TranslatorViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    /**
     * Other person's uid to open straight away — set when the user taps a chat
     * notification. Null (or blank) means "nothing to open".
     */
    openChatUid: String? = null
) {
    val authRepository = AuthRepository()
    val currentUser = authRepository.currentUser
    val startDestination = if (currentUser != null) Screen.Translator.route else Screen.Login.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    // OCR deliberately absent: BottomNav has five full-width items and no OCR slot,
    // so showing the bar on the OCR screen gave a nav row that couldn't highlight or
    // return to the current screen. OCR is reached from the Translator and left via
    // system back — see Screen.Ocr usage below.
    val showBottomNav = currentRoute in listOf(
        Screen.Translator.route,
        Screen.Conversation.route,
        Screen.Chat.route,
        Screen.Contacts.route,
        Screen.Profile.route
    )

    // Shared Pro flag, updated once FirebaseAuth restores the session and the profile is read.
    var isPro by remember { mutableStateOf(false) }

    // Notification tap -> straight into the conversation.
    //
    // FirebaseAuth restores the session asynchronously, so on a cold start from a
    // notification `currentUser` is often still null at first composition. Waiting for
    // it beats navigating to a thread the app would immediately consider signed-out.

    // ── Phone verification gate (task 2.6) ────────────────────────────────────
    //
    // Shown the first time the user reaches Chat or Contacts without a verified
    // number. Nowhere else: the Translator is the app's main job, and nagging on
    // startup is the fastest way to get "Skip" tapped without reading.
    //
    // `initial = true` is deliberate — the listener needs a round trip, and showing
    // the gate for the split second before it answers would flash a screen most users
    // have already completed.
    //
    // `LocalContext.current` is read OUTSIDE `remember`: a `remember` lambda is not a
    // composable scope, so calling it there does not compile.
    val context = LocalContext.current
    val phoneRepository = remember { PhoneVerificationRepository() }
    val uid = authRepository.currentUser?.uid
    val phoneVerified by remember(uid) {
        if (uid == null) flowOf(true) else phoneRepository.watchPhoneVerified(uid)
    }.collectAsState(initial = true)

    val preferences = remember(context) { PreferencesManager(context.applicationContext) }
    // A long, not a boolean: "skip" is permanent today, but a timestamp leaves room
    // for "ask again next month" without a migration.
    val phoneGateSkippedAt by preferences.phoneGateSkippedAtFlow.collectAsState(initial = 0L)

    LaunchedEffect(currentRoute, phoneVerified, phoneGateSkippedAt) {
        if (currentRoute !in listOf(Screen.Chat.route, Screen.Contacts.route)) return@LaunchedEffect
        if (phoneVerified || phoneGateSkippedAt != 0L) return@LaunchedEffect
        navController.navigate(Screen.PhoneVerification.route) { launchSingleTop = true }
    }

    LaunchedEffect(openChatUid) {
        val targetUid = openChatUid?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        var user = authRepository.currentUser
        var waitedMs = 0
        while (user == null && waitedMs < 3000) {
            delay(150)
            waitedMs += 150
            user = authRepository.currentUser
        }
        if (user == null) return@LaunchedEffect
        navController.navigate(Screen.ChatThread.createRoute(targetUid)) {
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav && !WindowInsets.isImeVisible) {
                BottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Translator.route) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Login.route) {
                    val authViewModel: AuthViewModel = viewModel()
                    LoginScreen(
                        viewModel = authViewModel,
                        onLoginSuccess = {
                            navController.navigate(Screen.Translator.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Translator.route) {
                    val translatorViewModel: TranslatorViewModel = viewModel()
                    val context = LocalContext.current
                    // Key the effect on the signed-in uid so it (re)fires once FirebaseAuth has
                    // restored the session — at first composition currentUser is often still null.
                    val uid = authRepository.currentUser?.uid
                    LaunchedEffect(uid) {
                        uid?.let { u ->
                            // One-shot startup sync: pulls remote history/profile/TTS config and
                            // pushes local changes (last-write-wins by updatedAt).
                            SyncManager(context.applicationContext).syncNow(u)
                            authRepository.watchProfile(u).collect { profile ->
                                isPro = profile?.isPro == true
                                translatorViewModel.setPro(isPro)
                            }
                        }
                    }
                    TranslatorScreen(
                        viewModel = translatorViewModel,
                        onNavigateToOcr = { navController.navigate(Screen.Ocr.route) }
                    )
                }

                composable(Screen.Conversation.route) {
                    val conversationViewModel: ConversationViewModel = viewModel()
                    ConversationScreen(viewModel = conversationViewModel)
                }

                composable(Screen.Chat.route) {
                    val chatListViewModel: ChatListViewModel = viewModel()
                    ChatListScreen(
                        viewModel = chatListViewModel,
                        onOpenThread = { uid -> navController.navigate(Screen.ChatThread.createRoute(uid)) },
                        onOpenContacts = { navController.navigate(Screen.Contacts.route) }
                    )
                }

                composable(
                    route = Screen.ChatThread.route,
                    arguments = listOf(navArgument("uid") { type = NavType.StringType })
                ) { backStackEntry ->
                    val otherUid = backStackEntry.arguments?.getString("uid").orEmpty()
                    val chatThreadViewModel: ChatThreadViewModel = viewModel()
                    LaunchedEffect(isPro) { chatThreadViewModel.setPro(isPro) }
                    ChatThreadScreen(
                        viewModel = chatThreadViewModel,
                        otherUid = otherUid,
                        onBack = { navController.popBackStack() },
                        onOpenContactCard = {
                            navController.navigate(Screen.ContactCard.createRoute(otherUid))
                        }
                    )
                }

                composable(
                    route = Screen.ContactCard.route,
                    arguments = listOf(navArgument("uid") { type = NavType.StringType })
                ) { backStackEntry ->
                    val otherUid = backStackEntry.arguments?.getString("uid").orEmpty()
                    val contactCardViewModel: ContactCardViewModel = viewModel()
                    ContactCardScreen(
                        viewModel = contactCardViewModel,
                        otherUid = otherUid,
                        onBack = { navController.popBackStack() },
                        onOpenThread = { uid -> navController.navigate(Screen.ChatThread.createRoute(uid)) }
                    )
                }

                composable(Screen.Contacts.route) {
                    val contactsViewModel: ContactsViewModel = viewModel()
                    ContactsScreen(
                        viewModel = contactsViewModel,
                        onOpenChat = { targetUid ->
                            navController.navigate(Screen.ChatThread.createRoute(targetUid))
                        },
                        onOpenContactCard = { targetUid ->
                            navController.navigate(Screen.ContactCard.createRoute(targetUid))
                        },
                        onOpenExternalThread = { phone ->
                            navController.navigate(Screen.ExternalThread.createRoute(phone))
                        }
                    )
                }

                composable(
                    route = Screen.ExternalThread.route,
                    arguments = listOf(navArgument("phone") { type = NavType.StringType })
                ) { backStackEntry ->
                    val rawPhone = backStackEntry.arguments?.getString("phone").orEmpty()
                    // Trasa koduje numer, my dekodujemy tutaj. Navigation dekoduje
                    // argumenty ścieżki sama, ale `Uri.decode` na już odkodowanym
                    // ciągu nic nie zmienia — ta linia jest bezpieczna w obie strony.
                    val phone = Uri.decode(rawPhone)
                    val externalViewModel: ExternalThreadViewModel = viewModel()
                    ExternalThreadScreen(
                        phone = phone,
                        viewModel = externalViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.PhoneVerification.route) {
                    val phoneViewModel: PhoneVerificationViewModel = viewModel()
                    PhoneVerificationScreen(
                        viewModel = phoneViewModel,
                        // Either way we leave the same way: back to wherever the gate
                        // interrupted. Verified or skipped, the gate has done its job
                        // and must not come back on the next recomposition.
                        onDone = { navController.popBackStack() },
                        onSkip = { navController.popBackStack() }
                    )
                }

                composable(Screen.Ocr.route) {
                    val ocrViewModel: OcrViewModel = viewModel()
                    LaunchedEffect(isPro) {
                        ocrViewModel.setPro(isPro)
                        ocrViewModel.refreshTtsConfig()
                    }
                    OcrScreen(viewModel = ocrViewModel, isPro = isPro)
                }

                composable(Screen.Profile.route) {
                    val profileViewModel: ProfileViewModel = viewModel()
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onOpenPhoneVerification = {
                            navController.navigate(Screen.PhoneVerification.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}
