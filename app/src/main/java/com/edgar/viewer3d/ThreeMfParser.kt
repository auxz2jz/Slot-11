package com.edgar.viewer3d

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

object ThreeMfParser {
    private data class Transform(val values: FloatArray) {
        fun apply(x: Float, y: Float, z: Float): FloatArray {
            if (values.size != 12) return floatArrayOf(x, y, z)
            return floatArrayOf(
                x * values[0] + y * values[3] + z * values[6] + values[9],
                x * values[1] + y * values[4] + z * values[7] + values[10],
                x * values[2] + y * values[5] + z * values[8] + values[11]
            )
        }

        companion object {
            val IDENTITY = Transform(floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
                0f, 0f, 0f
            ))

            fun parse(raw: String?): Transform {
                if (raw.isNullOrBlank()) return IDENTITY
                val p = raw.trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                return if (p.size == 12) Transform(p.toFloatArray()) else IDENTITY
            }
        }
    }

    private data class MeshDef(
        val positions: FloatArray,
        val indices: IntArray
    )

    private data class ComponentDef(
        val objectId: String,
        val transform: Transform
    )

    private data class ObjectDef(
        val id: String,
        val mesh: MeshDef?,
        val components: List<ComponentDef>
    )

    private data class BuildItem(
        val objectId: String,
        val transform: Transform
    )

    private data class Counts(val vertices: Int, val triangles: Int)

    fun parse(name: String, bytes: ByteArray): MeshData {
        val modelBytes = findPrimaryModel(bytes)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") } catch (_: Exception) {}
            try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") } catch (_: Exception) {}
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(modelBytes))
        val root = doc.documentElement
        val unit = root.getAttribute("unit").ifBlank { "millimeter" }

        val objectMap = LinkedHashMap<String, ObjectDef>()
        val resources = root.firstDirectChild("resources")
        if (resources != null) {
            resources.directChildren("object").forEach { objectElement ->
                val id = objectElement.getAttribute("id")
                if (id.isBlank()) return@forEach

                val meshElement = objectElement.firstDirectChild("mesh")
                val mesh = meshElement?.let(::parseMesh)
                val components = objectElement.firstDirectChild("components")
                    ?.directChildren("component")
                    ?.mapNotNull { component ->
                        val childId = component.getAttribute("objectid")
                        if (childId.isBlank()) null
                        else ComponentDef(childId, Transform.parse(component.getAttribute("transform")))
                    }
                    ?: emptyList()

                objectMap[id] = ObjectDef(id, mesh, components)
            }
        }

        require(objectMap.isNotEmpty()) { "3MF contains no readable objects." }

        val buildItems = root.firstDirectChild("build")
            ?.directChildren("item")
            ?.mapNotNull { item ->
                val objectId = item.getAttribute("objectid")
                if (objectId.isBlank()) null
                else BuildItem(objectId, Transform.parse(item.getAttribute("transform")))
            }
            ?: emptyList()

        val effectiveBuildItems = if (buildItems.isNotEmpty()) {
            buildItems
        } else {
            val referenced = objectMap.values.flatMap { it.components.map(ComponentDef::objectId) }.toSet()
            objectMap.keys.filterNot { it in referenced }.map { BuildItem(it, Transform.IDENTITY) }
        }

        require(effectiveBuildItems.isNotEmpty()) { "3MF contains no buildable objects." }

        var totalVertices = 0L
        var totalTriangles = 0L
        effectiveBuildItems.forEach { item ->
            val counts = countObject(item.objectId, objectMap, LinkedHashSet())
            totalVertices += counts.vertices
            totalTriangles += counts.triangles
        }

        require(totalVertices > 0L && totalTriangles > 0L) {
            "3MF contains no directly readable triangle mesh."
        }
        require(totalVertices <= Int.MAX_VALUE / 3L && totalTriangles <= Int.MAX_VALUE / 3L) {
            "3MF is too large for this Android viewer."
        }

        val outPositions = FloatArray(totalVertices.toInt() * 3)
        val outIndices = IntArray(totalTriangles.toInt() * 3)
        var vertexCursor = 0
        var indexCursor = 0

        fun appendObject(objectId: String, transforms: List<Transform>, stack: LinkedHashSet<String>) {
            require(stack.add(objectId)) { "3MF component cycle detected at object $objectId." }
            val obj = objectMap[objectId] ?: error("3MF references missing object $objectId.")

            obj.mesh?.let { mesh ->
                val vertexBase = vertexCursor / 3
                var p = 0
                while (p < mesh.positions.size) {
                    var point = floatArrayOf(
                        mesh.positions[p],
                        mesh.positions[p + 1],
                        mesh.positions[p + 2]
                    )
                    transforms.forEach { t ->
                        point = t.apply(point[0], point[1], point[2])
                    }
                    outPositions[vertexCursor++] = point[0]
                    outPositions[vertexCursor++] = point[1]
                    outPositions[vertexCursor++] = point[2]
                    p += 3
                }

                mesh.indices.forEach { localIndex ->
                    require(localIndex in 0 until mesh.positions.size / 3) {
                        "3MF triangle references vertex $localIndex outside its object."
                    }
                    outIndices[indexCursor++] = vertexBase + localIndex
                }
            }

            obj.components.forEach { component ->
                appendObject(
                    component.objectId,
                    listOf(component.transform) + transforms,
                    LinkedHashSet(stack)
                )
            }
        }

        effectiveBuildItems.forEach { item ->
            appendObject(item.objectId, listOf(item.transform), LinkedHashSet())
        }

        require(vertexCursor == outPositions.size && indexCursor == outIndices.size) {
            "3MF object count changed while flattening the build."
        }

        return MeshData(name, outPositions, null, outIndices, unit).withGeneratedNormals()
    }

    private fun countObject(
        objectId: String,
        objectMap: Map<String, ObjectDef>,
        stack: LinkedHashSet<String>
    ): Counts {
        require(stack.add(objectId)) { "3MF component cycle detected at object $objectId." }
        val obj = objectMap[objectId] ?: error("3MF references missing object $objectId.")

        var vertices = obj.mesh?.positions?.size?.div(3) ?: 0
        var triangles = obj.mesh?.indices?.size?.div(3) ?: 0

        obj.components.forEach { component ->
            val child = countObject(component.objectId, objectMap, LinkedHashSet(stack))
            vertices = Math.addExact(vertices, child.vertices)
            triangles = Math.addExact(triangles, child.triangles)
        }
        return Counts(vertices, triangles)
    }

    private fun parseMesh(mesh: Element): MeshDef {
        val verticesElement = mesh.firstDirectChild("vertices")
            ?: return MeshDef(FloatArray(0), IntArray(0))
        val vertexElements = verticesElement.directChildren("vertex")
        val positions = FloatArray(vertexElements.size * 3)
        vertexElements.forEachIndexed { i, vertex ->
            positions[i * 3] = vertex.requiredFloat("x")
            positions[i * 3 + 1] = vertex.requiredFloat("y")
            positions[i * 3 + 2] = vertex.requiredFloat("z")
        }

        val trianglesElement = mesh.firstDirectChild("triangles")
            ?: return MeshDef(positions, IntArray(0))
        val triangleElements = trianglesElement.directChildren("triangle")
        val indices = IntArray(triangleElements.size * 3)
        triangleElements.forEachIndexed { i, triangle ->
            indices[i * 3] = triangle.requiredInt("v1")
            indices[i * 3 + 1] = triangle.requiredInt("v2")
            indices[i * 3 + 2] = triangle.requiredInt("v3")
        }
        return MeshDef(positions, indices)
    }

    private fun findPrimaryModel(bytes: ByteArray): ByteArray {
        var firstModel: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val lower = entry.name.lowercase(Locale.US)
                if (!lower.endsWith(".model")) continue
                val data = zip.readBytes()
                if (firstModel == null) firstModel = data
                if (lower == "3d/3dmodel.model" || lower.endsWith("/3dmodel.model")) return data
            }
        }
        return firstModel ?: error("3MF package contains no .model document.")
    }

    private fun Element.requiredFloat(name: String): Float =
        getAttribute(name).toFloatOrNull()
            ?: error("3MF is missing numeric $name.")

    private fun Element.requiredInt(name: String): Int =
        getAttribute(name).toIntOrNull()
            ?: error("3MF is missing integer $name.")

    private fun Element.firstDirectChild(localName: String): Element? =
        directChildren(localName).firstOrNull()

    private fun Element.directChildren(localName: String): List<Element> {
        val out = ArrayList<Element>()
        var child: Node? = firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.tagName.substringAfter(':')
                if (name == localName) out += child
            }
            child = child.nextSibling
        }
        return out
    }
}
