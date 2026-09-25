package com.edgar.viewer3d

import kotlin.math.abs
import kotlin.math.sqrt

data class Bounds3(
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float
) {
    val sizeX get() = maxX - minX
    val sizeY get() = maxY - minY
    val sizeZ get() = maxZ - minZ
}

data class MeshData(
    val name: String,
    val positions: FloatArray,
    val normals: FloatArray?,
    val indices: IntArray,
    val unit: String? = null
) {
    val vertexCount get() = positions.size / 3
    val triangleCount get() = indices.size / 3

    fun bounds(): Bounds3 {
        require(positions.size >= 3) { "Mesh has no vertices." }
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            minX = minOf(minX, x); minY = minOf(minY, y); minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y); maxZ = maxOf(maxZ, z)
            i += 3
        }
        return Bounds3(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun withGeneratedNormals(): MeshData {
        if (normals != null && normals.size == positions.size && normals.any { abs(it) > 0.000001f }) {
            val normalized = FloatArray(normals.size)
            var valid = 0
            var i = 0
            while (i < normals.size) {
                val x = normals[i]; val y = normals[i + 1]; val z = normals[i + 2]
                val len = sqrt(x * x + y * y + z * z)
                if (len.isFinite() && len > 0.000001f) {
                    normalized[i] = x / len
                    normalized[i + 1] = y / len
                    normalized[i + 2] = z / len
                    valid++
                }
                i += 3
            }
            if (valid == positions.size / 3) return copy(normals = normalized)
        }
        val out = FloatArray(positions.size)
        var t = 0
        while (t + 2 < indices.size) {
            val ia = indices[t]; val ib = indices[t + 1]; val ic = indices[t + 2]
            val ax = positions[ia * 3]; val ay = positions[ia * 3 + 1]; val az = positions[ia * 3 + 2]
            val bx = positions[ib * 3]; val by = positions[ib * 3 + 1]; val bz = positions[ib * 3 + 2]
            val cx = positions[ic * 3]; val cy = positions[ic * 3 + 1]; val cz = positions[ic * 3 + 2]
            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = cx - ax; val vy = cy - ay; val vz = cz - az
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            for (v in intArrayOf(ia, ib, ic)) {
                out[v * 3] += nx; out[v * 3 + 1] += ny; out[v * 3 + 2] += nz
            }
            t += 3
        }
        var i = 0
        while (i < out.size) {
            val x = out[i]; val y = out[i + 1]; val z = out[i + 2]
            val len = sqrt(x * x + y * y + z * z)
            if (len > 0.000001f) {
                out[i] = x / len; out[i + 1] = y / len; out[i + 2] = z / len
            } else {
                out[i] = 0f; out[i + 1] = 1f; out[i + 2] = 0f
            }
            i += 3
        }
        return copy(normals = out)
    }
}

data class ModelStats(
    val format: String,
    val byteSize: Long,
    val vertices: Int? = null,
    val triangles: Int? = null,
    val bounds: Bounds3? = null,
    val unit: String? = null,
    val animations: Int? = null
)

data class PreparedModel(
    val bytes: ByteArray,
    val isGltfJson: Boolean,
    val stats: ModelStats
)
