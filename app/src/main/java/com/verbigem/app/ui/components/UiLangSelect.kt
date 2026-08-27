package com.verbigem.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun UiLangSelect(
    currentLangCode: String,
    onLangSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLang = LangCode.fromCode(currentLangCode)
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlagIcon(lang = currentLang, size = 18.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentLang.displayName,
                color = VerbigemTheme.colors.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(VerbigemTheme.colors.surface)
        ) {
            LangCode.entries.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagIcon(lang = lang, size = 18.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lang.displayName,
                                color = if (lang.code == currentLangCode) VerbigemTheme.colors.accent else VerbigemTheme.colors.ink,
                                fontWeight = if (lang.code == currentLangCode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    onClick = {
                        onLangSelected(lang.code)
                        expanded = false
                    }
                )
            }
        }
    }
}
