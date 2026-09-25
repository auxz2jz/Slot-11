package com.edgar.viewer3d

object ModelImporter {
    private val supported = setOf(
        "glb", "gltf", "stl", "obj", "ply", "off", "3mf", "amf", "x3d",
        "step", "stp"
    )

    fun extension(fileName: String) = fileName.substringAfterLast('.', "").lowercase()
    fun isSupported(fileName: String) = extension(fileName) in supported

    fun prepare(fileName: String, bytes: ByteArray, workDir: java.io.File? = null): PreparedModel {
        return when (val ext = extension(fileName)) {
            "glb" -> {
                val repaired = GlbRepair.prepare(bytes)
                val label = if (repaired.generatedNormals > 0 || repaired.assignedFallbackMaterials > 0) {
                    "GLB (auto-repaired)"
                } else {
                    "GLB"
                }
                PreparedModel(
                    repaired.bytes,
                    false,
                    ModelStats(label, bytes.size.toLong())
                )
            }
            "gltf" -> PreparedModel(bytes, true, ModelStats("glTF", bytes.size.toLong()))
            "stl" -> fromMesh("STL", bytes, StlParser.parse(fileName, bytes))
            "obj" -> fromMesh("OBJ", bytes, ObjParser.parse(fileName, bytes))
            "ply" -> fromMesh("PLY", bytes, PlyParser.parse(fileName, bytes))
            "off" -> fromMesh("OFF", bytes, OffParser.parse(fileName, bytes))
            "3mf" -> fromMesh("3MF", bytes, ThreeMfParser.parse(fileName, bytes))
            "amf" -> fromMesh("AMF", bytes, AmfParser.parse(fileName, bytes))
            "x3d" -> fromMesh("X3D", bytes, X3dParser.parse(fileName, bytes))
            "step", "stp" -> {
                val dir = workDir ?: error("STEP/STP import requires an Android cache directory.")
                fromMesh("STEP (OCCT)", bytes, OcctStepImporter.parse(fileName, bytes, dir))
            }
            else -> error("Unsupported file type .$ext. Supported: GLB, glTF, STL, OBJ, PLY, OFF, 3MF, AMF, X3D, STEP, STP.")
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
