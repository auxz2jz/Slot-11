package com.edgar.viewer3d

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

/**
 * Central structured diagnostic logger for the Android 3D Viewer.
 *
 * Diagnostics remain local to the device until the user explicitly exports them.
 * The logger records semantic actions/results, not frame-by-frame noise.
 */
object DiagnosticLogger {
    private const val MAX_RECENT_EVENTS = 1000
    private const val MAX_RETAINED_SESSIONS = 20

    private val lock = Any()
    private val recentEvents = ArrayDeque<String>()

    private lateinit var appContext: Context
    private lateinit var rootDir: File
    private lateinit var sessionDir: File
    private lateinit var eventsFile: File
    private lateinit var errorsFile: File

    @Volatile
    private var initialized = false

    @Volatile
    var sessionId: String = ""
        private set

    @Volatile
    var sessionLabel: String = ""
        private set

    @Volatile
    var currentTestId: String? = null
        private set

    @Volatile
    var currentStepId: String? = null
        private set

    private var sessionStartedUtc: String = ""
    private var elapsedOriginMs: Long = 0L
    private var sequence: Long = 0L
    private var appVersionName: String = "unknown"
    private var appVersionCode: Long = -1L
    private var inputInfo = JSONObject()
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            rootDir = File(appContext.filesDir, "diagnostics").apply { mkdirs() }
            rotatePreviousCrashArtifacts()
            readAppVersion()
            startSessionLocked("app_session", null)
            installCrashHandlerLocked()
            initialized = true
        }
    }

    fun startSession(label: String, testId: String? = null): String {
        ensureInitialized()
        synchronized(lock) {
            return startSessionLocked(label, testId)
        }
    }

    fun newId(prefix: String): String = prefix + "_" + UUID.randomUUID().toString()

    fun setTestContext(testId: String?, stepId: String?) {
        ensureInitialized()
        synchronized(lock) {
            currentTestId = testId
            currentStepId = stepId
        }
    }

    fun clearTestContext() {
        setTestContext(null, null)
    }

    fun event(
        category: String,
        event: String,
        details: Map<String, Any?> = emptyMap(),
        requestId: String? = null,
        operationId: String? = null
    ): Long {
        ensureInitialized()
        synchronized(lock) {
            return appendEventLocked(category, event, details, requestId, operationId)
        }
    }

    fun warning(
        event: String,
        details: Map<String, Any?> = emptyMap(),
        requestId: String? = null,
        operationId: String? = null
    ): Long = event("WARNING", event, details, requestId, operationId)

    fun error(
        module: String,
        operation: String,
        throwable: Throwable,
        details: Map<String, Any?> = emptyMap(),
        requestId: String? = null,
        operationId: String? = null
    ): Long {
        ensureInitialized()
        synchronized(lock) {
            val merged = LinkedHashMap<String, Any?>()
            merged.putAll(details)
            merged["module"] = module
            merged["operation"] = operation
            merged["errorType"] = throwable.javaClass.name
            merged["message"] = throwable.message
            merged["stackTrace"] = Log.getStackTraceString(throwable)
            val seq = appendEventLocked("ERROR", operation + "_FAILED", merged, requestId, operationId)
            runCatching {
                errorsFile.appendText(
                    buildString {
                        append("sequence=").append(seq).append('\n')
                        append("timestampUtc=").append(Instant.now()).append('\n')
                        append("module=").append(module).append('\n')
                        append("operation=").append(operation).append('\n')
                        append("testId=").append(currentTestId ?: "").append('\n')
                        append("stepId=").append(currentStepId ?: "").append('\n')
                        append("type=").append(throwable.javaClass.name).append('\n')
                        append("message=").append(throwable.message ?: "").append('\n')
                        append(Log.getStackTraceString(throwable)).append('\n')
                        append("---\n")
                    }
                )
            }
            return seq
        }
    }

    fun setInputInfo(details: Map<String, Any?>) {
        ensureInitialized()
        synchronized(lock) {
            inputInfo = mapToJson(details)
            writeTextAtomic(File(sessionDir, "input_info.json"), inputInfo.toString(2))
            appendEventLocked("INPUT", "INPUT_METADATA_UPDATED", details, null, null)
        }
    }

    fun inputInfoJson(): JSONObject {
        ensureInitialized()
        synchronized(lock) {
            return JSONObject(inputInfo.toString())
        }
    }

    fun sessionEventsFile(): File {
        ensureInitialized()
        return eventsFile
    }

    fun sessionErrorsFile(): File {
        ensureInitialized()
        return errorsFile
    }

    fun diagnosticsRoot(): File {
        ensureInitialized()
        return rootDir
    }

    fun sessionDirectory(): File {
        ensureInitialized()
        return sessionDir
    }

    fun sessionStartedUtc(): String = sessionStartedUtc

    fun versionName(): String = appVersionName

    fun versionCode(): Long = appVersionCode

    fun recentEventLines(): List<String> {
        ensureInitialized()
        synchronized(lock) { return recentEvents.toList() }
    }

    fun environmentJson(): JSONObject {
        ensureInitialized()
        val metrics = appContext.resources.displayMetrics
        val memoryInfo = ActivityManager.MemoryInfo()
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(memoryInfo)

        return JSONObject()
            .put("appVersion", appVersionName)
            .put("buildCode", appVersionCode)
            .put("packageName", appContext.packageName)
            .put("androidRelease", Build.VERSION.RELEASE ?: "")
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER ?: "")
            .put("model", Build.MODEL ?: "")
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS?.toList() ?: emptyList<String>()))
            .put("cpuCount", Runtime.getRuntime().availableProcessors())
            .put("totalRamBytes", memoryInfo.totalMem)
            .put("displayWidthPixels", metrics.widthPixels)
            .put("displayHeightPixels", metrics.heightPixels)
            .put("displayDensity", metrics.density.toDouble())
            .put("locale", Locale.getDefault().toLanguageTag())
    }

    private fun startSessionLocked(label: String, testId: String?): String {
        sessionId = UUID.randomUUID().toString()
        sessionLabel = label
        sessionStartedUtc = Instant.now().toString()
        elapsedOriginMs = SystemClock.elapsedRealtime()
        sequence = 0L
        currentTestId = testId
        currentStepId = null
        inputInfo = JSONObject()
        recentEvents.clear()

        sessionDir = File(rootDir, "sessions/$sessionId").apply { mkdirs() }
        eventsFile = File(sessionDir, "events.jsonl")
        errorsFile = File(sessionDir, "errors.txt")
        eventsFile.writeText("")
        errorsFile.writeText("")
        writeTextAtomic(File(sessionDir, "input_info.json"), inputInfo.toString(2))

        appendEventLocked(
            "SESSION",
            "SESSION_STARTED",
            mapOf(
                "label" to label,
                "sessionStartedUtc" to sessionStartedUtc,
                "appVersion" to appVersionName,
                "buildCode" to appVersionCode,
                "testId" to testId
            ),
            null,
            null
        )
        appendEventLocked(
            "APP",
            "ENVIRONMENT_CAPTURED",
            jsonToMap(environmentJson()),
            null,
            null
        )

        cleanupOldSessionsLocked()
        return sessionId
    }

    private fun appendEventLocked(
        category: String,
        event: String,
        details: Map<String, Any?>,
        requestId: String?,
        operationId: String?
    ): Long {
        sequence += 1L
        val obj = JSONObject()
            .put("sessionId", sessionId)
            .put("sequence", sequence)
            .put("timestampUtc", Instant.now().toString())
            .put("elapsedMs", SystemClock.elapsedRealtime() - elapsedOriginMs)
            .put("category", category)
            .put("event", event)
            .put("appVersion", appVersionName)
            .put("buildCode", appVersionCode)
            .put("testId", currentTestId ?: JSONObject.NULL)
            .put("testStepId", currentStepId ?: JSONObject.NULL)
            .put("requestId", requestId ?: JSONObject.NULL)
            .put("operationId", operationId ?: JSONObject.NULL)
            .put("details", mapToJson(details))

        val line = obj.toString()
        eventsFile.appendText(line + "\n")
        recentEvents.addLast(line)
        while (recentEvents.size > MAX_RECENT_EVENTS) {
            recentEvents.removeFirst()
        }
        return sequence
    }

    private fun installCrashHandlerLocked() {
        if (previousUncaughtHandler != null) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        previousUncaughtHandler = previous
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashDetails = linkedMapOf<String, Any?>(
                    "threadName" to thread.name,
                    "threadId" to thread.id,
                    "exceptionType" to throwable.javaClass.name,
                    "message" to throwable.message,
                    "stackTrace" to Log.getStackTraceString(throwable),
                    "eventLogFile" to eventsFile.name,
                    "sessionLabel" to sessionLabel
                )
                error("fatal", "UNCAUGHT_EXCEPTION", throwable, crashDetails)

                val crash = JSONObject()
                    .put("sessionId", sessionId)
                    .put("timestampUtc", Instant.now().toString())
                    .put("testId", currentTestId ?: JSONObject.NULL)
                    .put("testStepId", currentStepId ?: JSONObject.NULL)
                    .put("threadName", thread.name)
                    .put("threadId", thread.id)
                    .put("exceptionType", throwable.javaClass.name)
                    .put("message", throwable.message ?: "")
                    .put("stackTrace", Log.getStackTraceString(throwable))
                    .put("eventLogFile", eventsFile.absolutePath)

                writeTextAtomic(File(rootDir, "last_crash.json"), crash.toString(2))
                runCatching {
                    eventsFile.copyTo(
                        File(rootDir, "last_crashed_session_events.jsonl"),
                        overwrite = true
                    )
                }
            } catch (_: Throwable) {
                // Never let diagnostic crash handling prevent Android's normal crash path.
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    Runtime.getRuntime().exit(10)
                }
            }
        }
    }

    private fun rotatePreviousCrashArtifacts() {
        val lastCrash = File(rootDir, "last_crash.json")
        if (lastCrash.exists()) {
            lastCrash.copyTo(File(rootDir, "previous_crash.json"), overwrite = true)
            lastCrash.delete()
        }
        val lastEvents = File(rootDir, "last_crashed_session_events.jsonl")
        if (lastEvents.exists()) {
            lastEvents.copyTo(
                File(rootDir, "previous_crashed_session_events.jsonl"),
                overwrite = true
            )
            lastEvents.delete()
        }
    }

    private fun readAppVersion() {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        appVersionName = info.versionName ?: "unknown"
        appVersionCode = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun cleanupOldSessionsLocked() {
        val parent = File(rootDir, "sessions")
        val dirs = parent.listFiles()?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        dirs.drop(MAX_RETAINED_SESSIONS).forEach { old ->
            if (old.absolutePath != sessionDir.absolutePath) {
                runCatching { old.deleteRecursively() }
            }
        }
    }

    private fun ensureInitialized() {
        check(initialized) { "DiagnosticLogger.initialize() must be called first." }
    }

    private fun writeTextAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        for ((key, value) in map) {
            obj.put(key, toJsonValue(value))
        }
        return obj
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is JSONObject, is JSONArray,
            is String, is Number, is Boolean -> value
            is Map<*, *> -> {
                val obj = JSONObject()
                value.forEach { (k, v) -> if (k != null) obj.put(k.toString(), toJsonValue(v)) }
                obj
            }
            is Iterable<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
            is Array<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
            is IntArray -> JSONArray().apply { value.forEach { put(it) } }
            is LongArray -> JSONArray().apply { value.forEach { put(it) } }
            is FloatArray -> JSONArray().apply { value.forEach { put(it.toDouble()) } }
            is DoubleArray -> JSONArray().apply { value.forEach { put(it) } }
            else -> value.toString()
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)
            result[key] = if (value == JSONObject.NULL) null else value
        }
        return result
    }
}
