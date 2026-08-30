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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.SyncManager
import com.verbigem.app.ui.components.BottomNav
import com.verbigem.app.ui.screens.auth.AuthViewModel
import com.verbigem.app.ui.screens.auth.LoginScreen
import com.verbigem.app.ui.screens.chat.ChatScreen
import com.verbigem.app.ui.screens.chat.ChatViewModel
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

    val showBottomNav = currentRoute in listOf(
        Screen.Translator.route,
        Screen.Ocr.route,
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
                            restoreState = true
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
                    val chatViewModel: ChatViewModel = viewModel()
                    ChatScreen(
                        viewModel = chatViewModel,
                        onNavigateToContacts = { navController.navigate(Screen.Contacts.route) }
                    )
                }

                composable(Screen.Contacts.route) {
                    val contactsViewModel: ContactsViewModel = viewModel()
                    val chatViewModel: ChatViewModel = viewModel()
                    ContactsScreen(
                        viewModel = contactsViewModel,
                        onOpenChat = { targetUid ->
                            chatViewModel.selectContact(targetUid)
                            navController.navigate(Screen.Chat.route)
                        }
                    )
                }

                composable(Screen.Ocr.route) {
                    val ocrViewModel: OcrViewModel = viewModel()
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
