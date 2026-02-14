package com.iotek3.colorfinder.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves and lists images in the app's Pictures directory (ColorFinder folder).
 */
class ImageRepository(private val context: Context) {

    private val appPicturesDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            val colorFinder = File(dir, FOLDER_NAME)
            if (!colorFinder.exists()) colorFinder.mkdirs()
            return colorFinder
        }

    fun listSavedImages(): List<File> =
        appPicturesDir.listFiles { f -> f.isFile && (f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true) || f.name.endsWith(".png", true)) }
            ?.sortedByDescending { it.lastModified() }
            ?.toList()
            ?: emptyList()

    fun saveFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val file = newImageFile()
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
                file
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveFromInputStream(input: InputStream, suggestedName: String? = null): File? {
        return try {
            val file = suggestedName?.let { File(appPicturesDir, it) } ?: newImageFile()
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun saveBitmapToFile(bitmap: android.graphics.Bitmap): File? {
        return try {
            val file = newImageFile()
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun getFile(path: String): File? {
        val f = File(path)
        return if (f.exists()) f else null
    }

    /** Deletes a saved image file. Returns true if deleted, false if file not in app folder or delete failed. */
    fun deleteFile(file: File): Boolean {
        if (!file.canonicalPath.startsWith(appPicturesDir.canonicalPath)) return false
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun deleteByPath(path: String): Boolean {
        val file = getFile(path) ?: return false
        return deleteFile(file)
    }

    fun bitmapFromFile(file: File): android.graphics.Bitmap? =
        BitmapFactory.decodeFile(file.absolutePath)

    private fun newImageFile(): File {
        val name = SimpleDateFormat(FILE_PATTERN, Locale.US).format(Date())
        return File(appPicturesDir, "$name.jpg")
    }

    companion object {
        const val FOLDER_NAME = "ColorFinder"
        private const val FILE_PATTERN = "yyyyMMdd_HHmmss"
    }
}
