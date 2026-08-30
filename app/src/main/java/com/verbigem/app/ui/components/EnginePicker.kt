package com.verbigem.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.verbigem.app.data.model.EngineChoice
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun EnginePicker(
    selectedEngine: EngineChoice,
    onEngineSelected: (EngineChoice) -> Unit,
    isPro: Boolean,
    modifier: Modifier = Modifier
) {
    var tooltipEngine by remember { mutableStateOf<EngineChoice?>(null) }

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
            EngineChoice.entries.forEach { engine ->
                val isSelected = selectedEngine == engine
                val isEnabled = !engine.isProOnly || isPro

                val bgColor = if (isSelected) VerbigemTheme.colors.accent else Color.Transparent
                val textColor = if (isSelected) Color.White else if (isEnabled) VerbigemTheme.colors.ink else VerbigemTheme.colors.muted

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .clickable {
                            tooltipEngine = engine
                            if (isEnabled) {
                                onEngineSelected(engine)
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!isEnabled && engine.isProOnly) "${engine.icon} 🔒" else engine.icon,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Tooltip pod wyborem silnika — pokazuje opis (i info o wersji Pro) po kliknięciu zablokowanej ikony.
        tooltipEngine?.let { engine ->
            val tooltipText = if (engine.isProOnly) {
                "${stringResource(engine.descriptionResId)} — ${stringResource(R.string.pro_only)}"
            } else {
                stringResource(engine.descriptionResId)
            }
            Text(
                text = tooltipText,
                color = VerbigemTheme.colors.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
