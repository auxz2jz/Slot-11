package com.edgar.viewer3d

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object StlParser {
    fun parse(name: String, bytes: ByteArray): MeshData {
        if (bytes.size >= 84) {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(80)
            val count = bb.int.toLong() and 0xffffffffL
            val expected = 84L + count * 50L
            if (count > 0 && expected <= bytes.size.toLong()) return parseBinary(name, bytes, count.toInt())
        }
        return parseAscii(name, bytes.toString(Charsets.UTF_8))
    }

    private fun parseBinary(name: String, bytes: ByteArray, count: Int): MeshData {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(84)
        val positions = FloatArray(count * 9)
        val normals = FloatArray(count * 9)
        val indices = IntArray(count * 3) { it }
        var p = 0
        repeat(count) {
            val nx = bb.float; val ny = bb.float; val nz = bb.float
            repeat(3) {
                positions[p] = bb.float; positions[p + 1] = bb.float; positions[p + 2] = bb.float
                normals[p] = nx; normals[p + 1] = ny; normals[p + 2] = nz
                p += 3
            }
            bb.short
        }
        return MeshData(name, positions, normals, indices).withGeneratedNormals()
    }

    private fun parseAscii(name: String, text: String): MeshData {
        val values = ArrayList<Float>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("vertex ", ignoreCase = true)) {
                val p = line.split(Regex("\\s+"))
                if (p.size >= 4) {
                    values += p[1].toFloat(); values += p[2].toFloat(); values += p[3].toFloat()
                }
            }
        }
        require(values.size >= 9 && values.size % 9 == 0) { "Invalid or unsupported ASCII STL." }
        val positions = values.toFloatArray()
        return MeshData(name, positions, null, IntArray(positions.size / 3) { it }).withGeneratedNormals()
    }
}

object ObjParser {
    private data class FaceRef(val v: Int, val n: Int?)

    fun parse(name: String, bytes: ByteArray): MeshData {
        val sourcePos = ArrayList<FloatArray>()
        val sourceNorm = ArrayList<FloatArray>()
        val outPos = ArrayList<Float>()
        val outNorm = ArrayList<Float>()
        val outIdx = ArrayList<Int>()
        var everyNormal = true

        fun fixIndex(raw: Int, size: Int) = if (raw > 0) raw - 1 else size + raw
        fun parseRef(token: String): FaceRef {
            val p = token.split("/")
            val vi = fixIndex(p[0].toInt(), sourcePos.size)
            val ni = if (p.size >= 3 && p[2].isNotBlank()) fixIndex(p[2].toInt(), sourceNorm.size) else null
            return FaceRef(vi, ni)
        }
        fun emit(ref: FaceRef) {
            val v = sourcePos[ref.v]
            outPos += v[0]; outPos += v[1]; outPos += v[2]
            val n = ref.n?.let { sourceNorm.getOrNull(it) }
            if (n != null) {
                outNorm += n[0]; outNorm += n[1]; outNorm += n[2]
            } else {
                everyNormal = false
                outNorm += 0f; outNorm += 0f; outNorm += 0f
            }
            outIdx += outIdx.size
        }

        bytes.toString(Charsets.UTF_8).lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("v ") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) sourcePos += floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
                }
                line.startsWith("vn ") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) sourceNorm += floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
                }
                line.startsWith("f ") -> {
                    val refs = line.substring(2).trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map(::parseRef)
                    if (refs.size >= 3) for (i in 1 until refs.size - 1) {
                        emit(refs[0]); emit(refs[i]); emit(refs[i + 1])
                    }
                }
            }
        }
        require(outIdx.size >= 3) { "OBJ contains no triangle/polygon faces." }
        return MeshData(
            name, outPos.toFloatArray(),
            if (everyNormal) outNorm.toFloatArray() else null,
            outIdx.toIntArray()
        ).withGeneratedNormals()
    }
}

object PlyParser {
    fun parse(name: String, bytes: ByteArray): MeshData {
        val text = bytes.toString(Charsets.UTF_8)
        val end = text.indexOf("end_header")
        require(end >= 0) { "PLY header is missing end_header." }
        val header = text.substring(0, end)
        require(header.lineSequence().any { it.trim().equals("format ascii 1.0", true) }) {
            "Binary PLY is not supported yet; v0.1.0 supports ASCII PLY."
        }

        var vertexCount = 0
        var faceCount = 0
        val properties = ArrayList<String>()
        var inVertex = false
        header.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("element vertex ") -> { vertexCount = line.substringAfterLast(' ').toInt(); inVertex = true }
                line.startsWith("element face ") -> { faceCount = line.substringAfterLast(' ').toInt(); inVertex = false }
                line.startsWith("element ") -> inVertex = false
                inVertex && line.startsWith("property ") -> properties += line.substringAfterLast(' ')
            }
        }
        require(vertexCount > 0) { "PLY contains no vertices." }
        val xI = properties.indexOf("x"); val yI = properties.indexOf("y"); val zI = properties.indexOf("z")
        val nxI = properties.indexOf("nx"); val nyI = properties.indexOf("ny"); val nzI = properties.indexOf("nz")
        require(xI >= 0 && yI >= 0 && zI >= 0) { "PLY is missing x/y/z vertex properties." }

        val dataStart = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
        val lines = text.substring(dataStart).lineSequence().filter { it.isNotBlank() }.iterator()
        val pos = FloatArray(vertexCount * 3)
        val norm = if (nxI >= 0 && nyI >= 0 && nzI >= 0) FloatArray(vertexCount * 3) else null
        repeat(vertexCount) { i ->
            require(lines.hasNext()) { "PLY ended inside vertex table." }
            val p = lines.next().trim().split(Regex("\\s+"))
            pos[i * 3] = p[xI].toFloat(); pos[i * 3 + 1] = p[yI].toFloat(); pos[i * 3 + 2] = p[zI].toFloat()
            if (norm != null) {
                norm[i * 3] = p[nxI].toFloat(); norm[i * 3 + 1] = p[nyI].toFloat(); norm[i * 3 + 2] = p[nzI].toFloat()
            }
        }
        val idx = ArrayList<Int>()
        repeat(faceCount) {
            if (!lines.hasNext()) return@repeat
            val p = lines.next().trim().split(Regex("\\s+"))
            if (p.isEmpty()) return@repeat
            val n = p[0].toInt()
            if (n >= 3 && p.size >= n + 1) {
                val face = IntArray(n) { p[it + 1].toInt() }
                for (i in 1 until n - 1) { idx += face[0]; idx += face[i]; idx += face[i + 1] }
            }
        }
        require(idx.size >= 3) { "PLY contains no polygon faces." }
        return MeshData(name, pos, norm, idx.toIntArray()).withGeneratedNormals()
    }
}

object OffParser {
    fun parse(name: String, bytes: ByteArray): MeshData {
        val clean = bytes.toString(Charsets.UTF_8).lineSequence()
            .map { it.substringBefore('#').trim() }.filter { it.isNotBlank() }.toList()
        require(clean.isNotEmpty() && (clean[0] == "OFF" || clean[0] == "COFF")) { "Invalid OFF file." }
        val counts = clean[1].split(Regex("\\s+"))
        val vertexCount = counts[0].toInt()
        val faceCount = counts[1].toInt()
        val pos = FloatArray(vertexCount * 3)
        for (i in 0 until vertexCount) {
            val p = clean[i + 2].split(Regex("\\s+"))
            pos[i * 3] = p[0].toFloat(); pos[i * 3 + 1] = p[1].toFloat(); pos[i * 3 + 2] = p[2].toFloat()
        }
        val idx = ArrayList<Int>()
        val faceStart = 2 + vertexCount
        for (f in 0 until faceCount) {
            val p = clean[faceStart + f].split(Regex("\\s+"))
            val n = p[0].toInt()
            if (n >= 3) {
                val face = IntArray(n) { p[it + 1].toInt() }
                for (i in 1 until n - 1) { idx += face[0]; idx += face[i]; idx += face[i + 1] }
            }
        }
        require(idx.size >= 3) { "OFF contains no polygon faces." }
        return MeshData(name, pos, null, idx.toIntArray()).withGeneratedNormals()
    }
}

object ThreeMfParser {
    fun parse(name: String, bytes: ByteArray): MeshData {
        var modelBytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.lowercase(Locale.US).endsWith(".model")) {
                    modelBytes = zip.readBytes()
                    if (entry.name.lowercase(Locale.US).contains("3d/")) break
                }
            }
        }
        val xml = modelBytes ?: error("3MF package contains no .model document.")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val unit = doc.documentElement.getAttribute("unit").ifBlank { "millimeter" }

        var vertices = doc.getElementsByTagNameNS("*", "vertex")
        if (vertices.length == 0) vertices = doc.getElementsByTagName("vertex")
        val pos = FloatArray(vertices.length * 3)
        for (i in 0 until vertices.length) {
            val e = vertices.item(i) as org.w3c.dom.Element
            pos[i * 3] = e.getAttribute("x").toFloat()
            pos[i * 3 + 1] = e.getAttribute("y").toFloat()
            pos[i * 3 + 2] = e.getAttribute("z").toFloat()
        }

        var triangles = doc.getElementsByTagNameNS("*", "triangle")
        if (triangles.length == 0) triangles = doc.getElementsByTagName("triangle")
        val idx = IntArray(triangles.length * 3)
        for (i in 0 until triangles.length) {
            val e = triangles.item(i) as org.w3c.dom.Element
            idx[i * 3] = e.getAttribute("v1").toInt()
            idx[i * 3 + 1] = e.getAttribute("v2").toInt()
            idx[i * 3 + 2] = e.getAttribute("v3").toInt()
        }
        require(pos.isNotEmpty() && idx.isNotEmpty()) { "3MF contains no directly readable triangle mesh." }
        return MeshData(name, pos, null, idx, unit).withGeneratedNormals()
    }
}
