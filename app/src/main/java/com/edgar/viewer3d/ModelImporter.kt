package com.edgar.viewer3d

object ModelImporter {
    private val supported = setOf(
        "glb", "gltf", "stl", "obj", "ply", "off", "3mf", "amf", "x3d",
        "step", "stp"
    )

    fun extension(fileName: String) = fileName.substringAfterLast('.', "").lowercase()
    fun isSupported(fileName: String) = extension(fileName) in supported

    fun prepare(
        fileName: String,
        bytes: ByteArray,
        workDir: java.io.File? = null,
        operationId: String? = null
    ): PreparedModel {
        val ext = extension(fileName)
        DiagnosticLogger.event(
            "PROCESSING",
            "IMPORT_DISPATCHED",
            mapOf(
                "displayName" to fileName,
                "extension" to ext,
                "byteSize" to bytes.size
            ),
            operationId = operationId
        )

        try {
            return when (ext) {
                "glb" -> {
                    val repaired = GlbRepair.prepare(bytes)
                    DiagnosticLogger.event(
                        "PROCESSING",
                        "GLB_REPAIR_RESULT",
                        mapOf(
                            "generatedNormalPrimitives" to repaired.generatedNormals,
                            "assignedFallbackMaterials" to repaired.assignedFallbackMaterials,
                            "sourceBytes" to bytes.size,
                            "outputBytes" to repaired.bytes.size
                        ),
                        operationId = operationId
                    )
                    val label =
                        if (repaired.generatedNormals > 0 ||
                            repaired.assignedFallbackMaterials > 0
                        ) {
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
                "gltf" -> {
                    DiagnosticLogger.event(
                        "PROCESSING",
                        "GLTF_JSON_ACCEPTED",
                        mapOf("embeddedResourcesOnly" to true),
                        operationId = operationId
                    )
                    PreparedModel(
                        bytes,
                        true,
                        ModelStats("glTF", bytes.size.toLong())
                    )
                }
                "stl" -> fromMesh(
                    "STL",
                    bytes,
                    StlParser.parse(fileName, bytes),
                    operationId
                )
                "obj" -> fromMesh(
                    "OBJ",
                    bytes,
                    ObjParser.parse(fileName, bytes),
                    operationId
                )
                "ply" -> fromMesh(
                    "PLY",
                    bytes,
                    PlyParser.parse(fileName, bytes),
                    operationId
                )
                "off" -> fromMesh(
                    "OFF",
                    bytes,
                    OffParser.parse(fileName, bytes),
                    operationId
                )
                "3mf" -> fromMesh(
                    "3MF",
                    bytes,
                    ThreeMfParser.parse(fileName, bytes),
                    operationId
                )
                "amf" -> fromMesh(
                    "AMF",
                    bytes,
                    AmfParser.parse(fileName, bytes),
                    operationId
                )
                "x3d" -> fromMesh(
                    "X3D",
                    bytes,
                    X3dParser.parse(fileName, bytes),
                    operationId
                )
                "step", "stp" -> {
                    val dir = workDir
                        ?: error("STEP/STP import requires an Android cache directory.")
                    fromMesh(
                        "STEP (OCCT)",
                        bytes,
                        OcctStepImporter.parse(
                            fileName,
                            bytes,
                            dir,
                            operationId
                        ),
                        operationId
                    )
                }
                else -> error(
                    "Unsupported file type ." + ext +
                        ". Supported: GLB, glTF, STL, OBJ, PLY, OFF, 3MF, " +
                        "AMF, X3D, STEP, STP."
                )
            }
        } catch (t: Throwable) {
            DiagnosticLogger.error(
                module = "ModelImporter",
                operation = "IMPORT_" + ext.uppercase(),
                throwable = t,
                details = mapOf(
                    "displayName" to fileName,
                    "extension" to ext,
                    "byteSize" to bytes.size
                ),
                operationId = operationId
            )
            throw t
        }
    }

    private fun fromMesh(
        format: String,
        source: ByteArray,
        mesh: MeshData,
        operationId: String?
    ): PreparedModel {
        val bounds = mesh.bounds()
        DiagnosticLogger.event(
            "PROCESSING",
            "MESH_PARSED",
            mapOf(
                "format" to format,
                "vertices" to mesh.vertexCount,
                "triangles" to mesh.triangleCount,
                "unit" to mesh.unit,
                "bounds" to mapOf(
                    "minX" to bounds.minX,
                    "minY" to bounds.minY,
                    "minZ" to bounds.minZ,
                    "maxX" to bounds.maxX,
                    "maxY" to bounds.maxY,
                    "maxZ" to bounds.maxZ
                )
            ),
            operationId = operationId
        )

        val encoded = GlbEncoder.encode(mesh)
        require(encoded.isNotEmpty()) {
            "Mesh conversion produced an empty GLB."
        }

        DiagnosticLogger.event(
            "PROCESSING",
            "MESH_GLB_ENCODED",
            mapOf(
                "format" to format,
                "sourceBytes" to source.size,
                "outputBytes" to encoded.size
            ),
            operationId = operationId
        )

        return PreparedModel(
            bytes = encoded,
            isGltfJson = false,
            stats = ModelStats(
                format = format,
                byteSize = source.size.toLong(),
                vertices = mesh.vertexCount,
                triangles = mesh.triangleCount,
                bounds = bounds,
                unit = mesh.unit
            )
        )
    }
}
