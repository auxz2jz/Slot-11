package com.edgar.viewer3d

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

enum class GuidedStatus {
    PASS,
    FAIL,
    PARTIAL,
    BLOCKED,
    NOT_RUN
}

data class GuidedTestStep(
    val id: String,
    val title: String,
    val instruction: String,
    val expected: String,
    val requiredEvidence: List<String> = emptyList(),
    val visualConfirmationRequired: Boolean = true,
    val timeoutMs: Long? = null
)

data class GuidedTestDefinition(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<GuidedTestStep>
)

data class GuidedStepResult(
    val stepId: String,
    val status: GuidedStatus,
    val durationMs: Long,
    val message: String,
    val measuredValues: Map<String, String> = emptyMap()
)

/**
 * Permanent guided-testing framework for the actual Android 3D Viewer features.
 *
 * A user action is never sufficient evidence on its own. MainActivity and the
 * importer/render paths record objective evidence keys after real results occur.
 */
object GuidedTestController {
    private val definitions = linkedMapOf(
        "VIEWER_CORE" to GuidedTestDefinition(
            id = "VIEWER_CORE",
            title = "Core viewer",
            description = "Model load, touch navigation, Fit, Info and screenshot output.",
            steps = listOf(
                GuidedTestStep(
                    id = "CORE_LOAD",
                    title = "Load a model",
                    instruction = "Tap Open and choose a known-good supported model.",
                    expected = "The model is actually displayed and model statistics are available.",
                    requiredEvidence = listOf("MODEL_DISPLAYED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "CORE_ORBIT",
                    title = "One-finger orbit",
                    instruction = "Drag the model with one finger.",
                    expected = "The camera orientation changes and the model orbits smoothly.",
                    requiredEvidence = listOf("TOUCH_CAMERA_CHANGED_SINGLE"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "CORE_PAN_ZOOM",
                    title = "Two-finger pan / pinch",
                    instruction = "Use two fingers to pan and pinch zoom.",
                    expected = "The camera changes in response to the multi-touch gesture.",
                    requiredEvidence = listOf("TOUCH_CAMERA_CHANGED_MULTI"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "CORE_FIT",
                    title = "Fit",
                    instruction = "Tap Fit.",
                    expected = "The viewer resets to a usable framing.",
                    requiredEvidence = listOf("FIT_APPLIED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "CORE_INFO",
                    title = "Model information",
                    instruction = "Tap Info.",
                    expected = "The information dialog is populated from the current model state.",
                    requiredEvidence = listOf("INFO_PRESENTED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "CORE_SCREENSHOT",
                    title = "Screenshot",
                    instruction = "Tap Shot.",
                    expected = "A non-empty PNG is actually written to storage.",
                    requiredEvidence = listOf("SCREENSHOT_SAVED"),
                    visualConfirmationRequired = false
                )
            )
        ),
        "DISPLAY_CONTROLS" to GuidedTestDefinition(
            id = "DISPLAY_CONTROLS",
            title = "Display controls",
            description = "Quality, background, lighting, sun, auto-rotate and camera projection.",
            steps = listOf(
                GuidedTestStep(
                    id = "DISPLAY_QUALITY",
                    title = "Quality mode",
                    instruction = "Tap Quality once.",
                    expected = "The internal quality mode changes and Filament options are applied.",
                    requiredEvidence = listOf("QUALITY_CHANGED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "DISPLAY_BACKGROUND",
                    title = "Background",
                    instruction = "Open Display and choose a different background.",
                    expected = "The renderer clear color actually changes.",
                    requiredEvidence = listOf("BACKGROUND_CHANGED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "DISPLAY_LIGHTING",
                    title = "Lighting preset",
                    instruction = "Open Display and choose Soft, Studio or Bright lighting.",
                    expected = "Indirect and direct light intensities are applied.",
                    requiredEvidence = listOf("LIGHTING_CHANGED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "DISPLAY_SUN",
                    title = "Sun intensity",
                    instruction = "Open Display and choose a different Sun value.",
                    expected = "The Filament sun intensity setting changes.",
                    requiredEvidence = listOf("SUN_CHANGED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "DISPLAY_AUTOROTATE",
                    title = "Auto-rotate",
                    instruction = "Start Auto-rotate, change its speed once, reverse direction once, and let it move the model.",
                    expected = "The camera actually moves, the speed state changes, and the direction state reverses.",
                    requiredEvidence = listOf(
                        "AUTO_ROTATE_CAMERA_CHANGED",
                        "AUTO_ROTATE_SPEED_CHANGED",
                        "AUTO_ROTATE_DIRECTION_CHANGED"
                    ),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "DISPLAY_PROJECTION",
                    title = "Projection",
                    instruction = "Switch Perspective / Orthographic and test pinch zoom.",
                    expected = "Projection state changes, framing remains usable, and zoom visibly works.",
                    requiredEvidence = listOf("PROJECTION_CHANGED", "TOUCH_CAMERA_CHANGED_MULTI"),
                    visualConfirmationRequired = true
                )
            )
        ),
        "ANIMATION" to GuidedTestDefinition(
            id = "ANIMATION",
            title = "GLB / glTF animation",
            description = "Embedded animation availability, play/pause and next-clip behavior.",
            steps = listOf(
                GuidedTestStep(
                    id = "ANIM_LOAD",
                    title = "Load animated model",
                    instruction = "Open a GLB/glTF model that contains at least two animation clips.",
                    expected = "The displayed model reports two or more animations.",
                    requiredEvidence = listOf("ANIMATED_MODEL_LOADED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "ANIM_TOGGLE",
                    title = "Play / pause",
                    instruction = "Tap Anim.",
                    expected = "The internal playback state changes and the visible animation responds.",
                    requiredEvidence = listOf("ANIMATION_TOGGLED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "ANIM_NEXT",
                    title = "Next animation",
                    instruction = "Tap Next.",
                    expected = "The active animation index changes to another valid clip.",
                    requiredEvidence = listOf("ANIMATION_INDEX_CHANGED"),
                    visualConfirmationRequired = true
                )
            )
        ),
        "FORMAT_IMPORTS" to GuidedTestDefinition(
            id = "FORMAT_IMPORTS",
            title = "Supported format imports",
            description = "Regression test for each currently supported importer using the generated format test pack.",
            steps = listOf(
                GuidedTestStep(
                    id = "FORMAT_GLB",
                    title = "GLB",
                    instruction = "Open test_object.glb from the supported-format test pack.",
                    expected = "The GLB is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_GLB"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_GLTF",
                    title = "Embedded glTF",
                    instruction = "Open test_object.gltf.",
                    expected = "The embedded-resource glTF is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_GLTF"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_STL_BINARY",
                    title = "Binary STL",
                    instruction = "Open test_object_binary.stl.",
                    expected = "The binary STL parser path is used and the model is displayed.",
                    requiredEvidence = listOf(
                        "STL_BINARY_PARSED",
                        "MODEL_DISPLAYED_STL"
                    ),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_STL_ASCII",
                    title = "ASCII STL",
                    instruction = "Open test_object_ascii.stl.",
                    expected = "The ASCII STL parser path is used and the model is displayed.",
                    requiredEvidence = listOf(
                        "STL_ASCII_PARSED",
                        "MODEL_DISPLAYED_STL"
                    ),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_OBJ",
                    title = "OBJ",
                    instruction = "Open test_object.obj.",
                    expected = "OBJ geometry is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_OBJ"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_3MF",
                    title = "3MF",
                    instruction = "Open test_object.3mf.",
                    expected = "3MF geometry is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_3MF"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_AMF",
                    title = "AMF",
                    instruction = "Open test_object.amf.",
                    expected = "AMF geometry is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_AMF"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_X3D",
                    title = "X3D",
                    instruction = "Open test_object.x3d.",
                    expected = "X3D geometry is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_X3D"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_PLY",
                    title = "ASCII PLY",
                    instruction = "Open test_object.ply.",
                    expected = "ASCII PLY geometry is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_PLY"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_OFF",
                    title = "OFF",
                    instruction = "Open test_object.off.",
                    expected = "OFF geometry is actually displayed.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_OFF"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_STEP",
                    title = "STEP",
                    instruction = "Open test_object.step.",
                    expected = "OCCT imports and displays non-empty STEP geometry.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_STEP"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FORMAT_STP",
                    title = "STP",
                    instruction = "Open test_object.stp.",
                    expected = "OCCT imports and displays non-empty STP geometry through the same CAD path.",
                    requiredEvidence = listOf("MODEL_DISPLAYED_STP"),
                    visualConfirmationRequired = true
                )
            )
        ),
        "ROBUSTNESS" to GuidedTestDefinition(
            id = "ROBUSTNESS",
            title = "Robustness / recovery",
            description = "Model replacement, invalid-file failure handling, recovery and Open With.",
            steps = listOf(
                GuidedTestStep(
                    id = "ROBUST_REPLACE",
                    title = "Replace the current model",
                    instruction = "With one model already open, use Open to load a different supported model.",
                    expected = "The new model replaces the previous model and displays normally.",
                    requiredEvidence = listOf("MODEL_REPLACED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "ROBUST_INVALID",
                    title = "Invalid supported file",
                    instruction = "Select a deliberately invalid file that has a supported model extension.",
                    expected = "A readable load error is shown without crashing the app.",
                    requiredEvidence = listOf("MODEL_LOAD_FAILED_SAFELY"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "ROBUST_RECOVER",
                    title = "Recover after failure",
                    instruction = "After the invalid-file error, open a known-good supported model.",
                    expected = "The valid model displays successfully and the viewer remains usable.",
                    requiredEvidence = listOf("MODEL_DISPLAYED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "ROBUST_OPEN_WITH",
                    title = "Android Open With",
                    instruction = "From Android Files, use Open With to send a supported model to 3D Viewer.",
                    expected = "The app receives the external open request and displays the model.",
                    requiredEvidence = listOf(
                        "OPEN_WITH_RECEIVED",
                        "MODEL_DISPLAYED"
                    ),
                    visualConfirmationRequired = true
                )
            )
        ),
        "DIAGNOSTIC_SYSTEM" to GuidedTestDefinition(
            id = "DIAGNOSTIC_SYSTEM",
            title = "Diagnostics / export",
            description = "Checks that the diagnostic package can be created and saved.",
            steps = listOf(
                GuidedTestStep(
                    id = "DIAG_EXPORT",
                    title = "Export Diagnostics",
                    instruction = "Tap Test, choose Export Diagnostics, wait for the saved-location confirmation, then tap Test again.",
                    expected = "A non-empty diagnostic ZIP is successfully written to the local Downloads diagnostic folder.",
                    requiredEvidence = listOf("DIAGNOSTIC_EXPORT_COMPLETED"),
                    visualConfirmationRequired = false
                )
            )
        ),
        "FILE_WORKFLOW" to GuidedTestDefinition(
            id = "FILE_WORKFLOW",
            title = "File workflow",
            description = "File picker, supported import result and Recent-file reload.",
            steps = listOf(
                GuidedTestStep(
                    id = "FILE_PICKER_LOAD",
                    title = "Open from Android Files",
                    instruction = "Tap Open and select a supported model.",
                    expected = "Android returns a file and the app displays a valid model.",
                    requiredEvidence = listOf("FILE_PICKER_RETURNED", "MODEL_DISPLAYED"),
                    visualConfirmationRequired = true
                ),
                GuidedTestStep(
                    id = "FILE_RECENT",
                    title = "Recent reload",
                    instruction = "Tap Recent and choose the model you just loaded.",
                    expected = "The persisted recent entry is selected and the model is displayed again.",
                    requiredEvidence = listOf("RECENT_SELECTED", "MODEL_DISPLAYED"),
                    visualConfirmationRequired = true
                )
            )
        )
    )

    private var activeDefinition: GuidedTestDefinition? = null
    private var lastTestId: String? = null
    private var currentStepIndex = -1
    private var stepStartedElapsedMs = 0L
    private var testStartedElapsedMs = 0L
    private var testStartedUtc: String? = null
    private var testEndedUtc: String? = null
    private var overallStatus = GuidedStatus.NOT_RUN
    private val evidence = LinkedHashMap<String, JSONObject>()
    private val results = mutableListOf<GuidedStepResult>()

    fun definitions(): List<GuidedTestDefinition> = definitions.values.toList()

    fun isActive(): Boolean = activeDefinition != null && currentStepIndex >= 0

    fun activeTest(): GuidedTestDefinition? = activeDefinition

    fun currentStep(): GuidedTestStep? =
        activeDefinition?.steps?.getOrNull(currentStepIndex)

    fun currentStepNumber(): Int = if (isActive()) currentStepIndex + 1 else 0

    fun start(testId: String): GuidedTestStep {
        val definition = definitions[testId] ?: error("Unknown guided test: " + testId)
        activeDefinition = definition
        lastTestId = testId
        currentStepIndex = 0
        results.clear()
        evidence.clear()
        overallStatus = GuidedStatus.NOT_RUN
        testStartedElapsedMs = SystemClock.elapsedRealtime()
        stepStartedElapsedMs = testStartedElapsedMs
        testStartedUtc = Instant.now().toString()
        testEndedUtc = null

        DiagnosticLogger.startSession(
            label = "guided_test:" + testId,
            testId = testId
        )
        DiagnosticLogger.setTestContext(testId, definition.steps.first().id)
        DiagnosticLogger.event(
            "TEST",
            "GUIDED_TEST_STARTED",
            mapOf(
                "testId" to testId,
                "title" to definition.title,
                "stepCount" to definition.steps.size
            )
        )
        DiagnosticLogger.event(
            "TEST",
            "STEP_STARTED",
            mapOf(
                "stepId" to definition.steps.first().id,
                "title" to definition.steps.first().title,
                "expected" to definition.steps.first().expected
            )
        )
        persistState()
        return definition.steps.first()
    }

    fun recordEvidence(key: String, details: Map<String, Any?> = emptyMap()) {
        if (!isActive()) return
        val obj = JSONObject()
            .put("timestampUtc", Instant.now().toString())
            .put("elapsedMs", SystemClock.elapsedRealtime() - testStartedElapsedMs)
        for ((k, v) in details) {
            obj.put(k, v ?: JSONObject.NULL)
        }
        evidence[key] = obj
        DiagnosticLogger.event(
            "TEST",
            "EVIDENCE_RECORDED",
            mapOf(
                "evidenceKey" to key,
                "stepId" to currentStep()?.id,
                "details" to details
            )
        )
        persistState()
    }

    fun missingEvidenceForCurrent(): List<String> {
        val step = currentStep() ?: return emptyList()
        return step.requiredEvidence.filterNot { evidence.containsKey(it) }
    }

    fun canPassCurrent(): Boolean = missingEvidenceForCurrent().isEmpty()

    fun passCurrent(message: String = "Expected behavior confirmed."): Boolean {
        val step = currentStep() ?: return false
        val missing = missingEvidenceForCurrent()
        if (missing.isNotEmpty()) {
            DiagnosticLogger.warning(
                "TEST_PASS_REJECTED_MISSING_EVIDENCE",
                mapOf("stepId" to step.id, "missingEvidence" to missing)
            )
            return false
        }

        val duration = SystemClock.elapsedRealtime() - stepStartedElapsedMs
        val measured = step.requiredEvidence.associateWith { key ->
            evidence[key]?.toString() ?: ""
        }
        results += GuidedStepResult(
            stepId = step.id,
            status = GuidedStatus.PASS,
            durationMs = duration,
            message = message,
            measuredValues = measured
        )
        DiagnosticLogger.event(
            "TEST",
            "STEP_PASS",
            mapOf(
                "stepId" to step.id,
                "durationMs" to duration,
                "message" to message,
                "evidenceKeys" to step.requiredEvidence
            )
        )
        advanceAfterPass()
        persistState()
        return true
    }

    fun failCurrent(message: String): GuidedStatus {
        val step = currentStep() ?: return overallStatus
        val duration = SystemClock.elapsedRealtime() - stepStartedElapsedMs
        results += GuidedStepResult(
            stepId = step.id,
            status = GuidedStatus.FAIL,
            durationMs = duration,
            message = message,
            measuredValues = step.requiredEvidence.associateWith { key ->
                evidence[key]?.toString() ?: "missing"
            }
        )
        overallStatus = GuidedStatus.FAIL
        testEndedUtc = Instant.now().toString()
        DiagnosticLogger.event(
            "TEST",
            "STEP_FAIL",
            mapOf(
                "stepId" to step.id,
                "durationMs" to duration,
                "message" to message,
                "missingEvidence" to missingEvidenceForCurrent()
            )
        )
        DiagnosticLogger.event(
            "TEST",
            "GUIDED_TEST_FINISHED",
            mapOf("overallStatus" to overallStatus.name, "firstFailedStep" to step.id)
        )
        DiagnosticLogger.clearTestContext()
        evidence.clear()
        activeDefinition = null
        currentStepIndex = -1
        persistState()
        return overallStatus
    }

    fun blockCurrent(message: String): GuidedStatus {
        val step = currentStep() ?: return overallStatus
        val duration = SystemClock.elapsedRealtime() - stepStartedElapsedMs
        results += GuidedStepResult(
            stepId = step.id,
            status = GuidedStatus.BLOCKED,
            durationMs = duration,
            message = message
        )
        overallStatus = GuidedStatus.BLOCKED
        testEndedUtc = Instant.now().toString()
        DiagnosticLogger.event(
            "TEST",
            "STEP_BLOCKED",
            mapOf("stepId" to step.id, "durationMs" to duration, "message" to message)
        )
        DiagnosticLogger.event(
            "TEST",
            "GUIDED_TEST_FINISHED",
            mapOf("overallStatus" to overallStatus.name, "blockedStep" to step.id)
        )
        DiagnosticLogger.clearTestContext()
        evidence.clear()
        activeDefinition = null
        currentStepIndex = -1
        persistState()
        return overallStatus
    }

    fun cancel(message: String = "Test cancelled by user."): GuidedStatus {
        val step = currentStep()
        if (step != null) {
            val duration = SystemClock.elapsedRealtime() - stepStartedElapsedMs
            results += GuidedStepResult(
                stepId = step.id,
                status = GuidedStatus.PARTIAL,
                durationMs = duration,
                message = message
            )
        }
        overallStatus = GuidedStatus.PARTIAL
        testEndedUtc = Instant.now().toString()
        DiagnosticLogger.event(
            "TEST",
            "GUIDED_TEST_CANCELLED",
            mapOf("message" to message, "stepId" to step?.id)
        )
        DiagnosticLogger.clearTestContext()
        evidence.clear()
        activeDefinition = null
        currentStepIndex = -1
        persistState()
        return overallStatus
    }

    fun overallStatus(): GuidedStatus = overallStatus

    fun resultsJson(): JSONObject {
        val memory = buildResultsJson()
        if (!lastTestId.isNullOrBlank()) return memory

        val persisted = stateFile()
        if (!persisted.exists()) return memory
        return runCatching {
            JSONObject(persisted.readText())
        }.getOrDefault(memory)
    }

    private fun buildResultsJson(): JSONObject {
        val resultArray = JSONArray()
        for (result in results) {
            resultArray.put(
                JSONObject()
                    .put("stepId", result.stepId)
                    .put("status", result.status.name)
                    .put("durationMs", result.durationMs)
                    .put("message", result.message)
                    .put("measuredValues", JSONObject(result.measuredValues))
            )
        }

        val evidenceKeys = JSONArray()
        currentStep()?.requiredEvidence
            ?.filter { evidence.containsKey(it) }
            ?.forEach { evidenceKeys.put(it) }

        return JSONObject()
            .put("testId", lastTestId ?: "")
            .put("sessionId", DiagnosticLogger.sessionId)
            .put("overallStatus", overallStatus.name)
            .put("inProgress", isActive())
            .put(
                "completed",
                overallStatus == GuidedStatus.PASS ||
                    overallStatus == GuidedStatus.FAIL ||
                    overallStatus == GuidedStatus.BLOCKED
            )
            .put("currentStepId", currentStep()?.id ?: JSONObject.NULL)
            .put("currentStepNumber", currentStepNumber())
            .put("startedUtc", testStartedUtc ?: JSONObject.NULL)
            .put("endedUtc", testEndedUtc ?: JSONObject.NULL)
            .put("evidenceKeysForCurrentStep", evidenceKeys)
            .put("stepResults", resultArray)
    }

    fun summaryText(): String {
        if (lastTestId.isNullOrBlank()) {
            val persisted = resultsJson()
            if (persisted.optString("testId").isNotBlank()) {
                return buildString {
                    appendLine("Guided test results")
                    appendLine("Test ID: " + persisted.optString("testId"))
                    appendLine("Session ID: " + persisted.optString("sessionId"))
                    appendLine("Overall status: " + persisted.optString("overallStatus"))
                    appendLine("In progress when last saved: " + persisted.optBoolean("inProgress"))
                    appendLine(
                        "Current step if interrupted: " +
                            persisted.optString("currentStepId", "")
                    )
                    appendLine("Started UTC: " + persisted.optString("startedUtc", ""))
                    appendLine("Ended UTC: " + persisted.optString("endedUtc", ""))
                }
            }
        }

        val firstFailure = results.firstOrNull { it.status == GuidedStatus.FAIL }
        return buildString {
            appendLine("Guided test results")
            appendLine("Test ID: " + (lastTestId ?: ""))
            appendLine("Session ID: " + DiagnosticLogger.sessionId)
            appendLine("Overall status: " + overallStatus.name)
            appendLine("In progress: " + isActive())
            appendLine("Current step if interrupted: " + (currentStep()?.id ?: ""))
            appendLine("Started UTC: " + (testStartedUtc ?: ""))
            appendLine("Ended UTC: " + (testEndedUtc ?: ""))
            if (firstFailure != null) {
                appendLine("First failed step: " + firstFailure.stepId)
                appendLine("Failure: " + firstFailure.message)
            }
            appendLine()
            results.forEach { result ->
                appendLine(
                    result.stepId + ": " + result.status.name +
                        " (" + result.durationMs + " ms) — " + result.message
                )
            }
        }
    }

    private fun stateFile(): File =
        File(DiagnosticLogger.diagnosticsRoot(), "guided_test_state.json")

    private fun persistState() {
        runCatching {
            val target = stateFile()
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(buildResultsJson().toString(2))
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }

            val sessionCopy = File(
                DiagnosticLogger.sessionDirectory(),
                "guided_test_state.json"
            )
            sessionCopy.writeText(buildResultsJson().toString(2))
        }.onFailure { t ->
            DiagnosticLogger.error(
                module = "GuidedTestController",
                operation = "PERSIST_GUIDED_TEST_STATE",
                throwable = t
            )
        }
    }

    private fun advanceAfterPass() {
        val definition = activeDefinition ?: return
        currentStepIndex += 1
        if (currentStepIndex >= definition.steps.size) {
            overallStatus = GuidedStatus.PASS
            testEndedUtc = Instant.now().toString()
            DiagnosticLogger.event(
                "TEST",
                "GUIDED_TEST_FINISHED",
                mapOf(
                    "overallStatus" to overallStatus.name,
                    "durationMs" to (SystemClock.elapsedRealtime() - testStartedElapsedMs)
                )
            )
            DiagnosticLogger.clearTestContext()
            activeDefinition = null
            currentStepIndex = -1
            persistState()
            return
        }

        evidence.clear()
        stepStartedElapsedMs = SystemClock.elapsedRealtime()
        val step = definition.steps[currentStepIndex]
        DiagnosticLogger.setTestContext(definition.id, step.id)
        DiagnosticLogger.event(
            "TEST",
            "STEP_STARTED",
            mapOf(
                "stepId" to step.id,
                "title" to step.title,
                "expected" to step.expected
            )
        )
        persistState()
    }
}
