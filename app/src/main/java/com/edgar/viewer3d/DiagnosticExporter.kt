package com.edgar.viewer3d

import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
    val byteSize: Long,
    val contentUri: String? = null,
    val filePath: String? = null
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
        val temp = File(
            context.cacheDir,
            "3DViewer_Diagnostics_" +
                DiagnosticLogger.sessionId.take(8) + "_" +
                System.currentTimeMillis() + ".zip"
        )
        var savedDestination: DiagnosticExportResult? = null

        try {
            // First pass creates and verifies a complete diagnostic archive.
            buildArchive(
                target = temp,
                exportResult = null
            )
            require(temp.exists() && temp.length() > 0L) {
                "Diagnostic ZIP was not created or is empty."
            }

            savedDestination = saveToDownloads(context, temp)
            DiagnosticLogger.event(
                "DIAGNOSTIC",
                "EXPORT_ARCHIVE_PERSISTED",
                mapOf(
                    "location" to savedDestination.location,
                    "bytes" to savedDestination.byteSize
                ),
                operationId = operationId
            )

            // Second pass makes the exported package self-describing. The final
            // archive is only kept if this rewrite succeeds and verifies.
            val completionRecord = JSONObject()
                .put("status", "COMPLETED")
                .put("operationId", operationId)
                .put("sessionId", DiagnosticLogger.sessionId)
                .put("location", savedDestination.location)
                .put("completedUtc", Instant.now().toString())
                .put(
                    "durationMsBeforeFinalRewrite",
                    android.os.SystemClock.elapsedRealtime() - started
                )

            buildArchive(
                target = temp,
                exportResult = completionRecord
            )
            require(temp.exists() && temp.length() > 0L) {
                "Final diagnostic ZIP was not created or is empty."
            }

            val finalResult = rewriteSavedDestination(
                context = context,
                destination = savedDestination,
                source = temp
            )

            val duration = android.os.SystemClock.elapsedRealtime() - started
            DiagnosticLogger.event(
                "DIAGNOSTIC",
                "EXPORT_COMPLETED",
                mapOf(
                    "location" to finalResult.location,
                    "bytes" to finalResult.byteSize,
                    "durationMs" to duration,
                    "selfContainedExportResult" to true
                ),
                operationId = operationId
            )

            temp.delete()
            return finalResult
        } catch (t: Throwable) {
            savedDestination?.let {
                runCatching { deleteSavedDestination(context, it) }
            }
            DiagnosticLogger.error(
                module = "DiagnosticExporter",
                operation = "EXPORT_DIAGNOSTICS",
                throwable = t,
                details = mapOf(
                    "partialDestinationRemoved" to (savedDestination != null)
                ),
                operationId = operationId
            )
            temp.delete()
            throw t
        }
    }

    private fun buildArchive(
        target: File,
        exportResult: JSONObject?
    ) {
        if (target.exists()) target.delete()

        ZipOutputStream(FileOutputStream(target)).use { zip ->
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
                    appendLine()
                    appendLine(
                        "When present, export_result.json is the self-contained result " +
                            "of this export operation."
                    )
                }
            )

            addText(zip, "summary.txt", buildSummary(exportResult != null))
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
            addFileIfPresent(
                zip,
                "events.jsonl",
                DiagnosticLogger.sessionEventsFile()
            )

            val guidedJson = GuidedTestController.resultsJson()
            addText(
                zip,
                "guided_test_results.json",
                guidedJson.toString(2)
            )
            addText(
                zip,
                "guided_test_results.txt",
                GuidedTestController.summaryText()
            )

            if (exportResult != null) {
                addText(
                    zip,
                    "export_result.json",
                    exportResult.toString(2)
                )
            }

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
    }

    private fun buildSummary(hasExportResult: Boolean): String {
        val errors = DiagnosticLogger.sessionErrorsFile()
        return buildString {
            appendLine("Android 3D Viewer Diagnostics")
            appendLine("Generated UTC: " + Instant.now().toString())
            appendLine("App version: " + DiagnosticLogger.versionName())
            appendLine("Build code: " + DiagnosticLogger.versionCode())
            appendLine("Session ID: " + DiagnosticLogger.sessionId)
            appendLine("Session label: " + DiagnosticLogger.sessionLabel)
            appendLine(
                "Session started UTC: " +
                    DiagnosticLogger.sessionStartedUtc()
            )
            appendLine(
                "Active/last test ID: " +
                    GuidedTestController.resultsJson().optString("testId")
            )
            appendLine(
                "Guided test result: " +
                    GuidedTestController.overallStatus().name
            )
            appendLine(
                "Errors file present: " +
                    (errors.exists() && errors.length() > 0L)
            )
            appendLine()
            appendLine("Files in this package:")
            appendLine("- README.txt")
            appendLine("- summary.txt")
            appendLine("- device_app_info.txt")
            appendLine("- input_info.txt")
            appendLine("- events.jsonl")
            appendLine("- guided_test_results.json")
            appendLine("- guided_test_results.txt")
            if (hasExportResult) {
                appendLine("- export_result.json")
            }
            if (errors.exists() && errors.length() > 0L) {
                appendLine("- errors.txt")
            }
            val root = DiagnosticLogger.diagnosticsRoot()
            if (File(root, "previous_crash.json").exists()) {
                appendLine("- previous_crash.json")
            }
            if (
                File(
                    root,
                    "previous_crashed_session_events.jsonl"
                ).exists()
            ) {
                appendLine(
                    "- previous_crashed_session_events.jsonl"
                )
            }
            appendLine()
            append(GuidedTestController.summaryText())
        }
    }

    private fun saveToDownloads(
        context: Context,
        source: File
    ): DiagnosticExportResult {
        val fileName = source.name

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                        "/3DViewerDiagnostics"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: error(
                "MediaStore could not create the diagnostic ZIP."
            )

            try {
                context.contentResolver.openOutputStream(
                    uri,
                    "wt"
                )?.use { output ->
                    FileInputStream(source).use { input ->
                        input.copyTo(output)
                    }
                } ?: error("Could not write the diagnostic ZIP.")

                val size = contentLength(context, uri)
                require(size > 0L) {
                    "Diagnostic ZIP write could not be verified."
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(
                    uri,
                    values,
                    null,
                    null
                )

                return DiagnosticExportResult(
                    location =
                        "Downloads/3DViewerDiagnostics/" + fileName,
                    byteSize = size,
                    contentUri = uri.toString()
                )
            } catch (t: Throwable) {
                runCatching {
                    context.contentResolver.delete(
                        uri,
                        null,
                        null
                    )
                }
                throw t
            }
        }

        val dir = File(
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ),
            "3DViewerDiagnostics"
        ).apply { mkdirs() }
        val output = File(dir, fileName)
        source.copyTo(output, overwrite = true)
        require(
            output.length() == source.length() &&
                output.length() > 0L
        ) {
            "Diagnostic ZIP verification failed."
        }
        return DiagnosticExportResult(
            location = output.absolutePath,
            byteSize = output.length(),
            filePath = output.absolutePath
        )
    }

    private fun rewriteSavedDestination(
        context: Context,
        destination: DiagnosticExportResult,
        source: File
    ): DiagnosticExportResult {
        destination.contentUri?.let { uriText ->
            val uri = Uri.parse(uriText)
            context.contentResolver.openOutputStream(
                uri,
                "wt"
            )?.use { output ->
                FileInputStream(source).use { input ->
                    input.copyTo(output)
                }
            } ?: error(
                "Could not finalize the diagnostic ZIP."
            )

            val size = contentLength(context, uri)
            require(size > 0L) {
                "Final diagnostic ZIP verification failed."
            }
            return destination.copy(byteSize = size)
        }

        destination.filePath?.let { path ->
            val output = File(path)
            source.copyTo(output, overwrite = true)
            require(
                output.exists() &&
                    output.length() == source.length() &&
                    output.length() > 0L
            ) {
                "Final diagnostic ZIP verification failed."
            }
            return destination.copy(byteSize = output.length())
        }

        error("Diagnostic export destination is unavailable.")
    }

    private fun deleteSavedDestination(
        context: Context,
        destination: DiagnosticExportResult
    ) {
        destination.contentUri?.let { uriText ->
            context.contentResolver.delete(
                Uri.parse(uriText),
                null,
                null
            )
            return
        }
        destination.filePath?.let { path ->
            File(path).delete()
        }
    }

    private fun contentLength(
        context: Context,
        uri: Uri
    ): Long {
        return context.contentResolver.openFileDescriptor(
            uri,
            "r"
        )?.use { descriptor ->
            descriptor.statSize
        } ?: -1L
    }

    private fun addText(
        zip: ZipOutputStream,
        name: String,
        text: String
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun addFileIfPresent(
        zip: ZipOutputStream,
        name: String,
        file: File
    ) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).use { input ->
            input.copyTo(zip)
        }
        zip.closeEntry()
    }
}
