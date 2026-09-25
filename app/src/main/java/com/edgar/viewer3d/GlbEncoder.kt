package com.edgar.viewer3d

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GlbEncoder {
    fun encode(input: MeshData): ByteArray {
        val mesh = input.withGeneratedNormals()
        val normals = requireNotNull(mesh.normals)
        val bounds = mesh.bounds()

        val posBytes = floats(mesh.positions)
        val normBytes = floats(normals)
        val idxBytes = ints(mesh.indices)

        val posOffset = 0
        val normOffset = align4(posBytes.size)
        val idxOffset = align4(normOffset + normBytes.size)
        val binLength = align4(idxOffset + idxBytes.size)
        val bin = ByteArray(binLength)
        posBytes.copyInto(bin, posOffset)
        normBytes.copyInto(bin, normOffset)
        idxBytes.copyInto(bin, idxOffset)

        fun f(v: Float) = if (v.isFinite()) v.toString() else "0"
        val safeName = JSONObject.quote(mesh.name)
        val json = """
            {
              "asset":{"version":"2.0","generator":"Android 3D Viewer v0.1.0"},
              "scene":0,
              "scenes":[{"nodes":[0]}],
              "nodes":[{"mesh":0,"name":$safeName}],
              "meshes":[{"name":$safeName,"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0,"mode":4}]}],
              "materials":[{"name":"Default","pbrMetallicRoughness":{"baseColorFactor":[0.72,0.75,0.80,1.0],"metallicFactor":0.0,"roughnessFactor":0.72},"doubleSided":true}],
              "buffers":[{"byteLength":$binLength}],
              "bufferViews":[
                {"buffer":0,"byteOffset":$posOffset,"byteLength":${posBytes.size},"target":34962},
                {"buffer":0,"byteOffset":$normOffset,"byteLength":${normBytes.size},"target":34962},
                {"buffer":0,"byteOffset":$idxOffset,"byteLength":${idxBytes.size},"target":34963}
              ],
              "accessors":[
                {"bufferView":0,"componentType":5126,"count":${mesh.vertexCount},"type":"VEC3","min":[${f(bounds.minX)},${f(bounds.minY)},${f(bounds.minZ)}],"max":[${f(bounds.maxX)},${f(bounds.maxY)},${f(bounds.maxZ)}]},
                {"bufferView":1,"componentType":5126,"count":${mesh.vertexCount},"type":"VEC3"},
                {"bufferView":2,"componentType":5125,"count":${mesh.indices.size},"type":"SCALAR"}
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val jsonPaddedLength = align4(json.size)
        val totalLength = 12 + 8 + jsonPaddedLength + 8 + bin.size
        val out = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67)
        out.putInt(2)
        out.putInt(totalLength)
        out.putInt(jsonPaddedLength)
        out.putInt(0x4E4F534A)
        out.put(json)
        repeat(jsonPaddedLength - json.size) { out.put(0x20.toByte()) }
        out.putInt(bin.size)
        out.putInt(0x004E4942)
        out.put(bin)
        return out.array()
    }

    private fun align4(value: Int) = (value + 3) and -4

    private fun floats(values: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(bb::putFloat)
        return bb.array()
    }

    private fun ints(values: IntArray): ByteArray {
        val bb = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(bb::putInt)
        return bb.array()
    }
}
