package com.verbigem.app.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.data.QRBitmap
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * „Mój kod QR" (Faza 4.1).
 *
 * Pokazuje wygenerowany kod z linkiem do własnego profilu
 * (`https://mini.verbigem.com/u/<uid>`). Inna osoba skanuje go w swojej
 * aplikacji Verbigem i trafia na kartę kontaktu z przyciskiem „Dodaj do
 * znajomych" — bez wpisywania nicku czy szukania w książce.
 */
@Composable
fun MyQrScreen(
    viewModel: MyQrViewModel,
    onBack: () -> Unit
) {
    val nickname by viewModel.nickname.collectAsState()
    val context = LocalContext.current
    val qr = remember(viewModel.url) { QRBitmap.encode(viewModel.url, 512) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ------------------------------------------------------------- header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = VerbigemTheme.colors.ink
                )
            }
            Text(
                text = stringResource(R.string.qr_my_code),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --------------------------------------------------------- identity
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🙂", fontSize = 30.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = nickname?.takeIf { it.isNotBlank() } ?: stringResource(R.string.qr_my_code),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = VerbigemTheme.colors.ink
        )
        Text(
            text = stringResource(R.string.qr_my_code_hint),
            fontSize = 13.sp,
            color = VerbigemTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ------------------------------------------------------------- QR
        if (qr != null) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.qr_my_code),
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Text(
                text = stringResource(R.string.qr_generate_failed),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ----------------------------------------------------- copy link
        Button(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Verbigem profile", viewModel.url))
                Toast.makeText(context, context.getString(R.string.copied_clipboard), Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
            modifier = Modifier.fillMaxWidth(0.7f).height(44.dp)
        ) {
            Text(stringResource(R.string.qr_copy_link), color = Color.White, fontSize = 14.sp)
        }
    }
}
