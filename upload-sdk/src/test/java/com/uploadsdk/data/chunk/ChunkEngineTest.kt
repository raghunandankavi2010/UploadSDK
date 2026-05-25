package com.uploadsdk.data.chunk

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChunkEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val chunkEngine = ChunkEngine()

    @Test
    fun `split small file into single chunk`() = runBlocking {
        val file = tempFolder.newFile("small.txt")
        file.writeText("Hello, World!")

        val chunks = chunkEngine.splitFileIntoChunks(file, 1024)

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals(file.length() - 1, chunks[0].endByte)
    }

    @Test
    fun `split large file into multiple chunks`() = runBlocking {
        val file = tempFolder.newFile("large.txt")
        val content = "A".repeat(10000)
        file.writeText(content)

        val chunks = chunkEngine.splitFileIntoChunks(file, 3000)

        assertTrue(chunks.size > 1)
        assertEquals(0L, chunks[0].startByte)
        assertEquals(2999L, chunks[0].endByte)
        assertEquals(3000L, chunks[1].startByte)
    }

    @Test
    fun `get chunk data returns correct bytes`() = runBlocking {
        val file = tempFolder.newFile("test.txt")
        file.writeText("ABCDEFGHIJ")

        val chunk = com.uploadsdk.domain.model.ChunkInfo(
            chunkIndex = 0,
            startByte = 2,
            endByte = 5
        )

        val data = chunkEngine.getChunkData(file, chunk)

        assertEquals(4, data.size)
        assertEquals("CDEF", String(data))
    }

    @Test
    fun `compute checksum is consistent`() = runBlocking {
        val file = tempFolder.newFile("checksum.txt")
        file.writeText("test content")

        val checksum1 = chunkEngine.computeFileChecksum(file)
        val checksum2 = chunkEngine.computeFileChecksum(file)

        assertEquals(checksum1, checksum2)
        assertEquals(64, checksum1.length) // SHA-256 hex length
    }
}
