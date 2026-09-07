package com.verbigem.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.data.model.EngineChoice
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * Wybór silnika tłumaczenia.
 *
 * Od v41 opisy silników nie są wypisywane pod ikonami — każda ikona ma krótki
 * podpis (jak w menu dolnym), a długie naciśnięcie otwiera okno z pełnym
 * wyjaśnieniem ([EngineChoice.helpTextResId]).
 */
@Composable
fun EnginePicker(
    selectedEngine: EngineChoice,
    onEngineSelected: (EngineChoice) -> Unit,
    isPro: Boolean,
    helpState: HelpWindowState,
    /**
     * Silniki, które to urządzenie jest w stanie faktycznie uruchomić.
     * Domyślnie wszystkie. `LOCAL_PRO_7B` (2.9 GB wag) wypada z listy na
     * telefonach z małą ilością RAM lub miejsca — patrz
     * `ModelDownloader.blockReason`.
     */
    availableEngines: List<EngineChoice> = EngineChoice.entries,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.engine_picker_title),
            color = VerbigemTheme.colors.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            availableEngines.forEach { engine ->
                val isSelected = selectedEngine == engine
                val isEnabled = !engine.isProOnly || isPro

                val bgColor = if (isSelected) VerbigemTheme.colors.accent else Color.Transparent
                val textColor = if (isSelected) Color.White else if (isEnabled) VerbigemTheme.colors.ink else VerbigemTheme.colors.muted
                val engineHelpTitle = stringResource(engine.helpTitleResId)
                val engineHelpText = stringResource(engine.helpTextResId)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                    // Tap = wybierz silnik (tylko gdy włączony; dla free użytkownika
                    // silniki Pro są nieaktywne na kliknięcie, ale długie kliknięcie
                    // i tak otwiera okno pomocy — patrz reguła UI v41).
                    .helpClickable(
                        onClick = { if (isEnabled) onEngineSelected(engine) },
                        onLongClick = { helpState.show(engineHelpTitle, engineHelpText) }
                    )
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (!isEnabled && engine.isProOnly) "${engine.icon} 🔒" else engine.icon,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = stringResource(engine.captionResId),
                        color = textColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
