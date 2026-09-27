package com.edgar.viewer3d

import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OcctStepImporter {
    private const val MAGIC = 0x4F434354
    private const val VERSION = 1

    private val loadError: Throwable? = runCatching {
        System.loadLibrary("occt_step_bridge")
    }.exceptionOrNull()

    private external fun nativeLoadStep(path: String): ByteArray

    fun isAvailable(): Boolean = loadError == null

    fun parse(
        name: String,
        sourceBytes: ByteArray,
        workDir: File,
        operationId: String? = null
    ): MeshData {
        var stage = "native_library_check"
        val started = SystemClock.elapsedRealtime()

        try {
            loadError?.let {
                error(
                    "STEP/STP support is unavailable on this device/build: " +
                        (it.message ?: it.javaClass.simpleName)
                )
            }
            DiagnosticLogger.event(
                "PROCESSING",
                "STEP_OCCT_AVAILABLE",
                mapOf("available" to true),
                operationId = operationId
            )

            stage = "temporary_input_write"
            val suffix = "." + name.substringAfterLast('.', "step").lowercase()
            val temp = File.createTempFile("viewer_step_", suffix, workDir)
            try {
                temp.outputStream().use { it.write(sourceBytes) }
                require(temp.exists() && temp.length() == sourceBytes.size.toLong()) {
                    "STEP temporary input verification failed."
                }
                DiagnosticLogger.event(
                    "PROCESSING",
                    "STEP_TEMP_INPUT_READY",
                    mapOf(
                        "displayName" to name,
                        "byteSize" to sourceBytes.size,
                        "suffix" to suffix
                    ),
                    operationId = operationId
                )

                stage = "native_occt_load_and_tessellate"
                val nativeStarted = SystemClock.elapsedRealtime()
                DiagnosticLogger.event(
                    "PROCESSING",
                    "STEP_NATIVE_TESSELLATION_STARTED",
                    mapOf("displayName" to name),
                    operationId = operationId
                )
                val packed = nativeLoadStep(temp.absolutePath)
                require(packed.isNotEmpty()) {
                    "OCCT returned an empty STEP mesh payload."
                }
                DiagnosticLogger.event(
                    "PROCESSING",
                    "STEP_NATIVE_TESSELLATION_COMPLETED",
                    mapOf(
                        "packedBytes" to packed.size,
                        "durationMs" to (SystemClock.elapsedRealtime() - nativeStarted)
                    ),
                    operationId = operationId
                )

                stage = "bridge_unpack"
                val mesh = unpack(name, packed)
                DiagnosticLogger.event(
                    "PROCESSING",
                    "STEP_BRIDGE_UNPACKED",
                    mapOf(
                        "vertices" to mesh.vertexCount,
                        "triangles" to mesh.triangleCount,
                        "unit" to mesh.unit,
                        "durationMs" to (SystemClock.elapsedRealtime() - started)
                    ),
                    operationId = operationId
                )
                return mesh
            } finally {
                if (!temp.delete() && temp.exists()) {
                    DiagnosticLogger.warning(
                        "STEP_TEMP_DELETE_FAILED",
                        mapOf("fileName" to temp.name),
                        operationId = operationId
                    )
                }
            }
        } catch (t: Throwable) {
            DiagnosticLogger.error(
                module = "OcctStepImporter",
                operation = "STEP_IMPORT",
                throwable = t,
                details = mapOf(
                    "displayName" to name,
                    "sourceBytes" to sourceBytes.size,
                    "stage" to stage,
                    "durationMs" to (SystemClock.elapsedRealtime() - started)
                ),
                operationId = operationId
            )
            throw t
        }
    }

    private fun unpack(name: String, packed: ByteArray): MeshData {
        require(packed.size >= 16) { "OCCT returned an incomplete STEP mesh." }
        val input = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)
        require(input.int == MAGIC) { "OCCT STEP mesh has an invalid header." }
        require(input.int == VERSION) { "Unsupported OCCT STEP mesh bridge version." }

        val vertexCount = input.int
        val indexCount = input.int
        require(vertexCount > 0 && indexCount >= 3 && indexCount % 3 == 0) {
            "OCCT STEP mesh has invalid vertex/index counts."
        }

        val requiredBytes =
            16L + vertexCount.toLong() * 3L * 4L + indexCount.toLong() * 4L
        require(requiredBytes == packed.size.toLong()) {
            "OCCT STEP mesh payload length is inconsistent."
        }

        val positions = FloatArray(vertexCount * 3)
        for (i in positions.indices) {
            positions[i] = input.float
        }

        val indices = IntArray(indexCount)
        for (i in indices.indices) {
            val value = input.int
            require(value in 0 until vertexCount) {
                "OCCT STEP mesh contains an out-of-range triangle index."
            }
            indices[i] = value
        }

        return MeshData(
            name = name,
            positions = positions,
            normals = null,
            indices = indices,
            unit = "millimeter"
        ).withGeneratedNormals()
    }
}
