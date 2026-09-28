package com.edgar.viewer3d

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.filament.Camera
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.View as FilamentView
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.DecimalFormat
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val OPEN_REQUEST = 1001
        private const val PREFS = "viewer_prefs"
        init { Utils.init() }
    }

    private lateinit var surface: SurfaceView
    private lateinit var viewer: ModelViewer
    private lateinit var status: TextView
    private lateinit var choreographer: Choreographer
    private lateinit var cameraManipulator: Manipulator
    private var loopActive = false
    private var autoRotateEnabled = false
    private var autoRotateSpeedIndex = 1
    private var autoRotateDirection = 1f
    private var autoRotateLastFrameNanos = 0L
    private var autoRotateGrabActive = false
    private var autoRotateOffsetPx = 0f
    private var autoRotateOriginX = 0
    private var autoRotateOriginY = 0
    private var orthographicProjection = false
    private val projectionEye = DoubleArray(3)
    private val projectionTarget = DoubleArray(3)
    private val projectionUp = DoubleArray(3)
    private var currentName = "No model"
    private var currentStats = ModelStats("—", 0)
    private var quality = 1
    private var pendingOpenRequestId: String? = null
    private var touchStartedElapsedMs = 0L
    private var touchMaxPointers = 0
    private var touchMoveCount = 0
    private val touchStartEye = DoubleArray(3)
    private val touchStartTarget = DoubleArray(3)
    private val touchEndEye = DoubleArray(3)
    private val touchEndTarget = DoubleArray(3)
    private var autoRotateEvidencePending = false
    private val autoRotateStartEye = DoubleArray(3)
    private val autoRotateStartTarget = DoubleArray(3)
    private val autoRotateCurrentEye = DoubleArray(3)
    private val autoRotateCurrentTarget = DoubleArray(3)
    private var indirectLight: IndirectLight? = null
    private val fillLights = mutableListOf<Int>()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopActive) return
            updateAutoRotate(frameTimeNanos)
            updateProjectionForFrame()
            viewer.render(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLogger.event(
            "APP",
            "ACTIVITY_CREATED",
            mapOf("savedInstanceState" to (savedInstanceState != null))
        )
        window.statusBarColor = Color.rgb(18, 18, 18)
        window.navigationBarColor = Color.rgb(18, 18, 18)
        buildUi()
        cameraManipulator = Manipulator.Builder()
            .targetPosition(0f, 0f, -4f)
            .orbitSpeed(0.01f, 0.01f)
            .viewport(surface.width.coerceAtLeast(1), surface.height.coerceAtLeast(1))
            .build(Manipulator.Mode.ORBIT)
        viewer = ModelViewer(surface, manipulator = cameraManipulator)
        configureStudioLighting()
        surface.setOnTouchListener { _, event ->
            handleSurfaceTouch(event)
            true
        }
        configureBalancedQuality()
        setBackground(0.07, 0.075, 0.085)
        choreographer = Choreographer.getInstance()
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val openWithName = displayName(uri) ?: "model"
                DiagnosticLogger.event(
                    "FILE",
                    "OPEN_WITH_RECEIVED",
                    mapOf("displayName" to openWithName)
                )
                GuidedTestController.recordEvidence(
                    "OPEN_WITH_RECEIVED",
                    mapOf("displayName" to openWithName)
                )
                loadUri(uri, source = "open_with")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DiagnosticLogger.event("APP", "ACTIVITY_RESUMED")
        if (!loopActive) {
            loopActive = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    override fun onPause() {
        DiagnosticLogger.event("APP", "ACTIVITY_PAUSED")
        loopActive = false
        choreographer.removeFrameCallback(frameCallback)
        endAutoRotateGrab()
        autoRotateLastFrameNanos = 0L
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { viewer.destroyModel() }
        runCatching {
            fillLights.forEach { entity ->
                viewer.scene.removeEntity(entity)
                viewer.engine.lightManager.destroy(entity)
                EntityManager.get().destroy(entity)
            }
            fillLights.clear()
            indirectLight?.let {
                viewer.scene.indirectLight = null
                viewer.engine.destroyIndirectLight(it)
            }
            indirectLight = null
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val openWithName = displayName(uri) ?: "model"
                DiagnosticLogger.event(
                    "FILE",
                    "OPEN_WITH_RECEIVED",
                    mapOf("displayName" to openWithName)
                )
                GuidedTestController.recordEvidence(
                    "OPEN_WITH_RECEIVED",
                    mapOf("displayName" to openWithName)
                )
                loadUri(uri, source = "open_with")
            }
        }
    }

    @Deprecated("Legacy activity result keeps this first viewer dependency-light.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != OPEN_REQUEST) return

        val requestId = pendingOpenRequestId
        pendingOpenRequestId = null

        if (resultCode != RESULT_OK || data?.data == null) {
            DiagnosticLogger.event(
                "FILE",
                "FILE_PICKER_CANCELLED",
                mapOf("resultCode" to resultCode),
                requestId = requestId
            )
            return
        }

        val uri = data.data ?: return
        val name = displayName(uri) ?: "model"
        DiagnosticLogger.event(
            "FILE",
            "FILE_PICKER_RETURNED",
            mapOf(
                "displayName" to name,
                "extension" to ModelImporter.extension(name)
            ),
            requestId = requestId
        )
        GuidedTestController.recordEvidence(
            "FILE_PICKER_RETURNED",
            mapOf("displayName" to name)
        )

        val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            contentResolver.takePersistableUriPermission(uri, flags)
        }.onSuccess {
            DiagnosticLogger.event(
                "FILE",
                "PERSISTABLE_PERMISSION_RETAINED",
                mapOf("displayName" to name),
                requestId = requestId
            )
        }.onFailure { t ->
            DiagnosticLogger.warning(
                "PERSISTABLE_PERMISSION_NOT_RETAINED",
                mapOf("displayName" to name, "message" to (t.message ?: "")),
                requestId = requestId
            )
        }
        loadUri(uri, source = "picker", parentRequestId = requestId)
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom
                    )
                }
                insets
            }
        }
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val top = horizontalBar()
        addButton(top, "Open") { openFile() }
        addButton(top, "Recent") { showRecent() }
        addButton(top, "Fit") {
            if (viewer.asset != null) {
                val operationId = DiagnosticLogger.newId("fit")
                DiagnosticLogger.event(
                    "STATE",
                    "FIT_REQUESTED",
                    mapOf("model" to currentName),
                    operationId = operationId
                )
                stopAutoRotate(showToast = false)
                viewer.resetToDefaultState()
                DiagnosticLogger.event(
                    "STATE",
                    "FIT_APPLIED",
                    mapOf("model" to currentName),
                    operationId = operationId
                )
                GuidedTestController.recordEvidence(
                    "FIT_APPLIED",
                    mapOf("model" to currentName)
                )
            } else {
                DiagnosticLogger.warning("FIT_IGNORED_NO_MODEL")
            }
        }
        addButton(top, "Info") { showInfo() }
        addButton(top, "Shot") { captureScreenshot() }
        root.addView(top, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46), Gravity.TOP
        ))

        val bottom = horizontalBar()
        addButton(bottom, "Anim") { toggleAnimation() }
        addButton(bottom, "Next") { nextAnimation() }
        addButton(bottom, "Quality") { cycleQuality() }
        addButton(bottom, "Display") { showDisplayOptions() }
        addButton(bottom, "Test") {
            if (GuidedTestController.isActive()) {
                showGuidedStepReview()
            } else {
                showTestingCenter()
            }
        }
        root.addView(bottom, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46), Gravity.BOTTOM
        ))

        status = TextView(this).apply {
            text = "Open GLB, glTF, STL, OBJ, 3MF, STEP, AMF, X3D, PLY, or OFF"
            setTextColor(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            textSize = 12f
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        val sp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = dp(50)
            leftMargin = dp(5)
        }
        root.addView(status, sp)
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun horizontalBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xAA111111.toInt())
        setPadding(dp(2), dp(3), dp(2), dp(3))
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener {
                DiagnosticLogger.event(
                    "UI_ACTION",
                    "BUTTON_PRESSED",
                    mapOf("label" to label)
                )
                action()
            }
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
    }

    private fun handleSurfaceTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (autoRotateEnabled) {
                    stopAutoRotate(showToast = true, reason = "manual_touch")
                }
                touchStartedElapsedMs = SystemClock.elapsedRealtime()
                touchMaxPointers = event.pointerCount
                touchMoveCount = 0
                cameraManipulator.getLookAt(
                    touchStartEye,
                    touchStartTarget,
                    projectionUp
                )
                DiagnosticLogger.event(
                    "UI_ACTION",
                    "TOUCH_GESTURE_STARTED",
                    mapOf("pointerCount" to event.pointerCount)
                )
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                touchMaxPointers = maxOf(touchMaxPointers, event.pointerCount)
            }
            MotionEvent.ACTION_MOVE -> {
                touchMaxPointers = maxOf(touchMaxPointers, event.pointerCount)
                touchMoveCount += 1
            }
        }

        viewer.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            cameraManipulator.getLookAt(
                touchEndEye,
                touchEndTarget,
                projectionUp
            )
            val delta = cameraDelta(
                touchStartEye,
                touchStartTarget,
                touchEndEye,
                touchEndTarget
            )
            val duration = if (touchStartedElapsedMs > 0L) {
                SystemClock.elapsedRealtime() - touchStartedElapsedMs
            } else {
                0L
            }
            val gestureType = if (touchMaxPointers <= 1) {
                "single_touch_orbit"
            } else {
                "multi_touch_pan_or_zoom"
            }

            DiagnosticLogger.event(
                "UI_ACTION",
                "TOUCH_GESTURE_COMPLETED",
                mapOf(
                    "gestureType" to gestureType,
                    "maxPointerCount" to touchMaxPointers,
                    "moveEvents" to touchMoveCount,
                    "durationMs" to duration,
                    "cameraDelta" to delta,
                    "cancelled" to (event.actionMasked == MotionEvent.ACTION_CANCEL)
                )
            )

            if (delta > 0.0001 && touchMoveCount > 0) {
                DiagnosticLogger.event(
                    "STATE",
                    "CAMERA_CHANGED_BY_TOUCH",
                    mapOf(
                        "gestureType" to gestureType,
                        "cameraDelta" to delta
                    )
                )
                if (touchMaxPointers <= 1) {
                    GuidedTestController.recordEvidence(
                        "TOUCH_CAMERA_CHANGED_SINGLE",
                        mapOf("cameraDelta" to delta)
                    )
                } else {
                    GuidedTestController.recordEvidence(
                        "TOUCH_CAMERA_CHANGED_MULTI",
                        mapOf("cameraDelta" to delta)
                    )
                }
            }

            touchStartedElapsedMs = 0L
            touchMaxPointers = 0
            touchMoveCount = 0
        }
    }

    private fun cameraDelta(
        startEye: DoubleArray,
        startTarget: DoubleArray,
        endEye: DoubleArray,
        endTarget: DoubleArray
    ): Double {
        var sum = 0.0
        for (i in 0..2) {
            val eyeDelta = endEye[i] - startEye[i]
            val targetDelta = endTarget[i] - startTarget[i]
            sum += eyeDelta * eyeDelta + targetDelta * targetDelta
        }
        return kotlin.math.sqrt(sum)
    }

    private fun openFile() {
        val requestId = DiagnosticLogger.newId("file_picker")
        pendingOpenRequestId = requestId
        DiagnosticLogger.event(
            "FILE",
            "FILE_PICKER_REQUESTED",
            requestId = requestId
        )
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Do not restrict Android's document picker by MIME type here.
            // Providers disagree on MIME labels for formats such as STL.
            // The app validates the selected file by extension and parser after selection.
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, OPEN_REQUEST)
    }

    private fun loadUri(
        uri: Uri,
        source: String = "unknown",
        parentRequestId: String? = null
    ) {
        stopAutoRotate(showToast = false)
        val operationId = DiagnosticLogger.newId("model_load")
        val loadStartedElapsedMs = SystemClock.elapsedRealtime()
        val name = displayName(uri) ?: uri.lastPathSegment ?: "model"
        val extension = ModelImporter.extension(name)

        DiagnosticLogger.event(
            "FILE",
            "MODEL_LOAD_REQUESTED",
            mapOf(
                "source" to source,
                "displayName" to name,
                "extension" to extension,
                "previousModel" to currentName
            ),
            requestId = parentRequestId,
            operationId = operationId
        )

        if (!ModelImporter.isSupported(name)) {
            DiagnosticLogger.event(
                "FILE",
                "MODEL_LOAD_REJECTED",
                mapOf(
                    "reason" to "unsupported_extension",
                    "displayName" to name,
                    "extension" to extension
                ),
                requestId = parentRequestId,
                operationId = operationId
            )
            GuidedTestController.recordEvidence(
                "UNSUPPORTED_FILE_REJECTED",
                mapOf("displayName" to name, "extension" to extension)
            )
            toast("Unsupported extension. Use GLB, glTF, STL, OBJ, 3MF, STEP/STP, AMF, X3D, PLY, or OFF.")
            return
        }

        status.text = "Loading $name…"
        DiagnosticLogger.event(
            "STATE",
            "MODEL_LOAD_STATE_CHANGED",
            mapOf("stateBefore" to "idle", "stateAfter" to "loading"),
            operationId = operationId
        )

        Thread {
            val workerStarted = SystemClock.elapsedRealtime()
            DiagnosticLogger.event(
                "PROCESSING",
                "MODEL_LOAD_WORKER_STARTED",
                mapOf("threadName" to Thread.currentThread().name),
                operationId = operationId
            )
            try {
                val readStarted = SystemClock.elapsedRealtime()
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Android could not open the selected file.")
                val readDuration = SystemClock.elapsedRealtime() - readStarted

                DiagnosticLogger.event(
                    "FILE",
                    "INPUT_READ_COMPLETED",
                    mapOf(
                        "displayName" to name,
                        "extension" to extension,
                        "byteSize" to bytes.size,
                        "durationMs" to readDuration
                    ),
                    operationId = operationId
                )
                DiagnosticLogger.setInputInfo(
                    mapOf(
                        "displayName" to name,
                        "extension" to extension,
                        "byteSize" to bytes.size,
                        "source" to source
                    )
                )

                val importStarted = SystemClock.elapsedRealtime()
                DiagnosticLogger.event(
                    "PROCESSING",
                    "IMPORT_REQUESTED",
                    mapOf("displayName" to name, "extension" to extension),
                    operationId = operationId
                )

                val prepared = ModelImporter.prepare(name, bytes, cacheDir, operationId)
                val importDuration = SystemClock.elapsedRealtime() - importStarted

                DiagnosticLogger.event(
                    "PROCESSING",
                    "IMPORT_COMPLETED",
                    mapOf(
                        "format" to prepared.stats.format,
                        "sourceBytes" to prepared.stats.byteSize,
                        "vertices" to prepared.stats.vertices,
                        "triangles" to prepared.stats.triangles,
                        "unit" to prepared.stats.unit,
                        "preparedBytes" to prepared.bytes.size,
                        "isGltfJson" to prepared.isGltfJson,
                        "durationMs" to importDuration
                    ),
                    operationId = operationId
                )

                runOnUiThread {
                    loadPrepared(
                        uri = uri,
                        name = name,
                        prepared = prepared,
                        operationId = operationId,
                        source = source,
                        loadStartedElapsedMs = loadStartedElapsedMs
                    )
                }
            } catch (t: Throwable) {
                DiagnosticLogger.error(
                    module = "MainActivity",
                    operation = "MODEL_IMPORT",
                    throwable = t,
                    details = mapOf(
                        "displayName" to name,
                        "extension" to extension,
                        "source" to source,
                        "workerDurationMs" to (SystemClock.elapsedRealtime() - workerStarted)
                    ),
                    operationId = operationId
                )
                runOnUiThread {
                    status.text = "Load failed"
                    GuidedTestController.recordEvidence(
                        "MODEL_LOAD_FAILED_SAFELY",
                        mapOf(
                            "displayName" to name,
                            "extension" to extension,
                            "stage" to "import"
                        )
                    )
                    DiagnosticLogger.event(
                        "STATE",
                        "MODEL_LOAD_STATE_CHANGED",
                        mapOf("stateBefore" to "loading", "stateAfter" to "failed"),
                        operationId = operationId
                    )
                    AlertDialog.Builder(this)
                        .setTitle("Could not open model")
                        .setMessage(t.message ?: t.javaClass.simpleName)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun loadPrepared(
        uri: Uri,
        name: String,
        prepared: PreparedModel,
        operationId: String,
        source: String,
        loadStartedElapsedMs: Long
    ) {
        val previousName = currentName
        DiagnosticLogger.event(
            "STATE",
            "MODEL_DISPLAY_REQUESTED",
            mapOf(
                "displayName" to name,
                "format" to prepared.stats.format,
                "previousModel" to previousName
            ),
            operationId = operationId
        )

        try {
            if (prepared.isGltfJson) {
                viewer.loadModelGltf(ByteBuffer.wrap(prepared.bytes)) { ref ->
                    decodeEmbeddedResource(ref)
                }
                if (viewer.asset == null) {
                    error(
                        "This .gltf references external files. Embedded glTF works now; " +
                            "sidecar-folder loading is on the importer roadmap."
                    )
                }
            } else {
                viewer.loadModelGlb(ByteBuffer.wrap(prepared.bytes))
            }

            require(viewer.asset != null) {
                "Filament did not create a displayable model asset."
            }

            viewer.transformToUnitCube()
            currentName = name
            val animationCount = viewer.animator?.animationCount ?: 0
            currentStats = prepared.stats.copy(animations = animationCount)
            addRecent(uri, name)

            status.text = name + " • " + prepared.stats.format +
                (prepared.stats.triangles?.let { " • " + formatInt(it) + " triangles" } ?: "") +
                if (animationCount > 0) " • " + animationCount + " anim" else ""

            val totalDuration = SystemClock.elapsedRealtime() - loadStartedElapsedMs
            DiagnosticLogger.setInputInfo(
                mapOf(
                    "displayName" to name,
                    "extension" to ModelImporter.extension(name),
                    "byteSize" to prepared.stats.byteSize,
                    "source" to source,
                    "format" to prepared.stats.format,
                    "vertices" to prepared.stats.vertices,
                    "triangles" to prepared.stats.triangles,
                    "animations" to animationCount,
                    "unit" to prepared.stats.unit,
                    "bounds" to prepared.stats.bounds?.let {
                        mapOf(
                            "minX" to it.minX,
                            "minY" to it.minY,
                            "minZ" to it.minZ,
                            "maxX" to it.maxX,
                            "maxY" to it.maxY,
                            "maxZ" to it.maxZ
                        )
                    }
                )
            )
            DiagnosticLogger.event(
                "OUTPUT",
                "MODEL_DISPLAYED",
                mapOf(
                    "previousModel" to previousName,
                    "displayName" to name,
                    "format" to prepared.stats.format,
                    "vertices" to prepared.stats.vertices,
                    "triangles" to prepared.stats.triangles,
                    "animations" to animationCount,
                    "assetPresent" to (viewer.asset != null),
                    "durationMs" to totalDuration
                ),
                operationId = operationId
            )
            DiagnosticLogger.event(
                "STATE",
                "MODEL_LOAD_STATE_CHANGED",
                mapOf("stateBefore" to "loading", "stateAfter" to "displayed"),
                operationId = operationId
            )
            val extensionEvidence = ModelImporter.extension(name)
                .uppercase(Locale.US)
                .replace(".", "_")
            val displayEvidenceDetails = mapOf(
                "displayName" to name,
                "format" to prepared.stats.format,
                "triangles" to prepared.stats.triangles,
                "animations" to animationCount
            )
            GuidedTestController.recordEvidence(
                "MODEL_DISPLAYED",
                displayEvidenceDetails
            )
            GuidedTestController.recordEvidence(
                "MODEL_DISPLAYED_" + extensionEvidence,
                displayEvidenceDetails
            )
            if (previousName != "No model" && previousName != name) {
                GuidedTestController.recordEvidence(
                    "MODEL_REPLACED",
                    mapOf(
                        "previousModel" to previousName,
                        "newModel" to name
                    )
                )
            }
            if (animationCount >= 2) {
                GuidedTestController.recordEvidence(
                    "ANIMATED_MODEL_LOADED",
                    mapOf("animationCount" to animationCount)
                )
            }
        } catch (t: Throwable) {
            status.text = "Load failed"
            DiagnosticLogger.error(
                module = "MainActivity",
                operation = "MODEL_DISPLAY",
                throwable = t,
                details = mapOf(
                    "displayName" to name,
                    "format" to prepared.stats.format,
                    "source" to source
                ),
                operationId = operationId
            )
            GuidedTestController.recordEvidence(
                "MODEL_LOAD_FAILED_SAFELY",
                mapOf(
                    "displayName" to name,
                    "extension" to ModelImporter.extension(name),
                    "stage" to "display"
                )
            )
            DiagnosticLogger.event(
                "STATE",
                "MODEL_LOAD_STATE_CHANGED",
                mapOf("stateBefore" to "loading", "stateAfter" to "failed"),
                operationId = operationId
            )
            AlertDialog.Builder(this)
                .setTitle("Could not display model")
                .setMessage(t.message ?: t.javaClass.simpleName)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun decodeEmbeddedResource(ref: String): ByteBuffer? {
        if (!ref.startsWith("data:", true)) return null
        val comma = ref.indexOf(',')
        if (comma < 0) return null
        val meta = ref.substring(0, comma)
        val data = ref.substring(comma + 1)
        val bytes = if (meta.contains(";base64", true)) Base64.decode(data, Base64.DEFAULT)
        else Uri.decode(data).toByteArray(Charsets.UTF_8)
        return ByteBuffer.wrap(bytes)
    }

    private fun showInfo() {
        val s = currentStats
        val b = s.bounds
        val text = buildString {
            appendLine("File: $currentName")
            appendLine("Format: ${s.format}")
            appendLine("Size: ${formatBytes(s.byteSize)}")
            s.vertices?.let { appendLine("Vertices: ${formatInt(it)}") }
            s.triangles?.let { appendLine("Triangles: ${formatInt(it)}") }
            s.animations?.let { appendLine("Animations: $it") }
            s.unit?.let { appendLine("Source unit: $it") }
            if (b != null) {
                appendLine()
                appendLine("Source dimensions:")
                appendLine("X: ${fmt(b.sizeX)}")
                appendLine("Y: ${fmt(b.sizeY)}")
                appendLine("Z: ${fmt(b.sizeZ)}")
                appendLine()
                appendLine("Bounds:")
                appendLine("min (${fmt(b.minX)}, ${fmt(b.minY)}, ${fmt(b.minZ)})")
                appendLine("max (${fmt(b.maxX)}, ${fmt(b.maxY)}, ${fmt(b.maxZ)})")
            }
        }
        DiagnosticLogger.event(
            "OUTPUT",
            "INFO_PRESENTED",
            mapOf(
                "displayName" to currentName,
                "format" to s.format,
                "byteSize" to s.byteSize,
                "vertices" to s.vertices,
                "triangles" to s.triangles,
                "animations" to s.animations
            )
        )
        GuidedTestController.recordEvidence(
            "INFO_PRESENTED",
            mapOf("displayName" to currentName, "format" to s.format)
        )
        AlertDialog.Builder(this).setTitle("Model information").setMessage(text)
            .setPositiveButton("OK", null).show()
    }

    private fun toggleAnimation() {
        val animator = viewer.animator
        if (animator == null || animator.animationCount == 0) {
            DiagnosticLogger.warning(
                "ANIMATION_TOGGLE_IGNORED",
                mapOf("reason" to "no_animation", "model" to currentName)
            )
            toast("This model has no animation.")
            return
        }

        val oldValue = viewer.autoPlayAnimations
        viewer.autoPlayAnimations = !oldValue
        DiagnosticLogger.event(
            "STATE",
            "ANIMATION_TOGGLED",
            mapOf(
                "oldValue" to oldValue,
                "newValue" to viewer.autoPlayAnimations,
                "activeAnimationIndex" to viewer.activeAnimationIndex,
                "animationCount" to animator.animationCount
            )
        )
        GuidedTestController.recordEvidence(
            "ANIMATION_TOGGLED",
            mapOf(
                "oldValue" to oldValue,
                "newValue" to viewer.autoPlayAnimations
            )
        )
        toast(if (viewer.autoPlayAnimations) "Animation playing" else "Animation paused")
    }

    private fun nextAnimation() {
        val animator = viewer.animator
        if (animator == null || animator.animationCount == 0) {
            DiagnosticLogger.warning(
                "ANIMATION_NEXT_IGNORED",
                mapOf("reason" to "no_animation", "model" to currentName)
            )
            toast("This model has no animation.")
            return
        }

        val oldIndex = viewer.activeAnimationIndex
        val newIndex = (oldIndex + 1) % animator.animationCount
        viewer.activeAnimationIndex = newIndex
        viewer.autoPlayAnimations = true
        DiagnosticLogger.event(
            "STATE",
            "ANIMATION_INDEX_CHANGED",
            mapOf(
                "oldIndex" to oldIndex,
                "newIndex" to newIndex,
                "animationCount" to animator.animationCount,
                "autoPlay" to viewer.autoPlayAnimations
            )
        )
        GuidedTestController.recordEvidence(
            "ANIMATION_INDEX_CHANGED",
            mapOf("oldIndex" to oldIndex, "newIndex" to newIndex)
        )
        toast("Animation " + (newIndex + 1) + " of " + animator.animationCount)
    }

    private fun cycleQuality() {
        val oldQuality = quality
        quality = (quality + 1) % 3
        when (quality) {
            0 -> {
                viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
                    enabled = true
                    quality = FilamentView.QualityLevel.LOW
                }
                viewer.view.multiSampleAntiAliasingOptions =
                    viewer.view.multiSampleAntiAliasingOptions.apply { enabled = false }
                viewer.view.antiAliasing = FilamentView.AntiAliasing.FXAA
                toast("Performance quality")
            }
            1 -> {
                configureBalancedQuality()
                toast("Balanced quality")
            }
            2 -> {
                viewer.view.dynamicResolutionOptions =
                    viewer.view.dynamicResolutionOptions.apply { enabled = false }
                viewer.view.multiSampleAntiAliasingOptions =
                    viewer.view.multiSampleAntiAliasingOptions.apply { enabled = true }
                viewer.view.antiAliasing = FilamentView.AntiAliasing.FXAA
                viewer.view.renderQuality =
                    viewer.view.renderQuality.apply {
                        hdrColorBuffer = FilamentView.QualityLevel.HIGH
                    }
                toast("High quality")
            }
        }

        val modeName = when (quality) {
            0 -> "Performance"
            1 -> "Balanced"
            else -> "High"
        }
        DiagnosticLogger.event(
            "STATE",
            "QUALITY_CHANGED",
            mapOf(
                "oldModeIndex" to oldQuality,
                "newModeIndex" to quality,
                "newMode" to modeName
            )
        )
        GuidedTestController.recordEvidence(
            "QUALITY_CHANGED",
            mapOf("oldModeIndex" to oldQuality, "newMode" to modeName)
        )
    }

    private fun configureBalancedQuality() {
        viewer.view.renderQuality =
            viewer.view.renderQuality.apply { hdrColorBuffer = FilamentView.QualityLevel.MEDIUM }
        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = true
            quality = FilamentView.QualityLevel.MEDIUM
        }
        viewer.view.multiSampleAntiAliasingOptions =
            viewer.view.multiSampleAntiAliasingOptions.apply { enabled = true }
        viewer.view.antiAliasing = FilamentView.AntiAliasing.FXAA
    }

    private fun showDisplayOptions() {
        val options = arrayOf(
            "Background: black", "Background: studio gray", "Background: light gray",
            "Lighting: soft", "Lighting: studio", "Lighting: bright",
            "Sun: 50%", "Sun: 100%", "Sun: 150%",
            if (autoRotateEnabled) "Auto-rotate: Stop" else "Auto-rotate: Start",
            "Auto-rotate speed: ${autoRotateSpeedName()}",
            "Auto-rotate direction: ${autoRotateDirectionName()}",
            "Projection: ${if (orthographicProjection) "Orthographic" else "Perspective"}",
            "Named views..."
        )
        AlertDialog.Builder(this).setTitle("Display").setItems(options) { _, which ->
            val selectedLabel = options.getOrNull(which) ?: "unknown"
            DiagnosticLogger.event(
                "UI_ACTION",
                "DISPLAY_OPTION_SELECTED",
                mapOf("index" to which, "label" to selectedLabel)
            )
            when (which) {
                0 -> setBackground(0.0, 0.0, 0.0)
                1 -> setBackground(0.07, 0.075, 0.085)
                2 -> setBackground(0.35, 0.35, 0.35)
                3 -> setLightingPreset(12_000f, 65_000f, "Soft")
                4 -> setLightingPreset(22_000f, 90_000f, "Studio")
                5 -> setLightingPreset(34_000f, 115_000f, "Bright")
                6 -> setSunIntensity(50_000f)
                7 -> setSunIntensity(100_000f)
                8 -> setSunIntensity(150_000f)
                9 -> setAutoRotateEnabled(!autoRotateEnabled)
                10 -> {
                    val oldSpeed = autoRotateSpeedName()
                    autoRotateSpeedIndex = (autoRotateSpeedIndex + 1) % 3
                    val newSpeed = autoRotateSpeedName()
                    DiagnosticLogger.event(
                        "STATE",
                        "AUTO_ROTATE_SPEED_CHANGED",
                        mapOf("oldValue" to oldSpeed, "newValue" to newSpeed)
                    )
                    GuidedTestController.recordEvidence(
                        "AUTO_ROTATE_SPEED_CHANGED",
                        mapOf("oldValue" to oldSpeed, "newValue" to newSpeed)
                    )
                    toast("Auto-rotate speed: " + newSpeed)
                }
                11 -> {
                    val oldDirection = autoRotateDirectionName()
                    autoRotateDirection *= -1f
                    restartAutoRotateGrab()
                    val newDirection = autoRotateDirectionName()
                    DiagnosticLogger.event(
                        "STATE",
                        "AUTO_ROTATE_DIRECTION_CHANGED",
                        mapOf("oldValue" to oldDirection, "newValue" to newDirection)
                    )
                    GuidedTestController.recordEvidence(
                        "AUTO_ROTATE_DIRECTION_CHANGED",
                        mapOf("oldValue" to oldDirection, "newValue" to newDirection)
                    )
                    toast("Auto-rotate direction: " + newDirection)
                }
                12 -> setOrthographicProjection(!orthographicProjection)
                13 -> showNamedViews()
            }
        }.show()
    }

    private fun showNamedViews() {
        if (viewer.asset == null) {
            DiagnosticLogger.warning(
                "NAMED_VIEW_MENU_IGNORED",
                mapOf("reason" to "no_model")
            )
            toast("Open a model before choosing a named view.")
            return
        }

        val names = arrayOf(
            "Front",
            "Back",
            "Left",
            "Right",
            "Top",
            "Bottom",
            "Isometric"
        )

        DiagnosticLogger.event(
            "NAVIGATION",
            "NAMED_VIEW_MENU_PRESENTED",
            mapOf("viewCount" to names.size)
        )

        AlertDialog.Builder(this)
            .setTitle("Named views")
            .setItems(names) { _, which ->
                applyNamedView(names[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyNamedView(name: String) {
        if (viewer.asset == null) {
            toast("Open a model first.")
            return
        }

        val key = name.uppercase(Locale.US)
        val radiansPerPixel = 0.01
        val maxVertical =
            Math.PI / 2.0 - 0.01

        val theta: Double
        val phi: Double
        val expectedX: Double
        val expectedY: Double
        val expectedZ: Double

        when (key) {
            "FRONT" -> {
                theta = 0.0
                phi = 0.0
                expectedX = 0.0
                expectedY = 0.0
                expectedZ = 1.0
            }
            "BACK" -> {
                theta = Math.PI
                phi = 0.0
                expectedX = 0.0
                expectedY = 0.0
                expectedZ = -1.0
            }
            "LEFT" -> {
                theta = -Math.PI / 2.0
                phi = 0.0
                expectedX = -1.0
                expectedY = 0.0
                expectedZ = 0.0
            }
            "RIGHT" -> {
                theta = Math.PI / 2.0
                phi = 0.0
                expectedX = 1.0
                expectedY = 0.0
                expectedZ = 0.0
            }
            "TOP" -> {
                theta = 0.0
                phi = maxVertical
                expectedX = 0.0
                expectedY = 1.0
                expectedZ = 0.0
            }
            "BOTTOM" -> {
                theta = 0.0
                phi = -maxVertical
                expectedX = 0.0
                expectedY = -1.0
                expectedZ = 0.0
            }
            else -> {
                theta = Math.PI / 4.0
                phi = kotlin.math.atan(1.0 / kotlin.math.sqrt(2.0))
                val cosPhi = kotlin.math.cos(phi)
                expectedX = kotlin.math.sin(theta) * cosPhi
                expectedY = kotlin.math.sin(phi)
                expectedZ = kotlin.math.cos(theta) * cosPhi
            }
        }

        val operationId = DiagnosticLogger.newId("named_view")
        DiagnosticLogger.event(
            "STATE",
            "NAMED_VIEW_REQUESTED",
            mapOf(
                "view" to name,
                "projection" to if (orthographicProjection) {
                    "Orthographic"
                } else {
                    "Perspective"
                }
            ),
            operationId = operationId
        )

        stopAutoRotate(
            showToast = false,
            reason = "named_view"
        )
        viewer.resetToDefaultState()

        val centerX = surface.width.coerceAtLeast(1) / 2
        val centerY = surface.height.coerceAtLeast(1) / 2

        if (theta != 0.0 || phi != 0.0) {
            val targetX = centerX -
                kotlin.math.round(theta / radiansPerPixel).toInt()
            val targetY = centerY -
                kotlin.math.round(phi / radiansPerPixel).toInt()

            cameraManipulator.grabBegin(
                centerX,
                centerY,
                false
            )
            cameraManipulator.grabUpdate(
                targetX,
                targetY
            )
            cameraManipulator.grabEnd()
        }

        cameraManipulator.getLookAt(
            projectionEye,
            projectionTarget,
            projectionUp
        )

        val centerWorldX = 0.0
        val centerWorldY = 0.0
        val centerWorldZ = -4.0
        val viewX = projectionEye[0] - centerWorldX
        val viewY = projectionEye[1] - centerWorldY
        val viewZ = projectionEye[2] - centerWorldZ
        val viewLength = kotlin.math.sqrt(
            viewX * viewX +
                viewY * viewY +
                viewZ * viewZ
        ).coerceAtLeast(0.0001)

        val actualX = viewX / viewLength
        val actualY = viewY / viewLength
        val actualZ = viewZ / viewLength
        val directionDot =
            actualX * expectedX +
                actualY * expectedY +
                actualZ * expectedZ

        if (orthographicProjection) {
            updateProjectionForFrame()
        }

        val verified = directionDot >= 0.97
        DiagnosticLogger.event(
            "STATE",
            "NAMED_VIEW_APPLIED",
            mapOf(
                "view" to name,
                "directionDot" to directionDot,
                "verifiedDirection" to verified,
                "eyeX" to projectionEye[0],
                "eyeY" to projectionEye[1],
                "eyeZ" to projectionEye[2],
                "projection" to if (orthographicProjection) {
                    "Orthographic"
                } else {
                    "Perspective"
                }
            ),
            operationId = operationId
        )

        if (verified) {
            GuidedTestController.recordEvidence(
                "NAMED_VIEW_" + key,
                mapOf(
                    "directionDot" to directionDot,
                    "projection" to if (orthographicProjection) {
                        "Orthographic"
                    } else {
                        "Perspective"
                    }
                )
            )
            toast(name + " view")
        } else {
            DiagnosticLogger.warning(
                "NAMED_VIEW_DIRECTION_MISMATCH",
                mapOf(
                    "view" to name,
                    "directionDot" to directionDot
                ),
                operationId = operationId
            )
            toast(name + " view applied, but direction verification failed.")
        }
    }

    private fun setOrthographicProjection(enabled: Boolean) {
        val oldValue = orthographicProjection
        orthographicProjection = enabled
        if (enabled) {
            updateProjectionForFrame()
            toast("Orthographic projection")
        } else {
            restorePerspectiveProjection()
            toast("Perspective projection")
        }
        DiagnosticLogger.event(
            "STATE",
            "PROJECTION_CHANGED",
            mapOf(
                "oldMode" to if (oldValue) "Orthographic" else "Perspective",
                "newMode" to if (enabled) "Orthographic" else "Perspective",
                "viewportWidth" to viewer.view.viewport.width,
                "viewportHeight" to viewer.view.viewport.height
            )
        )
        GuidedTestController.recordEvidence(
            "PROJECTION_CHANGED",
            mapOf("newMode" to if (enabled) "Orthographic" else "Perspective")
        )
    }

    private fun restorePerspectiveProjection() {
        // Reassigning the focal length intentionally asks ModelViewer to rebuild
        // its normal lens projection for the current viewport.
        viewer.cameraFocalLength = viewer.cameraFocalLength
    }

    private fun updateProjectionForFrame() {
        if (!orthographicProjection) return

        val viewport = viewer.view.viewport
        val width = viewport.width
        val height = viewport.height
        if (width <= 0 || height <= 0) return

        cameraManipulator.getLookAt(projectionEye, projectionTarget, projectionUp)

        val gazeX = projectionTarget[0] - projectionEye[0]
        val gazeY = projectionTarget[1] - projectionEye[1]
        val gazeZ = projectionTarget[2] - projectionEye[2]
        val gazeLength = kotlin.math.sqrt(
            gazeX * gazeX + gazeY * gazeY + gazeZ * gazeZ
        ).coerceAtLeast(0.001)

        val unitGazeX = gazeX / gazeLength
        val unitGazeY = gazeY / gazeLength
        val unitGazeZ = gazeZ / gazeLength

        // transformToUnitCube() places the normalized model center at (0, 0, -4).
        // Filament ORBIT scroll moves eye and target together, so eye-to-target
        // distance is not a zoom metric. Instead, measure camera depth to the
        // fixed model center along the current gaze direction.
        val modelCenterX = 0.0
        val modelCenterY = 0.0
        val modelCenterZ = -4.0
        val toCenterX = modelCenterX - projectionEye[0]
        val toCenterY = modelCenterY - projectionEye[1]
        val toCenterZ = modelCenterZ - projectionEye[2]
        val depthToModelCenter = kotlin.math.abs(
            toCenterX * unitGazeX +
                toCenterY * unitGazeY +
                toCenterZ * unitGazeZ
        ).coerceAtLeast(0.25)

        // Filament's lens projection uses a 24 mm vertical sensor. Matching the
        // perspective framing at the model-center plane gives half-height:
        // depth * (sensorHeight / 2) / focalLength.
        val focalLengthMm = viewer.cameraFocalLength.toDouble().coerceAtLeast(0.001)
        val halfHeight =
            (depthToModelCenter * 12.0 / focalLengthMm).coerceAtLeast(0.05)
        val aspect = width.toDouble() / height.toDouble()
        val halfWidth = halfHeight * aspect

        viewer.camera.setProjection(
            Camera.Projection.ORTHO,
            -halfWidth, halfWidth,
            -halfHeight, halfHeight,
            viewer.cameraNear.toDouble(),
            viewer.cameraFar.toDouble()
        )
    }

    private fun autoRotateSpeedName(): String = when (autoRotateSpeedIndex) {
        0 -> "Slow"
        1 -> "Normal"
        else -> "Fast"
    }

    private fun autoRotatePixelsPerSecond(): Float = when (autoRotateSpeedIndex) {
        0 -> 25f
        1 -> 50f
        else -> 100f
    }

    private fun autoRotateDirectionName(): String =
        if (autoRotateDirection > 0f) "Right" else "Left"

    private fun setAutoRotateEnabled(enabled: Boolean) {
        if (enabled) {
            if (viewer.asset == null) {
                DiagnosticLogger.warning(
                    "AUTO_ROTATE_START_IGNORED",
                    mapOf("reason" to "no_model")
                )
                toast("Open a model before starting Auto-rotate.")
                return
            }
            cameraManipulator.getLookAt(
                autoRotateStartEye,
                autoRotateStartTarget,
                projectionUp
            )
            autoRotateEvidencePending = true
            autoRotateEnabled = true
            autoRotateLastFrameNanos = 0L
            restartAutoRotateGrab()
            DiagnosticLogger.event(
                "STATE",
                "AUTO_ROTATE_STATE_CHANGED",
                mapOf(
                    "oldValue" to false,
                    "newValue" to true,
                    "speed" to autoRotateSpeedName(),
                    "direction" to autoRotateDirectionName()
                )
            )
            toast(
                "Auto-rotate on • " +
                    autoRotateSpeedName() + " • " +
                    autoRotateDirectionName()
            )
        } else {
            stopAutoRotate(showToast = true)
        }
    }

    private fun stopAutoRotate(
        showToast: Boolean,
        reason: String = "requested"
    ) {
        val wasEnabled = autoRotateEnabled
        autoRotateEnabled = false
        autoRotateEvidencePending = false
        autoRotateLastFrameNanos = 0L
        autoRotateOffsetPx = 0f
        endAutoRotateGrab()
        if (wasEnabled) {
            DiagnosticLogger.event(
                "STATE",
                "AUTO_ROTATE_STATE_CHANGED",
                mapOf(
                    "oldValue" to true,
                    "newValue" to false,
                    "reason" to reason
                )
            )
        }
        if (showToast && wasEnabled) toast("Auto-rotate off")
    }

    private fun endAutoRotateGrab() {
        if (autoRotateGrabActive) {
            runCatching { cameraManipulator.grabEnd() }
            autoRotateGrabActive = false
        }
    }

    private fun restartAutoRotateGrab() {
        endAutoRotateGrab()
        autoRotateOffsetPx = 0f
        autoRotateLastFrameNanos = 0L
    }

    private fun beginAutoRotateGrab(frameTimeNanos: Long) {
        val width = surface.width.coerceAtLeast(1)
        val height = surface.height.coerceAtLeast(1)
        autoRotateOriginX = width / 2
        autoRotateOriginY = height / 2
        cameraManipulator.grabBegin(autoRotateOriginX, autoRotateOriginY, false)
        autoRotateGrabActive = true
        autoRotateOffsetPx = 0f
        autoRotateLastFrameNanos = frameTimeNanos
    }

    private fun updateAutoRotate(frameTimeNanos: Long) {
        if (!autoRotateEnabled || viewer.asset == null) return

        val centerX = surface.width.coerceAtLeast(1) / 2
        val centerY = surface.height.coerceAtLeast(1) / 2

        if (!autoRotateGrabActive ||
            centerX != autoRotateOriginX ||
            centerY != autoRotateOriginY
        ) {
            endAutoRotateGrab()
            beginAutoRotateGrab(frameTimeNanos)
            return
        }

        if (autoRotateLastFrameNanos == 0L) {
            autoRotateLastFrameNanos = frameTimeNanos
            return
        }

        val elapsedNanos = (frameTimeNanos - autoRotateLastFrameNanos)
            .coerceIn(0L, 100_000_000L)
        autoRotateLastFrameNanos = frameTimeNanos
        val deltaSeconds = elapsedNanos / 1_000_000_000f

        autoRotateOffsetPx +=
            autoRotatePixelsPerSecond() * autoRotateDirection * deltaSeconds

        cameraManipulator.grabUpdate(
            autoRotateOriginX + autoRotateOffsetPx.toInt(),
            autoRotateOriginY
        )

        if (autoRotateEvidencePending) {
            cameraManipulator.getLookAt(
                autoRotateCurrentEye,
                autoRotateCurrentTarget,
                projectionUp
            )
            val delta = cameraDelta(
                autoRotateStartEye,
                autoRotateStartTarget,
                autoRotateCurrentEye,
                autoRotateCurrentTarget
            )
            if (delta > 0.0001) {
                autoRotateEvidencePending = false
                DiagnosticLogger.event(
                    "STATE",
                    "AUTO_ROTATE_CAMERA_CHANGED",
                    mapOf(
                        "cameraDelta" to delta,
                        "speed" to autoRotateSpeedName(),
                        "direction" to autoRotateDirectionName()
                    )
                )
                GuidedTestController.recordEvidence(
                    "AUTO_ROTATE_CAMERA_CHANGED",
                    mapOf("cameraDelta" to delta)
                )
            }
        }

        val restartThreshold = (surface.width.coerceAtLeast(1) * 0.25f).coerceAtLeast(80f)
        if (kotlin.math.abs(autoRotateOffsetPx) >= restartThreshold) {
            endAutoRotateGrab()
            autoRotateOffsetPx = 0f
        }
    }

    private fun configureStudioLighting() {
        val ambient = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.55f, 0.58f, 0.62f))
            .intensity(22_000f)
            .build(viewer.engine)
        indirectLight = ambient
        viewer.scene.indirectLight = ambient

        addFillLight(-0.35f, -0.45f, -0.82f, 24_000f, 1.0f, 0.96f, 0.92f)
        addFillLight(0.72f, -0.30f, 0.62f, 14_000f, 0.90f, 0.95f, 1.0f)

        val manager = viewer.engine.lightManager
        manager.setIntensity(manager.getInstance(viewer.light), 90_000f)
    }

    private fun addFillLight(
        x: Float, y: Float, z: Float, intensity: Float,
        r: Float, g: Float, b: Float
    ) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .direction(x, y, z)
            .color(r, g, b)
            .intensity(intensity)
            .castShadows(false)
            .build(viewer.engine, entity)
        viewer.scene.addEntity(entity)
        fillLights += entity
    }

    private fun setLightingPreset(environment: Float, sun: Float, name: String) {
        indirectLight?.setIntensity(environment)
        val manager = viewer.engine.lightManager
        manager.setIntensity(manager.getInstance(viewer.light), sun)
        DiagnosticLogger.event(
            "STATE",
            "LIGHTING_CHANGED",
            mapOf(
                "preset" to name,
                "environmentIntensity" to environment,
                "sunIntensity" to sun
            )
        )
        GuidedTestController.recordEvidence(
            "LIGHTING_CHANGED",
            mapOf("preset" to name)
        )
        toast(name + " lighting")
    }

    private fun setBackground(r: Double, g: Double, b: Double) {
        val options: Renderer.ClearOptions = viewer.renderer.clearOptions
        options.clear = true
        options.clearColor = doubleArrayOf(r, g, b, 1.0)
        viewer.renderer.clearOptions = options
        DiagnosticLogger.event(
            "STATE",
            "BACKGROUND_CHANGED",
            mapOf("r" to r, "g" to g, "b" to b)
        )
        GuidedTestController.recordEvidence(
            "BACKGROUND_CHANGED",
            mapOf("r" to r, "g" to g, "b" to b)
        )
    }

    private fun setSunIntensity(value: Float) {
        val manager = viewer.engine.lightManager
        manager.setIntensity(manager.getInstance(viewer.light), value)
        DiagnosticLogger.event(
            "STATE",
            "SUN_CHANGED",
            mapOf("intensityLux" to value)
        )
        GuidedTestController.recordEvidence(
            "SUN_CHANGED",
            mapOf("intensityLux" to value)
        )
        toast("Sun " + (value / 1000).toInt() + "k lux")
    }

    private fun captureScreenshot() {
        val operationId = DiagnosticLogger.newId("screenshot")
        DiagnosticLogger.event(
            "OUTPUT",
            "SCREENSHOT_REQUESTED",
            mapOf("model" to currentName),
            operationId = operationId
        )

        if (viewer.asset == null) {
            DiagnosticLogger.warning(
                "SCREENSHOT_IGNORED_NO_MODEL",
                operationId = operationId
            )
            toast("Open a model first.")
            return
        }

        val started = SystemClock.elapsedRealtime()
        toast("Capturing…")
        viewer.debugGetNextFrameCallback { source ->
            DiagnosticLogger.event(
                "OUTPUT",
                "SCREENSHOT_FRAME_CAPTURED",
                mapOf(
                    "width" to source.width,
                    "height" to source.height
                ),
                operationId = operationId
            )
            Thread {
                try {
                    val matrix = Matrix().apply { preScale(1f, -1f) }
                    val bitmap = Bitmap.createBitmap(
                        source,
                        0,
                        0,
                        source.width,
                        source.height,
                        matrix,
                        true
                    )
                    val saved = saveBitmap(bitmap)
                    val duration = SystemClock.elapsedRealtime() - started
                    DiagnosticLogger.event(
                        "OUTPUT",
                        "SCREENSHOT_SAVED",
                        mapOf(
                            "location" to saved.first,
                            "byteSize" to saved.second,
                            "durationMs" to duration,
                            "width" to bitmap.width,
                            "height" to bitmap.height
                        ),
                        operationId = operationId
                    )
                    GuidedTestController.recordEvidence(
                        "SCREENSHOT_SAVED",
                        mapOf(
                            "location" to saved.first,
                            "byteSize" to saved.second
                        )
                    )
                    runOnUiThread {
                        toast("Screenshot saved: " + saved.first)
                    }
                } catch (t: Throwable) {
                    DiagnosticLogger.error(
                        module = "MainActivity",
                        operation = "SCREENSHOT_SAVE",
                        throwable = t,
                        details = mapOf("model" to currentName),
                        operationId = operationId
                    )
                    runOnUiThread {
                        toast("Screenshot failed: " + (t.message ?: t.javaClass.simpleName))
                    }
                }
            }.start()
        }
    }

    private fun saveBitmap(bitmap: Bitmap): Pair<String, Long> {
        val fileName = "3DViewer_" + System.currentTimeMillis() + ".png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/3DViewer"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("MediaStore insert failed.")

            try {
                val compressed = contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                } ?: error("Could not write screenshot.")
                require(compressed) { "Bitmap compression failed." }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)

                val size = contentResolver.openFileDescriptor(uri, "r")?.use {
                    it.statSize
                } ?: -1L
                require(size > 0L) {
                    "Screenshot output exists but its size could not be verified."
                }
                return "Pictures/3DViewer/" + fileName to size
            } catch (t: Throwable) {
                runCatching { contentResolver.delete(uri, null, null) }
                throw t
            }
        }

        val dir = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "3DViewer"
        ).apply { mkdirs() }
        val file = File(dir, fileName)
        val compressed = FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        require(compressed && file.exists() && file.length() > 0L) {
            "Screenshot output verification failed."
        }
        return file.absolutePath to file.length()
    }

    private fun addRecent(uri: Uri, name: String) {
        if (uri == Uri.EMPTY) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val existing = (0 until 8).mapNotNull { i ->
            val u = prefs.getString("recent_uri_" + i, null)
            val n = prefs.getString("recent_name_" + i, null)
            if (u != null && n != null) u to n else null
        }.filterNot { it.first == uri.toString() }.toMutableList()

        existing.add(0, uri.toString() to name)
        val edit = prefs.edit().clear()
        existing.take(8).forEachIndexed { i, pair ->
            edit.putString("recent_uri_" + i, pair.first)
            edit.putString("recent_name_" + i, pair.second)
        }
        edit.apply()

        DiagnosticLogger.event(
            "STATE",
            "RECENT_LIST_UPDATED",
            mapOf(
                "displayName" to name,
                "storedCount" to minOf(existing.size, 8)
            )
        )
    }

    private fun showRecent() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val items = (0 until 8).mapNotNull { i ->
            val u = prefs.getString("recent_uri_" + i, null)
            val n = prefs.getString("recent_name_" + i, null)
            if (u != null && n != null) u to n else null
        }

        DiagnosticLogger.event(
            "NAVIGATION",
            "RECENT_LIST_PRESENTED",
            mapOf("itemCount" to items.size)
        )

        if (items.isEmpty()) {
            DiagnosticLogger.warning("RECENT_LIST_EMPTY")
            toast("No recent models yet.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Recent models")
            .setItems(items.map { it.second }.toTypedArray()) { _, which ->
                val selected = items[which]
                DiagnosticLogger.event(
                    "UI_ACTION",
                    "RECENT_SELECTED",
                    mapOf(
                        "index" to which,
                        "displayName" to selected.second
                    )
                )
                GuidedTestController.recordEvidence(
                    "RECENT_SELECTED",
                    mapOf("displayName" to selected.second)
                )
                loadUri(
                    Uri.parse(selected.first),
                    source = "recent"
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                DiagnosticLogger.event(
                    "NAVIGATION",
                    "RECENT_LIST_CANCELLED"
                )
            }
            .show()
    }

    private fun showTestingCenter() {
        DiagnosticLogger.event(
            "NAVIGATION",
            "TESTING_CENTER_PRESENTED"
        )

        val options = arrayOf(
            "Start Guided Test",
            "Export Diagnostics",
            "Help / Controls",
            "Last Test Result"
        )

        AlertDialog.Builder(this)
            .setTitle("Testing Center")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        DiagnosticLogger.event(
                            "UI_ACTION",
                            "GUIDED_TEST_MENU_REQUESTED"
                        )
                        showGuidedTestMenu()
                    }
                    1 -> exportDiagnostics()
                    2 -> showHelp()
                    3 -> showLastTestResult()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHelp() {
        DiagnosticLogger.event("NAVIGATION", "HELP_PRESENTED")
        val message = """
            TOUCH
            One finger: orbit.
            Two fingers: pan and pinch zoom.

            FILES
            Open: choose a model from Android Files.
            Recent: reopen a previously granted file.
            Fit: reset framing.
            Info: file, mesh, bounds and animation information.
            Shot: save the rendered view as PNG.

            MODEL / DISPLAY
            Anim / Next: glTF animation controls.
            Quality: Performance / Balanced / High.
            Display: background, studio lighting, sun brightness,
            Auto-rotate and Projection.
            Auto-rotate: continuous turntable orbit with
            Slow/Normal/Fast speed and Left/Right direction.
            Touching the model stops Auto-rotate.
            Projection: Perspective or true Orthographic viewing.
            Display → Named views: Front, Back, Left, Right, Top,
            Bottom and Isometric.

            TESTING
            Test: opens the Testing Center directly.
            During a guided test, Test opens the current step review.
            Export Diagnostics creates a local ZIP in
            Downloads/3DViewerDiagnostics.
            Diagnostics do not upload automatically.

            SUPPORTED NOW
            GLB, embedded glTF, STL, OBJ, 3MF, STEP/STP via OCCT,
            AMF, X3D, ASCII PLY and OFF geometry.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("3D Viewer controls")
            .setView(scrollableDialogText(message))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showGuidedTestMenu() {
        val definitions = GuidedTestController.definitions()
        val labels = definitions.map { it.title }.toTypedArray()

        DiagnosticLogger.event(
            "NAVIGATION",
            "GUIDED_TEST_MENU_PRESENTED",
            mapOf("testCount" to definitions.size)
        )

        AlertDialog.Builder(this)
            .setTitle("Test This Version")
            .setItems(labels) { _, which ->
                val definition = definitions[which]
                DiagnosticLogger.event(
                    "UI_ACTION",
                    "GUIDED_TEST_SELECTED",
                    mapOf(
                        "testId" to definition.id,
                        "title" to definition.title
                    )
                )
                GuidedTestController.start(definition.id)
                showGuidedStepInstruction()
            }
            .setNegativeButton("Close") { _, _ ->
                DiagnosticLogger.event(
                    "NAVIGATION",
                    "GUIDED_TEST_MENU_CANCELLED"
                )
            }
            .show()
    }

    private fun showGuidedStepInstruction() {
        val test = GuidedTestController.activeTest()
        val step = GuidedTestController.currentStep()
        if (test == null || step == null) {
            showGuidedTestFinished()
            return
        }

        val stepNumber = GuidedTestController.currentStepNumber()
        val message = buildString {
            appendLine("Test: " + test.title)
            appendLine("Step " + stepNumber + " of " + test.steps.size)
            appendLine()
            appendLine("WHAT TO DO")
            appendLine(step.instruction)
            appendLine()
            appendLine("EXPECTED")
            appendLine(step.expected)
            appendLine()
            appendLine(
                "Perform the step, then tap the permanent Test button " +
                    "to review the result."
            )
        }

        AlertDialog.Builder(this)
            .setTitle(step.title)
            .setView(scrollableDialogText(message))
            .setPositiveButton("Do Step") { _, _ ->
                DiagnosticLogger.event(
                    "TEST",
                    "STEP_INSTRUCTION_DISMISSED_FOR_ACTION",
                    mapOf("stepId" to step.id)
                )
                toast("Perform the step, then tap Test.")
            }
            .setNegativeButton("Cancel Test") { _, _ ->
                GuidedTestController.cancel()
                showGuidedTestFinished()
            }
            .setNeutralButton("Blocked") { _, _ ->
                GuidedTestController.blockCurrent(
                    "Tester reported that this step could not be performed."
                )
                showGuidedTestFinished()
            }
            .show()
    }

    private fun showGuidedStepReview() {
        val test = GuidedTestController.activeTest()
        val step = GuidedTestController.currentStep()
        if (test == null || step == null) {
            showGuidedTestFinished()
            return
        }

        val missing = GuidedTestController.missingEvidenceForCurrent()
        val evidenceText = if (missing.isEmpty()) {
            "Required software evidence: observed."
        } else {
            "Required software evidence still missing: " +
                missing.joinToString(", ")
        }

        val message = buildString {
            appendLine("Test: " + test.title)
            appendLine(
                "Step " + GuidedTestController.currentStepNumber() +
                    " of " + test.steps.size
            )
            appendLine()
            appendLine("EXPECTED")
            appendLine(step.expected)
            appendLine()
            appendLine(evidenceText)
            if (step.visualConfirmationRequired) {
                appendLine()
                appendLine(
                    "Confirm the visible behavior as well as the " +
                        "software evidence."
                )
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Check: " + step.title)
            .setView(scrollableDialogText(message))
            .setPositiveButton(
                if (step.visualConfirmationRequired) {
                    "Looks Correct"
                } else {
                    "Verify Result"
                }
            ) { _, _ ->
                if (!GuidedTestController.canPassCurrent()) {
                    val stillMissing =
                        GuidedTestController.missingEvidenceForCurrent()
                    DiagnosticLogger.warning(
                        "GUIDED_TEST_PASS_BLOCKED",
                        mapOf(
                            "stepId" to step.id,
                            "missingEvidence" to stillMissing
                        )
                    )
                    AlertDialog.Builder(this)
                        .setTitle("Cannot pass this step yet")
                        .setView(
                            scrollableDialogText(
                                "The intended software result has not " +
                                    "been observed yet.\n\nMissing evidence: " +
                                    stillMissing.joinToString(", ")
                            )
                        )
                        .setPositiveButton("Continue Step") { _, _ ->
                            showGuidedStepInstruction()
                        }
                        .show()
                } else {
                    val passed = GuidedTestController.passCurrent(
                        if (step.visualConfirmationRequired) {
                            "Objective evidence observed and tester " +
                                "confirmed the visible result."
                        } else {
                            "Objective software result verified."
                        }
                    )
                    if (passed) {
                        if (GuidedTestController.isActive()) {
                            showGuidedStepInstruction()
                        } else {
                            showGuidedTestFinished()
                        }
                    }
                }
            }
            .setNegativeButton("Expected Behavior Failed") { _, _ ->
                GuidedTestController.failCurrent(
                    "Tester reported that the expected behavior failed."
                )
                showGuidedTestFinished()
            }
            .setNeutralButton("Cancel Test") { _, _ ->
                GuidedTestController.cancel()
                showGuidedTestFinished()
            }
            .show()
    }

    private fun showGuidedTestFinished() {
        val statusValue = GuidedTestController.overallStatus()
        val message = GuidedTestController.summaryText().ifBlank {
            "Guided test status: " + statusValue.name
        }

        AlertDialog.Builder(this)
            .setTitle("Test Result: " + statusValue.name)
            .setView(scrollableDialogText(message))
            .setPositiveButton("Export Test + Diagnostics") { _, _ ->
                exportDiagnostics()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showLastTestResult() {
        val result = GuidedTestController.resultsJson()
        val testId = result.optString("testId")
        if (testId.isBlank()) {
            toast("No guided test result is available yet.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Last Test Result")
            .setView(
                scrollableDialogText(
                    GuidedTestController.summaryText()
                )
            )
            .setPositiveButton("Export Diagnostics") { _, _ ->
                exportDiagnostics()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun scrollableDialogText(textValue: String): ScrollView {
        val textView = TextView(this).apply {
            text = textValue
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(12), dp(20), dp(18))
        }
        return ScrollView(this).apply {
            isFillViewport = true
            addView(
                textView,
                ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun exportDiagnostics() {
        DiagnosticLogger.event(
            "UI_ACTION",
            "EXPORT_DIAGNOSTICS_SELECTED"
        )
        toast("Exporting diagnostics…")
        Thread {
            try {
                val result = DiagnosticExporter.export(this)
                runOnUiThread {
                    GuidedTestController.recordEvidence(
                        "DIAGNOSTIC_EXPORT_COMPLETED",
                        mapOf(
                            "location" to result.location,
                            "byteSize" to result.byteSize
                        )
                    )
                    toast(
                        "Diagnostics saved: " +
                            result.location +
                            " (" + formatBytes(result.byteSize) + ")"
                    )
                }
            } catch (t: Throwable) {
                DiagnosticLogger.error(
                    module = "MainActivity",
                    operation = "EXPORT_DIAGNOSTICS_UI",
                    throwable = t
                )
                runOnUiThread {
                    toast(
                        "Diagnostics export failed: " +
                            (t.message ?: t.javaClass.simpleName)
                    )
                }
            }
        }.start()
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return uri.lastPathSegment
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Float) = DecimalFormat("0.###").format(v.toDouble())
    private fun formatInt(v: Int) = String.format(Locale.US, "%,d", v)
    private fun formatBytes(v: Long): String = when {
        v >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", v / 1_073_741_824.0)
        v >= 1_048_576L -> String.format(Locale.US, "%.2f MB", v / 1_048_576.0)
        v >= 1024L -> String.format(Locale.US, "%.1f KB", v / 1024.0)
        else -> "$v bytes"
    }
}
