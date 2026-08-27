package com.verbigem.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verbigem.app.data.model.LangCode

@Composable
fun FlagIcon(
    lang: LangCode,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val height = size * 0.72f
    val shape = RoundedCornerShape(3.dp)

    Box(
        modifier = modifier
            .size(width = size, height = height)
            .clip(shape)
            .border(0.5.dp, Color.Black.copy(alpha = 0.15f), shape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height

            when (lang) {
                LangCode.PL -> {
                    // White on top, Red on bottom
                    drawRect(Color.White, size = Size(w, h / 2))
                    drawRect(Color(0xFFDC143C), topLeft = Offset(0f, h / 2), size = Size(w, h / 2))
                }
                LangCode.EN -> {
                    // Navy background + cross
                    drawRect(Color(0xFF012169), size = Size(w, h))
                    // Red and white crosses
                    drawRect(Color.White, topLeft = Offset(w * 0.38f, 0f), size = Size(w * 0.24f, h))
                    drawRect(Color.White, topLeft = Offset(0f, h * 0.35f), size = Size(w, h * 0.3f))
                    drawRect(Color(0xFFC8102E), topLeft = Offset(w * 0.42f, 0f), size = Size(w * 0.16f, h))
                    drawRect(Color(0xFFC8102E), topLeft = Offset(0f, h * 0.4f), size = Size(w, h * 0.2f))
                }
                LangCode.ES -> {
                    // Red - Yellow - Red
                    drawRect(Color(0xFFAA151B), size = Size(w, h * 0.25f))
                    drawRect(Color(0xFFF1BF00), topLeft = Offset(0f, h * 0.25f), size = Size(w, h * 0.5f))
                    drawRect(Color(0xFFAA151B), topLeft = Offset(0f, h * 0.75f), size = Size(w, h * 0.25f))
                }
                LangCode.ZH -> {
                    // Red background with gold accent
                    drawRect(Color(0xFFDE2910), size = Size(w, h))
                    drawCircle(Color(0xFFFFDE00), radius = h * 0.2f, center = Offset(w * 0.25f, h * 0.35f))
                }
                LangCode.DE -> {
                    // Black - Red - Gold
                    drawRect(Color(0xFF000000), size = Size(w, h / 3))
                    drawRect(Color(0xFFDD0000), topLeft = Offset(0f, h / 3), size = Size(w, h / 3))
                    drawRect(Color(0xFFFFCE00), topLeft = Offset(0f, (h / 3) * 2), size = Size(w, h / 3))
                }
                LangCode.TR -> {
                    // Red with white crescent/star representation
                    drawRect(Color(0xFFE30A17), size = Size(w, h))
                    drawCircle(Color.White, radius = h * 0.28f, center = Offset(w * 0.4f, h * 0.5f))
                    drawCircle(Color(0xFFE30A17), radius = h * 0.22f, center = Offset(w * 0.45f, h * 0.5f))
                    drawCircle(Color.White, radius = h * 0.08f, center = Offset(w * 0.65f, h * 0.5f))
                }
            }
        }
    }
}
