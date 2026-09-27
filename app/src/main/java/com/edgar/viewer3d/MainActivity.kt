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
                DiagnosticLogger.event(
                    "FILE",
                    "OPEN_WITH_RECEIVED",
                    mapOf("displayName" to (displayName(uri) ?: "model"))
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
                DiagnosticLogger.event(
                    "FILE",
                    "OPEN_WITH_RECEIVED",
                    mapOf("displayName" to (displayName(uri) ?: "model"))
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
        addButton(bottom, "Help") { showHelp() }
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

                val prepared = ModelImporter.prepare(name, bytes, cacheDir)
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
            GuidedTestController.recordEvidence(
                "MODEL_DISPLAYED",
                mapOf(
                    "displayName" to name,
                    "format" to prepared.stats.format,
                    "triangles" to prepared.stats.triangles,
                    "animations" to animationCount
                )
            )
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
            "Projection: ${if (orthographicProjection) "Orthographic" else "Perspective"}"
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
                    toast("Auto-rotate direction: " + newDirection)
                }
                12 -> setOrthographicProjection(!orthographicProjection)
            }
        }.show()
    }

    private fun setOrthographicProjection(enabled: Boolean) {
        orthographicProjection = enabled
        if (enabled) {
            updateProjectionForFrame()
            toast("Orthographic projection")
        } else {
            restorePerspectiveProjection()
            toast("Perspective projection")
        }
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
                toast("Open a model before starting Auto-rotate.")
                return
            }
            autoRotateEnabled = true
            autoRotateLastFrameNanos = 0L
            restartAutoRotateGrab()
            toast("Auto-rotate on • ${autoRotateSpeedName()} • ${autoRotateDirectionName()}")
        } else {
            stopAutoRotate(showToast = true)
        }
    }

    private fun stopAutoRotate(showToast: Boolean) {
        val wasEnabled = autoRotateEnabled
        autoRotateEnabled = false
        autoRotateLastFrameNanos = 0L
        autoRotateOffsetPx = 0f
        endAutoRotateGrab()
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
        toast("$name lighting")
    }

    private fun setBackground(r: Double, g: Double, b: Double) {
        val options: Renderer.ClearOptions = viewer.renderer.clearOptions
        options.clear = true
        options.clearColor = doubleArrayOf(r, g, b, 1.0)
        viewer.renderer.clearOptions = options
    }

    private fun setSunIntensity(value: Float) {
        val manager = viewer.engine.lightManager
        manager.setIntensity(manager.getInstance(viewer.light), value)
        toast("Sun ${(value / 1000).toInt()}k lux")
    }

    private fun captureScreenshot() {
        if (viewer.asset == null) {
            toast("Open a model first.")
            return
        }
        toast("Capturing…")
        viewer.debugGetNextFrameCallback { source ->
            Thread {
                try {
                    val matrix = Matrix().apply { preScale(1f, -1f) }
                    val bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                    val location = saveBitmap(bitmap)
                    runOnUiThread { toast("Screenshot saved: $location") }
                } catch (t: Throwable) {
                    runOnUiThread { toast("Screenshot failed: ${t.message}") }
                }
            }.start()
        }
    }

    private fun saveBitmap(bitmap: Bitmap): String {
        val fileName = "3DViewer_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/3DViewer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed.")
            contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: error("Could not write screenshot.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            return "Pictures/3DViewer/$fileName"
        }
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "3DViewer").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    private fun addRecent(uri: Uri, name: String) {
        if (uri == Uri.EMPTY) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val existing = (0 until 8).mapNotNull { i ->
            val u = prefs.getString("recent_uri_$i", null)
            val n = prefs.getString("recent_name_$i", null)
            if (u != null && n != null) u to n else null
        }.filterNot { it.first == uri.toString() }.toMutableList()
        existing.add(0, uri.toString() to name)
        val edit = prefs.edit().clear()
        existing.take(8).forEachIndexed { i, pair ->
            edit.putString("recent_uri_$i", pair.first)
            edit.putString("recent_name_$i", pair.second)
        }
        edit.apply()
    }

    private fun showRecent() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val items = (0 until 8).mapNotNull { i ->
            val u = prefs.getString("recent_uri_$i", null)
            val n = prefs.getString("recent_name_$i", null)
            if (u != null && n != null) u to n else null
        }
        if (items.isEmpty()) {
            toast("No recent models yet.")
            return
        }
        AlertDialog.Builder(this).setTitle("Recent models")
            .setItems(items.map { it.second }.toTypedArray()) { _, which ->
                loadUri(Uri.parse(items[which].first))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("3D Viewer controls")
            .setMessage(
                """
                One finger: orbit.
                Two fingers: pan and pinch zoom.

                Open: choose a model from Android Files.
                Recent: reopen a previously granted file.
                Fit: reset framing.
                Info: file, mesh, bounds and animation information.
                Shot: save the rendered view as PNG.
                Anim / Next anim: glTF animation controls.
                Quality: Performance / Balanced / High.
                Display: background, studio lighting, sun brightness, Auto-rotate and Projection.
                Auto-rotate: continuous turntable orbit with Slow/Normal/Fast speed
                and Left/Right direction. Touching the model stops Auto-rotate so
                normal orbit/pan/zoom immediately takes over.
                Projection: switch between Perspective and true Orthographic viewing.
                Orbit, pan and pinch zoom continue to work in both modes.

                Supported now:
                GLB, embedded glTF, STL, OBJ, 3MF, STEP/STP via OCCT, AMF, X3D, ASCII PLY and OFF geometry.

                Planned: measurements, wireframe/edges, named views,
                section planes, exploded view, annotations, scene hierarchy, mesh
                diagnostics, AR and additional CAD/model formats.
                """.trimIndent()
            ).setPositiveButton("OK", null).show()
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
