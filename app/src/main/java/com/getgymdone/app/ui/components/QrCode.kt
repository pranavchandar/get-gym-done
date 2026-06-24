package com.getgymdone.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
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

/**
 * Decodes a QR code from an image picked by the user. Returns the raw payload, or null if the image
 * couldn't be read or holds no QR. Downscales large photos so decoding stays cheap.
 */
fun decodeQrFromUri(context: Context, uri: Uri): String? {
    val bitmap = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }.getOrNull() ?: return null

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val source = RGBLuminanceSource(width, height, pixels)
    val binary = BinaryBitmap(HybridBinarizer(source))
    val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    return runCatching { QRCodeReader().decode(binary, hints).text }.getOrNull()
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
