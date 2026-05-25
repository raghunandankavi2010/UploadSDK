package com.uploadsdk.data.chunk

import com.uploadsdk.domain.model.ChunkInfo
import com.uploadsdk.domain.repository.ChunkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChunkEngine @Inject constructor() : ChunkRepository {

    override suspend fun splitFileIntoChunks(file: File, chunkSize: Int): List<ChunkInfo> {
        val fileSize = file.length()
        if (fileSize == 0L) return emptyList()

        val chunks = mutableListOf<ChunkInfo>()
        var startByte = 0L
        var chunkIndex = 0

        // Adaptive sizing: use smaller chunks for files < 10MB
        val adaptiveChunkSize = when {
            fileSize < 10 * 1024 * 1024 -> (fileSize / 4).toInt().coerceAtLeast(64 * 1024)
            else -> chunkSize
        }

        while (startByte < fileSize) {
            val endByte = minOf(startByte + adaptiveChunkSize - 1, fileSize - 1)
            chunks.add(
                ChunkInfo(
                    chunkIndex = chunkIndex,
                    startByte = startByte,
                    endByte = endByte,
                    size = endByte - startByte + 1
                )
            )
            startByte = endByte + 1
            chunkIndex++
        }
        return chunks
    }

    override suspend fun getChunkData(file: File, chunk: ChunkInfo): ByteArray {
        val length = chunk.size.toInt()
        val buffer = ByteArray(length)
        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(chunk.startByte)
                raf.readFully(buffer)
            }
        }
        return buffer
    }

    override suspend fun verifyChunkIntegrity(chunk: ChunkInfo, data: ByteArray): Boolean {
        if (chunk.checksum == null) return true
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        val computed = hash.joinToString("") { "%02x".format(it) }
        return computed == chunk.checksum
    }

    override suspend fun computeFileChecksum(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        java.io.FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
