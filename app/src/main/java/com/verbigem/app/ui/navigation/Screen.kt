package com.verbigem.app.ui.navigation

sealed class Screen(val route: String) {
    data object Translator : Screen("translator")
    data object Conversation : Screen("conversation")
    data object Chat : Screen("chat")
    /** Thread with one person. `uid` is the OTHER participant's Firebase uid. */
    data object ChatThread : Screen("chat/{uid}") {
        fun createRoute(uid: String) = "chat/$uid"
    }
    data object Contacts : Screen("contacts")
    data object Profile : Screen("profile")
    data object Ocr : Screen("ocr")
    data object Login : Screen("login")
}
