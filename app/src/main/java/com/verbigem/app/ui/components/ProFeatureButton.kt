package com.verbigem.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme
import kotlinx.coroutines.launch

/**
 * An action button for a Pro-only feature.
 *
 * - When [isPro] is true the button is fully active and [onProClick] fires.
 * - When [isPro] is false the button is shown (greyed out) but tapping it only reveals a tooltip
 *   explaining the feature is Pro-only, so free users discover the capability exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProFeatureButton(
    icon: ImageVector,
    contentDescription: String,
    isPro: Boolean,
    onProClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltipText: String = stringResource(R.string.pro_feature_tooltip)
) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = tooltipState
    ) {
        IconButton(
            onClick = {
                if (isPro) {
                    onProClick()
                } else {
                    scope.launch { tooltipState.show() }
                }
            },
            modifier = modifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isPro) VerbigemTheme.colors.accent else VerbigemTheme.colors.muted
            )
        }
    }
}
