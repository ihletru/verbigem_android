package com.verbigem.app.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * **Prominent disclosure** — wymóg Google Play dla uprawnienia `READ_CONTACTS`.
 *
 * Zasada Play: zanim system zapyta o kontakty (i zanim aplikacja cokolwiek
 * odczyta), użytkownik musi zobaczyć WŁASNY ekran, który mówi:
 *  - jakie dane zbieramy,
 *  - po co,
 *  - że ich nie sprzedajemy,
 *  - i gdzie jest polityka prywatności.
 *
 * Ten ekran jest pokazywany **przed** `RequestPermission`, więc systemowy
 * dialog uprawnień nigdy nie jest pierwszym kontaktem użytkownika z tematem.
 * Treść (stringi × 6) musi zostać zgodna z opublikowaną polityką na
 * `mini.verbigem.com/privacy/` — jeżeli zmieniasz jedno, zmień i drugie.
 */
@Composable
fun ContactsPermissionScreen(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPolicy: () -> Unit
) {
    val c = VerbigemTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(40.dp)
        )

        Text(
            text = stringResource(R.string.contacts_perm_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink
        )

        Text(
            text = stringResource(R.string.contacts_perm_intro),
            fontSize = 15.sp,
            color = c.ink
        )

        // Co dokładnie robimy z kontaktami — trzy konkrety, nie ogólniki.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DisclosureRow(text = stringResource(R.string.contacts_perm_b1))
            DisclosureRow(text = stringResource(R.string.contacts_perm_b2))
            DisclosureRow(text = stringResource(R.string.contacts_perm_b3))
        }

        // Najważniejsza obietnica, wyróżniona ramką.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .border(1.dp, c.accent, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                text = stringResource(R.string.contacts_perm_hash_note),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink
            )
        }

        // Link do polityki + kontakt — oba OTWIERAJĄ SIĘ W PRZEGLĄDARCE,
        // żeby użytkownik widział naszą domenę, a nie WebView bez paska adresu.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPolicy)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.privacy_policy),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.accent,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.privacy_contact_email),
                    fontSize = 14.sp,
                    color = c.muted
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = stringResource(R.string.contacts_perm_cta),
                fontWeight = FontWeight.Bold
            )
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.contacts_perm_later),
                color = c.muted,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DisclosureRow(text: String) {
    val c = VerbigemTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = c.ink,
            modifier = Modifier.weight(1f)
        )
    }
}
