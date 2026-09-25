package com.edgar.viewer3d

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object X3dParser {
    private data class Transform(
        val translation: FloatArray = floatArrayOf(0f, 0f, 0f),
        val rotation: FloatArray = floatArrayOf(0f, 0f, 1f, 0f),
        val scale: FloatArray = floatArrayOf(1f, 1f, 1f),
        val center: FloatArray = floatArrayOf(0f, 0f, 0f)
    ) {
        fun apply(x0: Float, y0: Float, z0: Float): FloatArray {
            var x = x0 - center[0]
            var y = y0 - center[1]
            var z = z0 - center[2]

            x *= scale[0]
            y *= scale[1]
            z *= scale[2]

            val ax0 = rotation[0]
            val ay0 = rotation[1]
            val az0 = rotation[2]
            val angle = rotation[3]
            val len = sqrt(ax0 * ax0 + ay0 * ay0 + az0 * az0)
            if (len > 0.000001f && angle != 0f) {
                val ax = ax0 / len
                val ay = ay0 / len
                val az = az0 / len
                val c = cos(angle)
                val s = sin(angle)
                val dot = ax * x + ay * y + az * z
                val rx = x * c + (ay * z - az * y) * s + ax * dot * (1f - c)
                val ry = y * c + (az * x - ax * z) * s + ay * dot * (1f - c)
                val rz = z * c + (ax * y - ay * x) * s + az * dot * (1f - c)
                x = rx; y = ry; z = rz
            }

            return floatArrayOf(
                x + center[0] + translation[0],
                y + center[1] + translation[1],
                z + center[2] + translation[2]
            )
        }
    }

    fun parse(name: String, bytes: ByteArray): MeshData {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val positions = ArrayList<Float>()
        val indices = ArrayList<Int>()

        fun appendGeometry(geometry: Element, transforms: List<Transform>) {
            val coord = geometry.firstDescendant("Coordinate") ?: return
            val pointValues = parseNumbers(coord.getAttribute("point"))
            if (pointValues.size < 9 || pointValues.size % 3 != 0) return

            val base = positions.size / 3
            var i = 0
            while (i < pointValues.size) {
                var p = floatArrayOf(pointValues[i], pointValues[i + 1], pointValues[i + 2])
                transforms.forEach { t -> p = t.apply(p[0], p[1], p[2]) }
                positions += p[0]; positions += p[1]; positions += p[2]
                i += 3
            }

            val localVertexCount = pointValues.size / 3
            when (geometry.localTag()) {
                "IndexedTriangleSet" -> {
                    val raw = parseInts(geometry.getAttribute("index"))
                    for (j in raw.indices step 3) {
                        if (j + 2 >= raw.size) break
                        val a = raw[j]; val b = raw[j + 1]; val c = raw[j + 2]
                        if (a in 0 until localVertexCount && b in 0 until localVertexCount && c in 0 until localVertexCount) {
                            indices += base + a; indices += base + b; indices += base + c
                        }
                    }
                }
                "TriangleSet" -> {
                    for (v in 0 until localVertexCount) indices += base + v
                }
                "IndexedFaceSet" -> {
                    val raw = parseInts(geometry.getAttribute("coordIndex"))
                    val face = ArrayList<Int>()
                    fun flush() {
                        if (face.size >= 3 && face.all { it in 0 until localVertexCount }) {
                            for (k in 1 until face.size - 1) {
                                indices += base + face[0]
                                indices += base + face[k]
                                indices += base + face[k + 1]
                            }
                        }
                        face.clear()
                    }
                    raw.forEach { value ->
                        if (value == -1) flush() else face += value
                    }
                    flush()
                }
            }
        }

        fun visit(element: Element, transforms: List<Transform>) {
            val local = element.localTag()
            val nextTransforms = if (local == "Transform") {
                listOf(parseTransform(element)) + transforms
            } else transforms

            if (local in setOf("IndexedFaceSet", "IndexedTriangleSet", "TriangleSet")) {
                appendGeometry(element, nextTransforms)
                return
            }

            var child: Node? = element.firstChild
            while (child != null) {
                if (child is Element) visit(child, nextTransforms)
                child = child.nextSibling
            }
        }

        visit(doc.documentElement, emptyList())
        require(indices.size >= 3 && positions.size >= 9) { "X3D contains no supported triangle geometry." }

        return MeshData(
            name,
            positions.toFloatArray(),
            null,
            indices.toIntArray()
        ).withGeneratedNormals()
    }

    private fun parseTransform(element: Element): Transform = Transform(
        translation = parseVector(element.getAttribute("translation"), floatArrayOf(0f, 0f, 0f), 3),
        rotation = parseVector(element.getAttribute("rotation"), floatArrayOf(0f, 0f, 1f, 0f), 4),
        scale = parseVector(element.getAttribute("scale"), floatArrayOf(1f, 1f, 1f), 3),
        center = parseVector(element.getAttribute("center"), floatArrayOf(0f, 0f, 0f), 3)
    )

    private fun parseVector(raw: String, fallback: FloatArray, count: Int): FloatArray {
        val values = parseNumbers(raw)
        return if (values.size >= count) values.take(count).toFloatArray() else fallback
    }

    private fun parseNumbers(raw: String): List<Float> =
        raw.trim()
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toFloatOrNull() }

    private fun parseInts(raw: String): List<Int> =
        raw.trim()
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }

    private fun Element.localTag(): String = localName ?: tagName.substringAfter(':')

    private fun Element.firstDescendant(localName: String): Element? {
        if (localTag() == localName) return this
        var child: Node? = firstChild
        while (child != null) {
            if (child is Element) {
                val found = child.firstDescendant(localName)
                if (found != null) return found
            }
            child = child.nextSibling
        }
        return null
    }
}
