package com.sos.app.upload

import android.util.Log
import java.io.File
import kotlin.math.min

/**
 * Splits large video files into manageable chunks for upload.
 * Chunk size: ~20-25 MB to fit email attachments.
 */
class VideoChunker(private val inputFile: File) {
    companion object {
        private const val TAG = "VideoChunker"
        private const val CHUNK_SIZE = 20 * 1024 * 1024 // 20 MB chunks
    }

    fun chunk(): List<File> {
        val chunks = mutableListOf<File>()
        if (!inputFile.exists()) {
            Log.e(TAG, "Input file does not exist")
            return chunks
        }

        try {
            val totalSize = inputFile.length()
            var chunkIndex = 0
            var offset = 0L

            while (offset < totalSize) {
                val chunkSize = min(CHUNK_SIZE.toLong(), totalSize - offset).toInt()
                val chunkFile = File(inputFile.parent, "${inputFile.nameWithoutExtension}_chunk_$chunkIndex.mp4")

                inputFile.inputStream().use { input ->
                    input.skip(offset)
                    chunkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var bytesWritten = 0

                        while (bytesWritten < chunkSize && input.read(buffer, 0, min(buffer.size, chunkSize - bytesWritten)).also { bytesRead = it } >= 0) {
                            output.write(buffer, 0, bytesRead)
                            bytesWritten += bytesRead
                        }
                    }
                }

                chunks.add(chunkFile)
                offset += chunkSize.toLong()
                chunkIndex++
                Log.d(TAG, "Created chunk $chunkIndex: ${chunkFile.name} (${chunkFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error chunking file: ${e.message}")
        }

        return chunks
    }
}
