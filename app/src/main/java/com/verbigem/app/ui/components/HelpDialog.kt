package com.verbigem.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme

/*
 * Globalna reguła UI (od v41):
 *   kliknięcie ikony  → wykonuje jej zadanie
 *   długie kliknięcie → otwiera okno pomocy (czym jest / co robi / jak używać)
 *
 * Ten plik trzyma całą infrastrukturę: stan okna, okno, modyfikator
 * `helpClickable` oraz gotowe komponenty-ikony, żeby każdy ekran korzystał
 * z dokładnie tego samego wzorca.
 */

/** Stan okna pomocy. Jedna instancja na ekran/grupę elementów. */
class HelpWindowState internal constructor() {
    var title: String? by mutableStateOf(null)
        private set
    var text: String? by mutableStateOf(null)
        private set

    val isVisible: Boolean
        get() = title != null

    fun show(title: String, text: String) {
        this.title = title
        this.text = text
    }

    fun dismiss() {
        title = null
        text = null
    }
}

@Composable
fun rememberHelpWindowState(): HelpWindowState = remember { HelpWindowState() }

/**
 * Okno pomocy. Wywołaj na końcu ekranu (również wewnątrz `BottomNav`),
 * obok `rememberHelpWindowState()`.
 */
@Composable
fun HelpWindow(state: HelpWindowState) {
    // Capture the screen's localized context. Inside a Compose Dialog, LocalContext
    // reverts to the base Activity (device locale), so any stringResource() resolved
    // here would ignore the in-app UI language — the close button would always read
    // "Rozumiem" on a Polish phone regardless of the chosen interface language.
    // Re-provide the screen's context so the whole dialog is translated correctly.
    val localizedContext = LocalContext.current
    val title = state.title ?: return
    val text = state.text.orEmpty()

    Dialog(onDismissRequest = { state.dismiss() }) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = VerbigemTheme.colors.surface,
            border = BorderStroke(1.dp, VerbigemTheme.colors.border)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(VerbigemTheme.colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = VerbigemTheme.colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerbigemTheme.colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = VerbigemTheme.colors.ink
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerbigemTheme.colors.accent)
                        .helpClickable(
                            onClick = { state.dismiss() },
                            onLongClick = { state.dismiss() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.help_close),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
        }
    }
}

/**
 * Tap = akcja, długi tap = okno pomocy.
 *
 * `enabled` wyłącza **tylko akcję**, NIGDY pomoc. Nie wolno przekazać go do
 * `combinedClickable(enabled = ...)`: tam `enabled = false` wyłącza też
 * `onLongClick`, więc na nieaktywnej kontrolce (pusty tekst → „Tłumacz",
 * darmowe konto → silniki, brak zdjęcia → przyciski OCR) pomoc znika bez
 * śladu — dokładnie ten błąd, przez który Milosz zgłaszał v41.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.helpClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
): Modifier = this.combinedClickable(
    enabled = true,
    onClick = { if (enabled) onClick() },
    onLongClick = onLongClick
)

/**
 * Ikona-akcja: kliknięcie wykonuje [onClick], długie kliknięcie otwiera okno pomocy.
 * Zastępuje `IconButton` — nie wolno dokładać drugiego `clickable` do elementu,
 * który już ma własny (IconButton/Button), bo wewnętrzny wygrywa i long-press ginie.
 */
@Composable
fun HelpIconButton(
    onClick: () -> Unit,
    helpState: HelpWindowState,
    helpTitle: String,
    helpText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.helpClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = { helpState.show(helpTitle, helpText) }
        ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Przycisk z ramką: ikona + podpis pod spodem (11.sp — tak jak w menu dolnym).
 * Używane dla mikrofonu / aparatu / aparatu Pro na stronie Tłumacza.
 */
@Composable
fun HelpFramedIconButton(
    icon: ImageVector,
    caption: String,
    helpTitle: String,
    helpText: String,
    helpState: HelpWindowState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = VerbigemTheme.colors.accent,
    iconSize: Dp = 22.dp,
    isActive: Boolean = false
) {
    val frameColor = if (isActive) tint else VerbigemTheme.colors.border
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) tint.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, frameColor, RoundedCornerShape(12.dp))
            .helpClickable(
                onClick = onClick,
                onLongClick = { helpState.show(helpTitle, helpText) }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = caption,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = caption,
            fontSize = 11.sp,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/** Ładny przycisk ze znakiem zapytania — po prawej stronie tytułu ekranu. */
@Composable
fun QuestionMarkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(VerbigemTheme.colors.accent.copy(alpha = 0.12f))
            .border(1.dp, VerbigemTheme.colors.accent.copy(alpha = 0.45f), CircleShape)
            .helpClickable(onClick = onClick, onLongClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = stringResource(R.string.help_open),
            tint = VerbigemTheme.colors.accent,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/**
 * Nagłówek ekranu: logo świetlika (bez tła) + tytuł (+ opcjonalny podtytuł)
 * + przycisk „?" po prawej stronie.
 */
@Composable
fun ScreenHeader(
    title: String,
    /** Treść okna pomocy — już rozwiązana przez `stringResource` u wywołującego. */
    helpState: HelpWindowState,
    helpTitle: String,
    helpText: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    logoSize: Dp = 40.dp,
    /** Opcjonalna dodatkowa ikona akcji przed znakiem zapytania. */
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_firefly),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(logoSize)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.ink
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = VerbigemTheme.colors.muted
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(4.dp))
            trailing()
        }
        Spacer(modifier = Modifier.width(8.dp))
        QuestionMarkButton(onClick = { helpState.show(helpTitle, helpText) })
    }
}
