package com.uploadsdk.data.chunk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChunkEngineInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)

    private val chunkEngine = ChunkEngine()

    @Test
    fun testChunkSplittingOnDevice() = runBlocking {
        val file = tempFolder.newFile("device_test.txt")
        file.writeText("x".repeat(100000))

        val chunks = chunkEngine.splitFileIntoChunks(file, 10000)

        assertEquals(10, chunks.size)

        // Verify we can read each chunk
        chunks.forEach { chunk ->
            val data = chunkEngine.getChunkData(file, chunk)
            assertEquals(chunk.size.toInt(), data.size)
        }
    }

    @Test
    fun testLargeFileChunking() = runBlocking {
        val file = tempFolder.newFile("large.bin")
        // Create 1MB file
        file.writeBytes(ByteArray(1024 * 1024) { it.toByte() })

        val chunks = chunkEngine.splitFileIntoChunks(file, 256 * 1024)

        assertEquals(4, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals((256 * 1024 - 1).toLong(), chunks[0].endByte)
    }
}
