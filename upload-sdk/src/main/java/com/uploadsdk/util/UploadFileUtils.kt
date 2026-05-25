package com.uploadsdk.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object UploadFileUtils {

    fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index) ?: name
                }
            }
        }
        return name
    }

    fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) {
                    size = cursor.getLong(index)
                }
            }
        }
        return size
    }

    fun uriToFile(contentResolver: ContentResolver, uri: Uri, outputDir: File): File {
        val fileName = getFileName(contentResolver, uri)
        val file = File(outputDir, "upload_${System.currentTimeMillis()}_$fileName")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    fun deleteQuietly(file: File?) {
        try {
            file?.delete()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
