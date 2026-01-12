package com.nymcard.cardsscan.factory

import android.content.Context
import android.content.res.AssetFileDescriptor
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Singleton factory for loading ML models from resources using Kotlin object
 */
object ResourceModelFactory {

    /**
     * Load model from raw resource using inline function for better performance
     */
    private inline fun loadModelFromResource(context: Context, resource: Int): MappedByteBuffer {
        return context.resources.openRawResourceFd(resource).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    /**
     * Extension function for AssetFileDescriptor to use with 'use' scope function
     */
    private inline fun <T> AssetFileDescriptor.use(block: (AssetFileDescriptor) -> T): T {
        return try {
            block(this)
        } finally {
            close()
        }
    }
}