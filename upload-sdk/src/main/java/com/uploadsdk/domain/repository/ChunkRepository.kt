package com.uploadsdk.domain.repository

import com.uploadsdk.domain.model.ChunkInfo
import java.io.File

interface ChunkRepository {
    suspend fun splitFileIntoChunks(file: File, chunkSize: Int): List<ChunkInfo>
    suspend fun getChunkData(file: File, chunk: ChunkInfo): ByteArray
    suspend fun verifyChunkIntegrity(chunk: ChunkInfo, data: ByteArray): Boolean
    suspend fun computeFileChecksum(file: File): String
}
