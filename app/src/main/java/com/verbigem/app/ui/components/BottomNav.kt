package com.verbigem.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.ui.navigation.Screen
import com.verbigem.app.ui.theme.VerbigemTheme

data class NavItem(
    val route: String,
    val icon: ImageVector,
    /** String resource id — labels were hardcoded EN/PL before, which broke i18n. */
    val titleResId: Int,
    /** Long-press explanation shown in the help dialog. */
    val helpResId: Int
)

@Composable
fun BottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val help = rememberHelpWindowState()

    val items = listOf(
        NavItem(Screen.Translator.route, Icons.Default.Translate, R.string.nav_translate, R.string.help_nav_translate),
        NavItem(Screen.Conversation.route, Icons.Default.RecordVoiceOver, R.string.nav_conversation, R.string.help_nav_conversation),
        NavItem(Screen.Chat.route, Icons.Default.Chat, R.string.nav_chat, R.string.help_nav_chat),
        NavItem(Screen.Contacts.route, Icons.Default.Group, R.string.nav_contacts, R.string.help_nav_contacts),
        NavItem(Screen.Ocr.route, Icons.Default.PhotoCamera, R.string.nav_ocr, R.string.help_nav_ocr),
        NavItem(Screen.Profile.route, Icons.Default.Person, R.string.nav_profile, R.string.help_nav_profile)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VerbigemTheme.colors.surface)
            .border(width = 1.dp, color = VerbigemTheme.colors.border)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                val tint = if (isSelected) VerbigemTheme.colors.accent else VerbigemTheme.colors.muted
                val title = stringResource(item.titleResId)
                val tabHelp = stringResource(item.helpResId)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        // Tap = navigate, long-press = explain this tab.
                        .helpClickable(
                            onClick = { onNavigate(item.route) },
                            onLongClick = { help.show(title, tabHelp) }
                        )
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = title,
                        tint = tint
                    )
                    Text(
                        text = title,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    HelpWindow(help)
}
