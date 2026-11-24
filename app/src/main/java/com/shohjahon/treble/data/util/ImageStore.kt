package com.shohjahon.treble.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.shohjahon.treble.data.remote.ApiClient
import com.shohjahon.treble.data.remote.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Galereyadan tanlangan rasmlarni kichraytirib, ilovaning ichki xotirasiga saqlaydi. */
object ImageStore {

    /** Telefon ichidagi fayl (server rasmlari "/uploads/..." bilan boshlanadi). */
    fun isLocal(path: String): Boolean = path.startsWith("/") && !path.startsWith("/uploads/")

    /** Coil uchun model: server rasmi, lokal fayl yoki internet havolasi. */
    fun model(path: String): Any = when {
        path.startsWith("/uploads/") -> ApiConfig.BASE_URL + path
        isLocal(path) -> File(path)
        else -> path
    }

    /** Galereyadan tanlangan rasmni kichraytirib serverga yuklaydi. Muvaffaqiyatda ("/uploads/...", null). */
    suspend fun uploadFromUri(context: Context, uri: Uri): Pair<String?, String?> {
        val local = withContext(Dispatchers.IO) { save(context, uri) }
            ?: return Pair(null, "Rasmni o'qib bo'lmadi.")
        try {
            val bytes = withContext(Dispatchers.IO) { File(local).readBytes() }
            val r = ApiClient.upload("/upload", bytes, "image/jpeg")
            val url = r.json?.optString("url")
            return if (r.ok && !url.isNullOrBlank()) Pair(url, null) else Pair(null, r.message)
        } finally {
            delete(local)
        }
    }

    /** Galereyadan tanlangan rasmni [maxSide] pikselgacha kichraytirib, JPEG baytlari ko'rinishida qaytaradi. */
    suspend fun jpegBytes(context: Context, uri: Uri, maxSide: Int): ByteArray? = withContext(Dispatchers.IO) {
        val local = save(context, uri, maxSide) ?: return@withContext null
        try {
            File(local).readBytes()
        } finally {
            delete(local)
        }
    }

    fun save(context: Context, uri: Uri, maxSideLimit: Int = 1600): String? {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxSide / sample > maxSideLimit) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null

            // Telefon suratni yonboshlatib saqlagan bo'lsa, to'g'rilaymiz
            val degrees = try {
                resolver.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }

            val dir = File(context.filesDir, "game_images").apply { mkdirs() }
            val file = File(dir, UUID.randomUUID().toString() + ".jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun delete(path: String) {
        if (isLocal(path)) {
            try {
                File(path).delete()
            } catch (e: Exception) {
                // e'tibor bermaymiz
            }
        }
    }
}
