package com.verbigem.app.ui.screens.ocr

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Crop-selection overlay rendered over a picture.
 *
 * A single full-size [Canvas] with ONE [pointerInput] layer (constant `Unit` key, so the
 * gesture handler is never re-created mid-drag). Corner circles are hit-tested by distance
 * in full-image pixel space — dragging a corner resizes the frame (the OPPOSITE corner stays
 * fixed). Touching OUTSIDE any handle does NOT consume the event, so the parent `verticalScroll`
 * still scrolls the page (critical for photos taller than the screen).
 *
 * `rect` is in image-relative coordinates 0f..1f and lives in the ViewModel, so it survives
 * recomposition and is independent of preview scaling.
 *
 * @param rect      current crop rectangle (0f..1f), fully inside the image
 * @param onRect    called on every move with the (clamped) new rectangle
 * @param lineColor colour of the crop border / corner handles
 */
@Composable
fun CropOverlay(
    rect: RectF,
    onRect: (RectF) -> Unit,
    lineColor: Color = Color(0xFF4CAF50),
    handleRadius: Dp = 9.dp,
    hitRadius: Dp = 34.dp
) {
    val rectState = rememberUpdatedState(rect)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val w = size.width
                val h = size.height
                val hit = hitRadius.toPx()

                // Which corner (0=TL,1=TR,2=BL,3=BR) is within hitRadius of (px,py)? -1 = none.
                fun cornerAt(px: Float, py: Float): Int {
                    val r = rectState.value
                    val pts = arrayOf(
                        Offset(r.left * w, r.top * h),
                        Offset(r.right * w, r.top * h),
                        Offset(r.left * w, r.bottom * h),
                        Offset(r.right * w, r.bottom * h)
                    )
                    for (i in pts.indices) {
                        val dx = px - pts[i].x
                        val dy = py - pts[i].y
                        if (dx * dx + dy * dy <= hit * hit) return i
                    }
                    return -1
                }

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val corner = cornerAt(firstDown.position.x, firstDown.position.y)
                    if (corner < 0) {
                        // Not on a handle → do NOT consume, let the parent column scroll.
                        return@awaitEachGesture
                    }
                    // A handle was grabbed: claim the gesture and resize from it.
                    firstDown.consume()
                    val r0 = rectState.value
                    // The OPPOSITE (fixed) corner stays put for the whole drag.
                    val fixedLeft = when (corner) { 0, 2 -> r0.right; else -> r0.left }
                    val fixedTop = when (corner) { 0, 1 -> r0.bottom; else -> r0.top }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        val nx = (change.position.x / w).coerceIn(0f, 1f)
                        val ny = (change.position.y / h).coerceIn(0f, 1f)
                        onRect(clampRect(RectF(fixedLeft, fixedTop, nx, ny)))
                        change.consume()
                        if (event.changes.all { !it.pressed }) break
                    }
                }
            }
    ) {
        val left = rect.left * size.width
        val top = rect.top * size.height
        val right = rect.right * size.width
        val bottom = rect.bottom * size.height

        // Dim everything outside the selected region.
        val path = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRect(Rect(left, top, right, bottom))
        }
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.45f))
        }

        // Crop border.
        drawRect(
            color = lineColor,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2.dp.toPx())
        )

        // Corner handles.
        val hr = handleRadius.toPx()
        listOf(
            Offset(left, top), Offset(right, top),
            Offset(left, bottom), Offset(right, bottom)
        ).forEach { c -> drawCircle(color = lineColor, radius = hr, center = c) }
    }
}

/** Keeps the rect inside 0f..1f and enforces a minimum size so it can't collapse. */
private fun clampRect(r: RectF): RectF {
    val left = min(r.left, r.right).coerceIn(0f, 1f)
    val right = max(r.left, r.right).coerceIn(0f, 1f)
    val top = min(r.top, r.bottom).coerceIn(0f, 1f)
    val bottom = max(r.top, r.bottom).coerceIn(0f, 1f)
    val minSize = 0.05f
    val cw = (right - left).coerceAtLeast(minSize)
    val ch = (bottom - top).coerceAtLeast(minSize)
    val w = cw.coerceAtMost(1f - left)
    val h = ch.coerceAtMost(1f - top)
    return RectF(left, top, left + w, top + h)
}
