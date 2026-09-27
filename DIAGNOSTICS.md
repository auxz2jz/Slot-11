# Android 3D Viewer Diagnostics

This project follows the current Master Instruction Library and the project's program-specific diagnostic/testing architecture.

## Current implementation status

**v0.8.0 CANDIDATE — IMPLEMENTED IN SOURCE, DEVICE VALIDATION REQUIRED**

Diagnostics were designed after inspecting this application's actual controls, file/import paths, renderer state, background work, and error handling. The system does not assume controls from another application.

## Actual viewer behavior covered

User-facing controls and workflows instrumented in v0.8.0:

- Open / Android Files picker
- Android Open With
- Recent-file list and reload
- Fit
- Info
- Shot / screenshot
- Anim play/pause
- Next animation
- Quality mode
- Display menu selection
- background presets
- lighting presets
- sun intensity
- Auto-rotate start/stop, speed and direction
- Perspective / Orthographic projection
- one-finger orbit
- two-finger pan / pinch zoom
- Help
- Test This Version
- Export Diagnostics

Important automatic/background operations covered:

- file read worker
- importer dispatch
- mesh parsing/conversion
- GLB auto-repair
- mesh-to-GLB encoding
- STEP temporary-input creation
- OCCT STEP native tessellation
- JNI packed-mesh validation/unpack
- Filament model display request/result
- auto-rotate camera movement
- screenshot render callback and background save
- diagnostic export worker
- application/session lifecycle
- uncaught crash preservation

There are currently no program text-entry fields, keyboard shortcuts, sliders, or user-cancellable import jobs, so diagnostics do not invent those controls.

## Core event model

The central logger is DiagnosticLogger.kt.

Each JSONL event includes:

- diagnostic session UUID
- monotonically increasing sequence number
- UTC timestamp
- monotonic elapsed milliseconds
- category and event name
- exact app version/build code
- optional guided-test ID and step ID
- optional request ID and operation ID
- structured details

Primary categories currently used include SESSION, APP, UI_ACTION, NAVIGATION, INPUT, FILE, PROCESSING, STATE, OUTPUT, TEST, DIAGNOSTIC, WARNING, and ERROR.

The full ordered event stream is the primary source for locating the first real failure.

## User action versus software result

A control press is never treated as proof of success.

For model open, the trace separates picker request/return, model-load request, loading state, input read, import request/result, display request, MODEL_DISPLAYED result, and displayed state. An import/display error is recorded at the stage where it first occurs.

For screenshots, Shot creates SCREENSHOT_REQUESTED, then the rendered frame is captured, PNG writing is attempted, the destination is verified non-empty, and only then is SCREENSHOT_SAVED recorded.

For Auto-rotate, enabling the state does not satisfy the guided test. The camera manipulator must actually change.

Touch move events are summarized rather than logged frame-by-frame. The gesture summary records maximum pointer count, move count, duration, camera before/after delta, gesture classification, and cancellation.

## Importer diagnostics

ModelImporter.kt records importer dispatch, safe input metadata, GLB repair results, parsed mesh counts/bounds, mesh-to-GLB output size, and importer errors with operation correlation.

OcctStepImporter.kt records native OCCT availability, temporary input verification, native tessellation start/completion, packed result size/duration, bridge unpack/validation, resulting geometry counts, and exact failing stage on error.

The native C++ bridge itself remains unchanged in v0.8.0; Kotlin-side staged diagnostics surround its call and validate its payload.

## Persistent sessions and retention

A normal app launch starts a diagnostic session. Starting a guided test starts a fresh diagnostic session.

Session data remains local under the application's private files area. The logger keeps a bounded recent-event buffer and a bounded number of historical session directories.

Guided-test progress is persisted after meaningful changes. If the app is interrupted, saved machine-readable state retains the test ID and current step so a later diagnostic export can identify where testing stopped.

## Error and crash preservation

Caught important failures are routed through the central error logger with exception type, message, stack trace, module, operation, session/test/step context, operation/request identifiers where available, safe state/input details, and duration when known.

An application-level uncaught-exception handler attempts to preserve session ID, UTC timestamp, active test/step, thread, exception type/message/stack, event-log path, and the crashed session event trail. It then delegates to Android's prior handler.

On the next launch, crash artifacts rotate to previous_crash.json and previous_crashed_session_events.jsonl for export.

## Input and environment metadata

The system records only debugging-relevant information such as app/build, Android/SDK, manufacturer/model, ABI, CPU count, total RAM, display size/density, locale, model display name, extension/format, size, geometry counts, animation count, unit/bounds, and load source.

It does not collect precise location, credentials, account data, or source 3D model contents.

## Diagnostic export

The visible Export Diagnostics action is available through Help in the v0.8.0 candidate.

The exporter creates a local ZIP in Downloads/3DViewerDiagnostics when supported. Package contents include, when applicable:

- README.txt
- summary.txt
- device_app_info.txt
- input_info.txt
- events.jsonl
- guided_test_results.json
- guided_test_results.txt
- errors.txt
- previous_crash.json
- previous_crashed_session_events.jsonl

The ZIP is verified non-empty and export itself has requested/completed/failed events. Diagnostics are never uploaded automatically.

## Known diagnostic gaps after v0.8.0

These are deliberately not faked:

- large-file progress percentage is not available from every parser yet
- import cancellation is not yet implemented
- a long-operation stall watchdog is not yet implemented
- GPU/FPS/backend diagnostics remain roadmap work
- detailed progress from inside the native OCCT C++ tessellation loop is not yet exposed through JNI

Those capabilities should be added incrementally when the underlying application support exists.

## Recovery workflow

1. identify the failed guided-test step
2. inspect summary.txt
3. inspect events.jsonl chronologically
4. locate the earliest abnormal request/state/result
5. inspect errors/crash data
6. make the smallest evidence-based fix
7. rebuild
8. repeat the same test
9. preserve the new result

## Baseline rule

The diagnostics branch does not replace a known-good viewer merely because it compiles.

- v0.4.0 remains the last fully regression-verified baseline
- v0.5.1 STL and animation behavior were feature-verified
- v0.6.0 Auto-rotate was feature-verified
- v0.7.1 projection fix is checkpointed separately and still requires device retest
- v0.8.0 diagnostics/guided testing is a separate candidate until built and tested
