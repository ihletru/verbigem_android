package com.verbigem.app.ui.navigation

sealed class Screen(val route: String) {
    data object Translator : Screen("translator")
    data object Conversation : Screen("conversation")
    data object Chat : Screen("chat")
    /** Thread with one person. `uid` is the OTHER participant's Firebase uid. */
    data object ChatThread : Screen("chat/{uid}") {
        fun createRoute(uid: String) = "chat/$uid"
    }

    /**
     * Per-contact settings for one person: alias, translation language, pin, mute,
     * block. Reached from the thread header and from Contacts.
     */
    data object ContactCard : Screen("contact/{uid}") {
        fun createRoute(uid: String) = "contact/$uid"
    }
    data object Contacts : Screen("contacts")
    data object Profile : Screen("profile")
    data object Ocr : Screen("ocr")
    data object Login : Screen("login")
}
