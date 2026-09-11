package com.verbigem.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * Okna z zachowanym językiem interfejsu.
 *
 * ### Problem, który te dwa komponenty rozwiązują
 *
 * Wnętrze `Dialog { }` w Compose to **osobna kompozycja**, do której `LocalContext`
 * wraca jako bazowa aktywność — czyli kontekst **systemu**, a nie ten podmieniony
 * w `MainActivity.LocalizationWrapper`. Skutek: `stringResource()` wewnątrz okna
 * bierze język telefonu, ignorując język wybrany w aplikacji. Na polskim telefonie
 * z interfejsem ustawionym na angielski każde okno było po polsku.
 *
 * `HelpDialog` (przycisk „Rozumiem") i `ModelDownloadDialog` miały to opakowanie
 * wpisane ręcznie — i tylko one. Pozostałe osiem okien w aplikacji nie miało,
 * więc reguła z README („nie używaj `stringResource` w `Dialog` bez opakowania")
 * była nieegzekwowana.
 *
 * ### Dlaczego to jest bezpieczne
 *
 * Kontekst jest przechwytywany **przed** otwarciem okna i tylko przepisywany dalej.
 * Jeśli dane okno i tak dziedziczyło poprawny kontekst, przepisanie tego samego
 * niczego nie zmienia. Jeśli nie dziedziczyło — naprawia. Nie ma wariantu,
 * w którym to psuje.
 *
 * ### Użycie
 *
 * Nowe okno pisz na `LocalizedDialog` / `LocalizedAlertDialog`, nie na `Dialog` /
 * `AlertDialog`. Jeśli kiedyś zajdzie potrzeba użycia surowego `Dialog`, złap
 * kontekst przed nim:
 *
 * ```kotlin
 * val localizedContext = LocalContext.current
 * Dialog(onDismissRequest = { ... }) {
 *     CompositionLocalProvider(LocalContext provides localizedContext) { ... }
 * }
 * ```
 */
@Composable
fun LocalizedDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val localizedContext = LocalContext.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
            content()
        }
    }
}

/**
 * `AlertDialog` z językiem interfejsu zamiast języka telefonu — patrz [LocalizedDialog].
 *
 * `AlertDialog` przyjmuje treść jako **osobne lambdy** (`title`, `text`,
 * `confirmButton`, `dismissButton`, `icon`) i renderuje je wewnątrz własnego okna,
 * więc każda z nich musi dostać kontekst osobno. Nie da się tego załatwić jednym
 * `CompositionLocalProvider` wokół wywołania `AlertDialog`.
 */
@Composable
fun LocalizedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    // Domyślne wartości biorą się z motywu Verbigem (Calm/Sharp/Playful), a NIE
    // z MaterialTheme — inaczej okno nie pasuje do wybranego layoutu:
    //   * `AlertDialogDefaults.shape` = 28 dp, a karty w apce mają 20 dp;
    //   * `titleContentColor`/`textContentColor` to tokeny M3 (`onSurfaceVariant`),
    //     których VerbigemTheme w ogóle nie ustawia → tekst w kolorach Material,
    //     nie w naszym `ink`/`muted`.
    // Ustawiamy je tutaj, bo to JEDNO miejsce, przez które przechodzą wszystkie
    // okna w aplikacji (Profil, Czat, Kontakty, modele).
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = VerbigemTheme.colors.surface,
    iconContentColor: Color = VerbigemTheme.colors.accent,
    titleContentColor: Color = VerbigemTheme.colors.ink,
    textContentColor: Color = VerbigemTheme.colors.muted,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val localizedContext = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { LocalizedSlot(localizedContext) { confirmButton() } },
        modifier = modifier,
        dismissButton = dismissButton?.let { slot ->
            { LocalizedSlot(localizedContext) { slot() } }
        },
        icon = icon?.let { slot ->
            { LocalizedSlot(localizedContext) { slot() } }
        },
        title = title?.let { slot ->
            { LocalizedSlot(localizedContext) { slot() } }
        },
        text = text?.let { slot ->
            { LocalizedSlot(localizedContext) { slot() } }
        },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** Jedno miejsce, w którym slot okna dostaje kontekst aplikacji. */
@Composable
private fun LocalizedSlot(
    context: android.content.Context,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContext provides context) { content() }
}
