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
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.SyncManager
import com.verbigem.app.ui.components.BottomNav
import com.verbigem.app.ui.screens.auth.AuthViewModel
import com.verbigem.app.ui.screens.auth.LoginScreen
import com.verbigem.app.ui.screens.chat.ChatListScreen
import com.verbigem.app.ui.screens.chat.ChatListViewModel
import com.verbigem.app.ui.screens.chat.ChatThreadScreen
import com.verbigem.app.ui.screens.chat.ChatThreadViewModel
import com.verbigem.app.ui.screens.contacts.ContactsScreen
import com.verbigem.app.ui.screens.contacts.ContactsViewModel
import com.verbigem.app.ui.screens.conversation.ConversationScreen
import com.verbigem.app.ui.screens.conversation.ConversationViewModel
import com.verbigem.app.ui.screens.ocr.OcrScreen
import com.verbigem.app.ui.screens.ocr.OcrViewModel
import com.verbigem.app.ui.screens.profile.ProfileScreen
import com.verbigem.app.ui.screens.profile.ProfileViewModel
import com.verbigem.app.ui.screens.translator.TranslatorScreen
import com.verbigem.app.ui.screens.translator.TranslatorViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
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
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Contacts.route) {
                    val contactsViewModel: ContactsViewModel = viewModel()
                    ContactsScreen(
                        viewModel = contactsViewModel,
                        onOpenChat = { targetUid ->
                            navController.navigate(Screen.ChatThread.createRoute(targetUid))
                        }
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
                        }
                    )
                }
            }
        }
    }
}
