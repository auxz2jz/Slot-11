package com.edgar.viewer3d

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DiagnosticExportResult(
    val location: String,
    val byteSize: Long
)

object DiagnosticExporter {
    fun export(context: Context): DiagnosticExportResult {
        val operationId = DiagnosticLogger.newId("diagnostic_export")
        DiagnosticLogger.event(
            "DIAGNOSTIC",
            "EXPORT_REQUESTED",
            mapOf("sessionId" to DiagnosticLogger.sessionId),
            operationId = operationId
        )

        val started = android.os.SystemClock.elapsedRealtime()
        try {
            val temp = File(
                context.cacheDir,
                "3DViewer_Diagnostics_" +
                    DiagnosticLogger.sessionId.take(8) + "_" +
                    System.currentTimeMillis() + ".zip"
            )

            ZipOutputStream(FileOutputStream(temp)).use { zip ->
                addText(
                    zip,
                    "README.txt",
                    buildString {
                        appendLine("Android 3D Viewer diagnostic package")
                        appendLine()
                        appendLine("Diagnostics remain local until the user explicitly exports them.")
                        appendLine("Original source 3D model contents are not included.")
                        appendLine("The package contains structured events, test results, errors,")
                        appendLine("safe input metadata and device/app information for debugging.")
                    }
                )

                addText(zip, "summary.txt", buildSummary())
                addText(
                    zip,
                    "device_app_info.txt",
                    DiagnosticLogger.environmentJson().toString(2)
                )
                addText(
                    zip,
                    "input_info.txt",
                    DiagnosticLogger.inputInfoJson().toString(2)
                )
                addFileIfPresent(zip, "events.jsonl", DiagnosticLogger.sessionEventsFile())

                val guidedJson = GuidedTestController.resultsJson()
                addText(zip, "guided_test_results.json", guidedJson.toString(2))
                addText(zip, "guided_test_results.txt", GuidedTestController.summaryText())

                val errors = DiagnosticLogger.sessionErrorsFile()
                if (errors.exists() && errors.length() > 0L) {
                    addFileIfPresent(zip, "errors.txt", errors)
                }

                val root = DiagnosticLogger.diagnosticsRoot()
                addFileIfPresent(
                    zip,
                    "previous_crash.json",
                    File(root, "previous_crash.json")
                )
                addFileIfPresent(
                    zip,
                    "previous_crashed_session_events.jsonl",
                    File(root, "previous_crashed_session_events.jsonl")
                )
            }

            require(temp.exists() && temp.length() > 0L) {
                "Diagnostic ZIP was not created or is empty."
            }

            val result = saveToDownloads(context, temp)
            val duration = android.os.SystemClock.elapsedRealtime() - started
            DiagnosticLogger.event(
                "DIAGNOSTIC",
                "EXPORT_COMPLETED",
                mapOf(
                    "location" to result.location,
                    "bytes" to result.byteSize,
                    "durationMs" to duration
                ),
                operationId = operationId
            )
            temp.delete()
            return result
        } catch (t: Throwable) {
            DiagnosticLogger.error(
                module = "DiagnosticExporter",
                operation = "EXPORT_DIAGNOSTICS",
                throwable = t,
                operationId = operationId
            )
            throw t
        }
    }

    private fun buildSummary(): String {
        val errors = DiagnosticLogger.sessionErrorsFile()
        return buildString {
            appendLine("Android 3D Viewer Diagnostics")
            appendLine("Generated UTC: " + Instant.now().toString())
            appendLine("App version: " + DiagnosticLogger.versionName())
            appendLine("Build code: " + DiagnosticLogger.versionCode())
            appendLine("Session ID: " + DiagnosticLogger.sessionId)
            appendLine("Session label: " + DiagnosticLogger.sessionLabel)
            appendLine("Session started UTC: " + DiagnosticLogger.sessionStartedUtc())
            appendLine("Active/last test ID: " + (GuidedTestController.resultsJson().optString("testId")))
            appendLine("Guided test result: " + GuidedTestController.overallStatus().name)
            appendLine("Errors file present: " + (errors.exists() && errors.length() > 0L))
            appendLine()
            appendLine("Files in this package:")
            appendLine("- README.txt")
            appendLine("- summary.txt")
            appendLine("- device_app_info.txt")
            appendLine("- input_info.txt")
            appendLine("- events.jsonl")
            appendLine("- guided_test_results.json")
            appendLine("- guided_test_results.txt")
            if (errors.exists() && errors.length() > 0L) appendLine("- errors.txt")
            val root = DiagnosticLogger.diagnosticsRoot()
            if (File(root, "previous_crash.json").exists()) appendLine("- previous_crash.json")
            if (File(root, "previous_crashed_session_events.jsonl").exists()) {
                appendLine("- previous_crashed_session_events.jsonl")
            }
            appendLine()
            append(GuidedTestController.summaryText())
        }
    }

    private fun saveToDownloads(context: Context, source: File): DiagnosticExportResult {
        val fileName = source.name
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/3DViewerDiagnostics"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("MediaStore could not create the diagnostic ZIP.")

            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output) }
                } ?: error("Could not write the diagnostic ZIP.")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return DiagnosticExportResult(
                    location = "Downloads/3DViewerDiagnostics/" + fileName,
                    byteSize = source.length()
                )
            } catch (t: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw t
            }
        }

        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "3DViewerDiagnostics"
        ).apply { mkdirs() }
        val output = File(dir, fileName)
        source.copyTo(output, overwrite = true)
        require(output.length() == source.length() && output.length() > 0L) {
            "Diagnostic ZIP verification failed."
        }
        return DiagnosticExportResult(output.absolutePath, output.length())
    }

    private fun addText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun addFileIfPresent(zip: ZipOutputStream, name: String, file: File) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }
}
