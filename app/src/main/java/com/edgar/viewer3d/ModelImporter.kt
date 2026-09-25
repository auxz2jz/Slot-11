package com.edgar.viewer3d

object ModelImporter {
    private val supported = setOf("glb", "gltf", "stl", "obj", "ply", "off", "3mf")

    fun extension(fileName: String) = fileName.substringAfterLast('.', "").lowercase()
    fun isSupported(fileName: String) = extension(fileName) in supported

    fun prepare(fileName: String, bytes: ByteArray): PreparedModel {
        return when (val ext = extension(fileName)) {
            "glb" -> PreparedModel(bytes, false, ModelStats("GLB", bytes.size.toLong()))
            "gltf" -> PreparedModel(bytes, true, ModelStats("glTF", bytes.size.toLong()))
            "stl" -> fromMesh("STL", bytes, StlParser.parse(fileName, bytes))
            "obj" -> fromMesh("OBJ", bytes, ObjParser.parse(fileName, bytes))
            "ply" -> fromMesh("PLY", bytes, PlyParser.parse(fileName, bytes))
            "off" -> fromMesh("OFF", bytes, OffParser.parse(fileName, bytes))
            "3mf" -> fromMesh("3MF", bytes, ThreeMfParser.parse(fileName, bytes))
            else -> error("Unsupported file type .$ext. Supported: GLB, glTF, STL, OBJ, PLY, OFF, 3MF.")
        }
    }

    private fun fromMesh(format: String, source: ByteArray, mesh: MeshData): PreparedModel {
        return PreparedModel(
            bytes = GlbEncoder.encode(mesh),
            isGltfJson = false,
            stats = ModelStats(
                format = format,
                byteSize = source.size.toLong(),
                vertices = mesh.vertexCount,
                triangles = mesh.triangleCount,
                bounds = mesh.bounds(),
                unit = mesh.unit
            )
        )
    }
}
