package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.UUID

/** Local-only evidence. Re-encoding strips metadata; OCR is a review aid, never proof of authenticity. */
object OdometerPhotoStore {
    fun importPhoto(context: Context, uri: Uri): Pair<String, String> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 20 * 1024 * 1024) { "Photo exceeds 20 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot read photo")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a valid photo" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2000) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Cannot decode photo")
        val directory = File(context.noBackupFilesDir, "odometers").apply { check(exists() || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = try { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text }
                catch (_: Exception) { "" } finally { recognizer.close() }
            return file.absolutePath to text
        } catch (e: Exception) { file.delete(); throw e }
        finally { bitmap.recycle() }
    }
    fun file(context: Context, path: String): File? {
        val root = File(context.noBackupFilesDir, "odometers").canonicalFile
        val file = File(path).canonicalFile
        return file.takeIf { it.parentFile == root && it.isFile }
    }
    fun delete(context: Context, path: String) { file(context, path)?.delete() }
}
