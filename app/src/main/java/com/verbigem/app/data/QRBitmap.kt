package com.verbigem.app.data

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generuje czytelny kod QR (Bitmap) z dowolnego tekstu — używany w Fazie 4 do
 * pokazania własnego linku profilowego (`https://mini.verbigem.com/u/<uid>`).
 *
 * `runCatching`, bo ZXing rzuca, gdy tekst jest pusty lub za długi na wybrany
 * rozmiar — w UI pokazujemy wtedy komunikat zamiast kraszować ekran.
 *
 * Renderer to ZXing (a nie GMS Code Scanner), bo Code Scanner potrafi TYLKO
 * czytać kody; do wygenerowania własnego potrzebny jest oddzielny moduł.
 */
object QRBitmap {

    fun encode(content: String, sizePx: Int = 512): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(
                    x,
                    y,
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bmp
    }.getOrNull()
}
