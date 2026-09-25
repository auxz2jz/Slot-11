package com.edgar.viewer3d

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

object AmfParser {
    fun parse(name: String, bytes: ByteArray): MeshData {
        val xmlBytes = unpackIfNeeded(bytes)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") } catch (_: Exception) {}
            try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") } catch (_: Exception) {}
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val root = doc.documentElement
        require((root.localName ?: root.tagName).equals("amf", true)) { "Invalid AMF document." }
        val unit = root.getAttribute("unit").ifBlank { "millimeter" }

        data class ObjectMesh(val positions: FloatArray, val indices: IntArray)

        val meshes = ArrayList<ObjectMesh>()
        root.directChildren("object").forEach { objectElement ->
            val mesh = objectElement.firstDirectChild("mesh") ?: return@forEach
            val vertices = mesh.firstDirectChild("vertices")
                ?.directChildren("vertex")
                ?: emptyList()
            if (vertices.isEmpty()) return@forEach

            val positions = FloatArray(vertices.size * 3)
            vertices.forEachIndexed { i, vertex ->
                val coords = vertex.firstDirectChild("coordinates")
                    ?: error("AMF vertex is missing coordinates.")
                positions[i * 3] = coords.childText("x").toFloat()
                positions[i * 3 + 1] = coords.childText("y").toFloat()
                positions[i * 3 + 2] = coords.childText("z").toFloat()
            }

            val indexList = ArrayList<Int>()
            mesh.directChildren("volume").forEach { volume ->
                volume.directChildren("triangle").forEach { triangle ->
                    indexList += triangle.childText("v1").toInt()
                    indexList += triangle.childText("v2").toInt()
                    indexList += triangle.childText("v3").toInt()
                }
            }

            if (indexList.isNotEmpty()) {
                val indices = indexList.toIntArray()
                require(indices.all { it in vertices.indices }) {
                    "AMF triangle references a vertex outside its object."
                }
                meshes += ObjectMesh(positions, indices)
            }
        }

        require(meshes.isNotEmpty()) { "AMF contains no directly readable triangle meshes." }

        val totalVertices = meshes.sumOf { it.positions.size / 3 }
        val totalIndices = meshes.sumOf { it.indices.size }
        val outPositions = FloatArray(totalVertices * 3)
        val outIndices = IntArray(totalIndices)

        var pCursor = 0
        var iCursor = 0
        meshes.forEach { mesh ->
            val base = pCursor / 3
            mesh.positions.copyInto(outPositions, pCursor)
            pCursor += mesh.positions.size
            mesh.indices.forEach { outIndices[iCursor++] = base + it }
        }

        return MeshData(name, outPositions, null, outIndices, unit).withGeneratedNormals()
    }

    private fun unpackIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
            return bytes
        }
        var firstXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val lower = entry.name.lowercase(Locale.US)
                if (lower.endsWith(".amf")) return zip.readBytes()
                if (firstXml == null && lower.endsWith(".xml")) firstXml = zip.readBytes()
            }
        }
        return firstXml ?: error("Compressed AMF contains no AMF/XML model.")
    }

    private fun Element.childText(localName: String): String =
        firstDirectChild(localName)?.textContent?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("AMF is missing $localName.")

    private fun Element.firstDirectChild(localName: String): Element? =
        directChildren(localName).firstOrNull()

    private fun Element.directChildren(localName: String): List<Element> {
        val out = ArrayList<Element>()
        var child: Node? = firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.tagName.substringAfter(':')
                if (name.equals(localName, true)) out += child
            }
            child = child.nextSibling
        }
        return out
    }
}
