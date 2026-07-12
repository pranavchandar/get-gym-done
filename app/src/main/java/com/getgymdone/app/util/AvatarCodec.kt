package com.getgymdone.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/**
 * Encodes a picked image into a tiny base64 JPEG thumbnail and decodes it back. Kept small (≤256px)
 * so the display picture rides inside the user's Firestore stats doc — no Firebase Storage needed.
 */
object AvatarCodec {
    private const val MAX_DIM = 256

    /** Loads [uri], downscales to a square-ish thumbnail, and returns a base64 JPEG (or null). */
    fun fromUri(context: Context, uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = minOf(MAX_DIM.toFloat() / src.width, MAX_DIM.toFloat() / src.height, 1f)
        val scaled = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    /** Decodes a base64 thumbnail to an [ImageBitmap], or null if absent/invalid. */
    fun decode(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
        }.getOrNull()
    }
}
