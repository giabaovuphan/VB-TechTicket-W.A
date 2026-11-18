package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object PhotoUtils {
    private const val MAX_DIMENSION_PX = 720
    private const val MAX_BASE64_LENGTH = 260_000

    suspend fun compressUriToBase64(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Không thể đọc tệp ảnh đã chọn.")

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

            val maxSide = max(boundsOptions.outWidth, boundsOptions.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxSide / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: error("Định dạng ảnh không hợp lệ.")

            val scaled = scaleBitmap(decoded, MAX_DIMENSION_PX)
            if (scaled != decoded) decoded.recycle()

            var quality = 72
            var encoded: String
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                quality -= 12
            } while (encoded.length > MAX_BASE64_LENGTH && quality >= 30)

            scaled.recycle()
            require(encoded.length <= 295_000) { "Ảnh có dung lượng quá lớn, vui lòng chọn ảnh khác." }
            encoded
        }
    }

    suspend fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val cleanBase64 = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    private fun scaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val longest = max(width, height)
        if (longest <= maxDimension) return source

        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
