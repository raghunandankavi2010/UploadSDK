package com.uploadsdk.data.preprocessor

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun generate(file: File, mimeType: String): String? {
        return try {
            when {
                mimeType.startsWith("image/") -> generateImageThumbnail(file)
                mimeType.startsWith("video/") -> generateVideoThumbnail(file)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateImageThumbnail(file: File): String? {
        val bitmap = ThumbnailUtils.extractThumbnail(
            android.graphics.BitmapFactory.decodeFile(file.absolutePath),
            512,
            512
        ) ?: return null
        return saveThumbnail(bitmap, file.name)
    }

    private fun generateVideoThumbnail(file: File): String? {
        val bitmap = ThumbnailUtils.createVideoThumbnail(
            file.absolutePath,
            MediaStore.Images.Thumbnails.MINI_KIND
        ) ?: return null
        return saveThumbnail(bitmap, file.name)
    }

    private fun saveThumbnail(bitmap: Bitmap, originalName: String): String {
        val thumbFile = File(context.cacheDir, "thumbs/thumb_${originalName}.jpg").apply {
            parentFile?.mkdirs()
        }
        FileOutputStream(thumbFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        bitmap.recycle()
        return thumbFile.absolutePath
    }
}
