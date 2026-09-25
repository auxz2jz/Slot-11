package com.edgar.viewer3d

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class GlbRepairResult(
    val bytes: ByteArray,
    val generatedNormals: Int,
    val assignedFallbackMaterials: Int
)

object GlbRepair {
    private const val GLB_MAGIC = 0x46546C67
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942
    private const val MODE_TRIANGLES = 4
    private const val FLOAT = 5126
    private const val UNSIGNED_BYTE = 5121
    private const val UNSIGNED_SHORT = 5123
    private const val UNSIGNED_INT = 5125

    fun prepare(source: ByteArray): GlbRepairResult {
        val parsed = parse(source) ?: return GlbRepairResult(source, 0, 0)
        val root = parsed.first
        val originalBin = parsed.second ?: return GlbRepairResult(source, 0, 0)

        val accessors = root.optJSONArray("accessors") ?: return GlbRepairResult(source, 0, 0)
        val bufferViews = root.optJSONArray("bufferViews") ?: return GlbRepairResult(source, 0, 0)
        val meshes = root.optJSONArray("meshes") ?: return GlbRepairResult(source, 0, 0)
        val bin = ByteArrayOutputStream(originalBin.size + 64 * 1024)
        bin.write(originalBin)

        var generatedNormals = 0
        var missingMaterialCount = 0

        for (meshIndex in 0 until meshes.length()) {
            val mesh = meshes.optJSONObject(meshIndex) ?: continue
            val primitives = mesh.optJSONArray("primitives") ?: continue
            for (primitiveIndex in 0 until primitives.length()) {
                val primitive = primitives.optJSONObject(primitiveIndex) ?: continue
                val attributes = primitive.optJSONObject("attributes") ?: continue

                if (!primitive.has("material")) missingMaterialCount++

                if (primitive.optInt("mode", MODE_TRIANGLES) != MODE_TRIANGLES) continue
                if (attributes.has("NORMAL")) continue
                if (!attributes.has("POSITION")) continue

                val positionAccessorIndex = attributes.optInt("POSITION", -1)
                if (positionAccessorIndex !in 0 until accessors.length()) continue
                val positions = readPositions(
                    originalBin,
                    accessors,
                    bufferViews,
                    positionAccessorIndex
                ) ?: continue

                val indices = readIndices(
                    originalBin,
                    accessors,
                    bufferViews,
                    primitive,
                    positions.size / 3
                ) ?: continue

                if (indices.size < 3 || indices.size % 3 != 0) continue
                val normals = generateNormals(positions, indices) ?: continue

                while (bin.size() % 4 != 0) bin.write(0)
                val normalByteOffset = bin.size()
                val normalBytes = ByteBuffer.allocate(normals.size * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply { normals.forEach(::putFloat) }
                    .array()
                bin.write(normalBytes)

                val normalBufferViewIndex = bufferViews.length()
                bufferViews.put(
                    JSONObject()
                        .put("buffer", 0)
                        .put("byteOffset", normalByteOffset)
                        .put("byteLength", normalBytes.size)
                        .put("target", 34962)
                )

                val normalAccessorIndex = accessors.length()
                accessors.put(
                    JSONObject()
                        .put("bufferView", normalBufferViewIndex)
                        .put("componentType", FLOAT)
                        .put("count", positions.size / 3)
                        .put("type", "VEC3")
                )

                attributes.put("NORMAL", normalAccessorIndex)
                generatedNormals++
            }
        }

        var assignedFallbackMaterials = 0
        if (missingMaterialCount > 0) {
            val materials = root.optJSONArray("materials") ?: JSONArray().also {
                root.put("materials", it)
            }
            val fallbackMaterialIndex = materials.length()
            materials.put(
                JSONObject()
                    .put("name", "Viewer Default")
                    .put(
                        "pbrMetallicRoughness",
                        JSONObject()
                            .put("baseColorFactor", JSONArray(doubleArrayOf(1.0, 1.0, 1.0, 1.0).toList()))
                            .put("metallicFactor", 0.0)
                            .put("roughnessFactor", 0.8)
                    )
                    .put("doubleSided", true)
            )

            for (meshIndex in 0 until meshes.length()) {
                val mesh = meshes.optJSONObject(meshIndex) ?: continue
                val primitives = mesh.optJSONArray("primitives") ?: continue
                for (primitiveIndex in 0 until primitives.length()) {
                    val primitive = primitives.optJSONObject(primitiveIndex) ?: continue
                    if (!primitive.has("material")) {
                        primitive.put("material", fallbackMaterialIndex)
                        assignedFallbackMaterials++
                    }
                }
            }
        }

        if (generatedNormals == 0 && assignedFallbackMaterials == 0) {
            return GlbRepairResult(source, 0, 0)
        }

        val finalBin = bin.toByteArray()
        val buffers = root.optJSONArray("buffers")
        if (buffers != null && buffers.length() > 0) {
            buffers.optJSONObject(0)?.put("byteLength", finalBin.size)
        }

        val asset = root.optJSONObject("asset")
        if (asset != null) {
            val oldGenerator = asset.optString("generator", "")
            val suffix = "Android 3D Viewer auto-repair"
            asset.put("generator", if (oldGenerator.isBlank()) suffix else "$oldGenerator + $suffix")
        }

        return GlbRepairResult(
            bytes = rebuild(root, finalBin),
            generatedNormals = generatedNormals,
            assignedFallbackMaterials = assignedFallbackMaterials
        )
    }

    private fun parse(source: ByteArray): Pair<JSONObject, ByteArray?>? {
        if (source.size < 20) return null
        val input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
        if (input.int != GLB_MAGIC) return null
        if (input.int != 2) return null
        val declaredLength = input.int
        if (declaredLength > source.size || declaredLength < 20) return null

        var json: JSONObject? = null
        var bin: ByteArray? = null
        while (input.position() + 8 <= declaredLength) {
            val chunkLength = input.int
            val chunkType = input.int
            if (chunkLength < 0 || input.position() + chunkLength > declaredLength) return null
            val chunk = ByteArray(chunkLength)
            input.get(chunk)
            when (chunkType) {
                JSON_CHUNK -> {
                    val text = chunk.toString(Charsets.UTF_8)
                        .trimEnd('\u0000', ' ', '\t', '\r', '\n')
                    json = JSONObject(text)
                }
                BIN_CHUNK -> bin = chunk
            }
        }
        val root = json ?: return null
        return root to bin
    }

    private fun readPositions(
        bin: ByteArray,
        accessors: JSONArray,
        bufferViews: JSONArray,
        accessorIndex: Int
    ): FloatArray? {
        val accessor = accessors.optJSONObject(accessorIndex) ?: return null
        if (accessor.optInt("componentType") != FLOAT) return null
        if (accessor.optString("type") != "VEC3") return null
        if (accessor.has("sparse")) return null

        val count = accessor.optInt("count", -1)
        if (count <= 0) return null
        val bufferViewIndex = accessor.optInt("bufferView", -1)
        val bufferView = bufferViews.optJSONObject(bufferViewIndex) ?: return null
        if (bufferView.optInt("buffer", 0) != 0) return null

        val start = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = bufferView.optInt("byteStride", 12)
        if (stride < 12) return null
        if (start < 0 || start + (count - 1) * stride + 12 > bin.size) return null

        val input = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(count * 3)
        for (i in 0 until count) {
            val p = start + i * stride
            out[i * 3] = input.getFloat(p)
            out[i * 3 + 1] = input.getFloat(p + 4)
            out[i * 3 + 2] = input.getFloat(p + 8)
        }
        return out
    }

    private fun readIndices(
        bin: ByteArray,
        accessors: JSONArray,
        bufferViews: JSONArray,
        primitive: JSONObject,
        vertexCount: Int
    ): IntArray? {
        if (!primitive.has("indices")) {
            val sequential = IntArray(vertexCount) { it }
            return if (sequential.size % 3 == 0) sequential else null
        }

        val accessorIndex = primitive.optInt("indices", -1)
        val accessor = accessors.optJSONObject(accessorIndex) ?: return null
        if (accessor.optString("type") != "SCALAR") return null
        if (accessor.has("sparse")) return null

        val componentType = accessor.optInt("componentType")
        val componentSize = when (componentType) {
            UNSIGNED_BYTE -> 1
            UNSIGNED_SHORT -> 2
            UNSIGNED_INT -> 4
            else -> return null
        }

        val count = accessor.optInt("count", -1)
        if (count <= 0) return null
        val bufferViewIndex = accessor.optInt("bufferView", -1)
        val bufferView = bufferViews.optJSONObject(bufferViewIndex) ?: return null
        if (bufferView.optInt("buffer", 0) != 0) return null

        val start = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = bufferView.optInt("byteStride", componentSize)
        if (stride < componentSize) return null
        if (start < 0 || start + (count - 1) * stride + componentSize > bin.size) return null

        val input = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        val out = IntArray(count)
        for (i in 0 until count) {
            val p = start + i * stride
            out[i] = when (componentType) {
                UNSIGNED_BYTE -> input.get(p).toInt() and 0xFF
                UNSIGNED_SHORT -> input.getShort(p).toInt() and 0xFFFF
                UNSIGNED_INT -> {
                    val value = input.getInt(p).toLong() and 0xFFFFFFFFL
                    if (value > Int.MAX_VALUE) return null
                    value.toInt()
                }
                else -> return null
            }
            if (out[i] !in 0 until vertexCount) return null
        }
        return out
    }

    private fun generateNormals(positions: FloatArray, indices: IntArray): FloatArray? {
        val vertexCount = positions.size / 3
        if (vertexCount <= 0) return null
        val normals = FloatArray(positions.size)

        var i = 0
        while (i + 2 < indices.size) {
            val ia = indices[i]
            val ib = indices[i + 1]
            val ic = indices[i + 2]

            val ax = positions[ia * 3]
            val ay = positions[ia * 3 + 1]
            val az = positions[ia * 3 + 2]
            val bx = positions[ib * 3]
            val by = positions[ib * 3 + 1]
            val bz = positions[ib * 3 + 2]
            val cx = positions[ic * 3]
            val cy = positions[ic * 3 + 1]
            val cz = positions[ic * 3 + 2]

            val ux = bx - ax
            val uy = by - ay
            val uz = bz - az
            val vx = cx - ax
            val vy = cy - ay
            val vz = cz - az
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx

            for (v in intArrayOf(ia, ib, ic)) {
                normals[v * 3] += nx
                normals[v * 3 + 1] += ny
                normals[v * 3 + 2] += nz
            }
            i += 3
        }

        i = 0
        while (i < normals.size) {
            val x = normals[i]
            val y = normals[i + 1]
            val z = normals[i + 2]
            val length = sqrt(x * x + y * y + z * z)
            if (length > 0.000001f && length.isFinite()) {
                normals[i] = x / length
                normals[i + 1] = y / length
                normals[i + 2] = z / length
            } else {
                normals[i] = 0f
                normals[i + 1] = 1f
                normals[i + 2] = 0f
            }
            i += 3
        }
        return normals
    }

    private fun rebuild(root: JSONObject, bin: ByteArray): ByteArray {
        var jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
        val jsonPadding = (4 - jsonBytes.size % 4) % 4
        if (jsonPadding > 0) {
            jsonBytes += ByteArray(jsonPadding) { 0x20 }
        }

        val binPadding = (4 - bin.size % 4) % 4
        val paddedBin = if (binPadding == 0) bin else bin + ByteArray(binPadding)
        val totalLength = 12 + 8 + jsonBytes.size + 8 + paddedBin.size

        return ByteBuffer.allocate(totalLength)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(GLB_MAGIC)
                putInt(2)
                putInt(totalLength)
                putInt(jsonBytes.size)
                putInt(JSON_CHUNK)
                put(jsonBytes)
                putInt(paddedBin.size)
                putInt(BIN_CHUNK)
                put(paddedBin)
            }
            .array()
    }
}
