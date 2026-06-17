package com.getgymdone.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Renders [content] as a QR code. Kept plain black-on-white (not themed) because contrast is what
 * makes a code reliably scannable. The bitmap is cached per content/size via [remember].
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, sizePx: Int = 640) {
    val image = remember(content, sizePx) { encodeQr(content, sizePx) }
    Image(
        bitmap = image,
        contentDescription = "Friend QR code",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private fun encodeQr(content: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i ->
        if (matrix.get(i % size, i / size)) AndroidColor.BLACK else AndroidColor.WHITE
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, size, 0, 0, size, size) }
        .asImageBitmap()
}
