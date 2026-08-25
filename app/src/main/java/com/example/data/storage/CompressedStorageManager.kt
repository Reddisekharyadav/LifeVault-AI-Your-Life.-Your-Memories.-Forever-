package com.example.data.storage

import android.util.Base64
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@JsonClass(generateAdapter = true)
data class CompressedVaultPayload(
    val email: String,
    val exportTimestamp: Long,
    val totalMemories: Int,
    val totalRelationships: Int,
    val memories: List<LocalMemory>,
    val relationships: List<LocalRelationship>
)

data class CompressionResult(
    val compressedPayload: String,
    val rawBytes: Long,
    val compressedBytes: Long,
    val savingsPercent: Int,
    val itemsCount: Int
)

data class StorageTelemetry(
    val rawSizeBytes: Long,
    val compressedSizeBytes: Long,
    val totalEntries: Int,
    val compressionRatioText: String,
    val spaceSavedBytes: Long
)

object CompressedStorageManager {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(CompressedVaultPayload::class.java)

    /**
     * Compresses any string using GZIP compression algorithm.
     */
    fun compress(input: String): ByteArray {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzip ->
            gzip.write(bytes)
        }
        return byteStream.toByteArray()
    }

    /**
     * Decompresses GZIP byte array back into a standard UTF-8 string.
     */
    fun decompress(compressedBytes: ByteArray): String {
        val byteStream = ByteArrayInputStream(compressedBytes)
        val gzip = GZIPInputStream(byteStream)
        return gzip.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    /**
     * Serializes memories and relationships into a JSON payload and compresses it with GZIP,
     * encoding into a portable Base64 archive string.
     */
    fun exportCompressedVault(
        email: String,
        memories: List<LocalMemory>,
        relationships: List<LocalRelationship>
    ): CompressionResult {
        val payload = CompressedVaultPayload(
            email = email,
            exportTimestamp = System.currentTimeMillis(),
            totalMemories = memories.size,
            totalRelationships = relationships.size,
            memories = memories,
            relationships = relationships
        )

        val jsonString = payloadAdapter.toJson(payload)
        val rawBytes = jsonString.toByteArray(StandardCharsets.UTF_8).size.toLong()

        val compressedBytes = compress(jsonString)
        val compressedLength = compressedBytes.size.toLong()

        val base64Encoded = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

        val savings = if (rawBytes > 0) {
            (((rawBytes - compressedLength).toDouble() / rawBytes.toDouble()) * 100).toInt().coerceAtLeast(0)
        } else {
            0
        }

        return CompressionResult(
            compressedPayload = base64Encoded,
            rawBytes = rawBytes,
            compressedBytes = compressedLength,
            savingsPercent = savings,
            itemsCount = memories.size + relationships.size
        )
    }

    /**
     * Restores vault from a compressed Base64 GZIP archive string or raw JSON string.
     */
    fun importCompressedVault(archiveData: String): CompressedVaultPayload? {
        val cleaned = archiveData.trim()
        return try {
            // First attempt to decode as Base64 GZIP
            val decodedBytes = Base64.decode(cleaned, Base64.DEFAULT)
            val decompressedJson = decompress(decodedBytes)
            payloadAdapter.fromJson(decompressedJson)
        } catch (e: Exception) {
            // Fallback: try direct JSON parsing if raw JSON was pasted
            try {
                payloadAdapter.fromJson(cleaned)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Computes storage telemetry comparing raw SQLite footprint vs GZIP compressed footprint.
     */
    fun computeTelemetry(memories: List<LocalMemory>, relationships: List<LocalRelationship>): StorageTelemetry {
        val payload = CompressedVaultPayload(
            email = "local",
            exportTimestamp = System.currentTimeMillis(),
            totalMemories = memories.size,
            totalRelationships = relationships.size,
            memories = memories,
            relationships = relationships
        )
        val jsonString = payloadAdapter.toJson(payload)
        val rawSize = jsonString.toByteArray(StandardCharsets.UTF_8).size.toLong()
        val compressed = compress(jsonString).size.toLong()
        val saved = (rawSize - compressed).coerceAtLeast(0)
        val ratioPercent = if (rawSize > 0) {
            (((rawSize - compressed).toDouble() / rawSize.toDouble()) * 100).toInt().coerceAtLeast(0)
        } else {
            0
        }

        return StorageTelemetry(
            rawSizeBytes = rawSize,
            compressedSizeBytes = compressed,
            totalEntries = memories.size + relationships.size,
            compressionRatioText = "$ratioPercent% Compressed",
            spaceSavedBytes = saved
        )
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
