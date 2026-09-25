package com.edgar.viewer3d

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
        workDir: File
    ): MeshData {
        loadError?.let {
            error(
                "STEP/STP support is unavailable on this device/build: " +
                    (it.message ?: it.javaClass.simpleName)
            )
        }

        val suffix = "." + name.substringAfterLast('.', "step").lowercase()
        val temp = File.createTempFile("viewer_step_", suffix, workDir)
        try {
            temp.outputStream().use { it.write(sourceBytes) }
            val packed = nativeLoadStep(temp.absolutePath)
            return unpack(name, packed)
        } finally {
            runCatching { temp.delete() }
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
