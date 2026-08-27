package com.verbigem.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.ui.navigation.AppNavigation
import com.verbigem.app.ui.theme.VerbigemAppTheme
import com.verbigem.app.ui.theme.VerbigemTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager(this)

        setContent {
            val themeName by preferencesManager.themeFlow.collectAsState(initial = "calm")
            val modeName by preferencesManager.modeFlow.collectAsState(initial = "day")
            val uiLang by preferencesManager.uiLangFlow.collectAsState(initial = "pl")

            LocalizationWrapper(uiLang) {
                VerbigemAppTheme(
                    themeName = themeName,
                    modeName = modeName
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = VerbigemTheme.colors.bg
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun LocalizationWrapper(langCode: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = remember(langCode) { Locale.forLanguageTag(langCode) }
    val configuration = remember(langCode) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config
    }
    val localizedContext = remember(langCode) {
        context.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(LocalContext provides localizedContext) {
        content()
    }
}

