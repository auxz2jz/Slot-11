# PROJECT MEMORY — Android 3D Viewer

## Canonical repository
- Repository: **auxz2jz/Slot-11**
- Project: Android 3D Viewer / Model Inspector
- Package: `com.edgar.viewer3d`
- Last device-tested stable version on `main`: **v0.4.0** (`82accea784de4b31ebfc9af48be51bcaff6af229`)
- Permanent tested checkpoint branch: `stable/v0.4.0-tested`
- Last feature checkpoint: **v0.5.1 STL picker compatibility + STEP/STP via OCCT** (`candidate/v0.5.1-stl-animation-tested`)
- Last tested feature checkpoint: **v0.6.0 auto-rotate / turntable** (`candidate/v0.6.0-auto-rotate-tested`)
- Projection-fix checkpoint: **v0.7.1 Perspective / Orthographic** (`candidate/v0.7.1-projection-fix-untested`)
- Archived diagnostic candidate: **v0.8.0** (`archive/v0.8.0-diagnostics-export-defects`)
- Diagnostic export checkpoint: **v0.8.1** (`candidate/v0.8.1-diagnostic-export-fix`)
- Tested v0.8.2 branch: **testing UI + coverage** (`feature/testing-ui-v0.8.2`)
- Current development branch: **v0.8.3 active-test navigation fix** (`feature/testing-ui-v0.8.3`)
- Latest candidate status: **CANDIDATE — CI BUILD SUCCEEDED, NOT USER VERIFIED**
- Successful v0.5.0 build source commit: `5625660add9ee76d708ba90acf83e1729e0f493a`
- Successful v0.5.0 GitHub Actions run: `36184091026`
- v0.5.0 APK artifact ID: `10886586045`
- v0.5.0 artifact ZIP SHA-256: `b9bec51841664ec009c86c303054a7c44bbb9d2a32297b6c4aa050f06eb6244d`
- v0.5.0 extracted APK SHA-256: `c4187753d08fd49311c0db74c7bcd8666a84102e88e92b205be19c77b8ad8ea8`
- This file is the Android project memory/checkpoint source of truth.
- Master Library startup order: read `auxz2jz/master-instruction-library/INSTRUCTION_INDEX.md` and mandatory files, then `MASTER_RULE_ADOPTION.md`, `CROSS_PLATFORM_COORDINATION.md`, this file, `3D_VIEWER_ROADMAP.md`, `TESTING.md`, and `DIAGNOSTICS.md` before substantial source work.

## Product goal
Build a phone-friendly but progressively professional 3D model viewer that can open common mesh/model files directly on Android and grow toward CAD-style inspection tools.

The app must not become a bare file previewer. Preserve a feature-rich direction: formats, inspection, measurement, display modes, animation, diagnostics, scene controls, screenshots, recents/library, and strong touch navigation.

## Rendering architecture
- Google Filament is the canonical real-time renderer.
- GLB/glTF are rendered directly through Filament gltfio / ModelViewer.
- Non-glTF triangle formats are parsed into `MeshData`, converted in memory to a standards-compliant GLB, and loaded through the same renderer.
- This keeps camera behavior, lighting, PBR rendering, screenshots, quality settings, and future inspection logic on one pipeline.

## v0.1.0 baseline
Implemented:
- GLB.
- Embedded-resource glTF.
- Binary + ASCII STL.
- OBJ geometry.
- ASCII PLY.
- OFF.
- 3MF triangle geometry.
- Android file picker.
- Open-with intent handling.
- Touch orbit / pan / zoom.
- Fit/reset.
- glTF animation play/pause + next.
- Recent files.
- File/model info.
- Screenshots.
- Quality modes.
- Background presets.
- Sun intensity presets.
- CI debug APK build.

Known v0.1.0 limitations:
- External sidecar resources referenced by .gltf are not yet resolved through a folder/tree chooser.
- OBJ MTL/material/texture loading is not yet implemented.
- Binary PLY is not yet implemented.
- 3MF materials/textures/components are not yet fully interpreted.
- CAD/B-rep formats such as STEP/IGES need a dedicated conversion/import backend.
- FBX/DAE/USD family is roadmap work.
- Measurement, section planes, wireframe/edge overlay, exploded view, AR, scene tree, annotations, and mesh-repair diagnostics are roadmap work.

## v0.4.0 3D-printing compatibility pass
User-provided regression corpus inspected on 2026-09-25 (files were used for diagnostics, not committed to the repository):
- Smoker_Assembly_Open_75deg_Outlined.stl: binary STL, 3,868 triangles, valid correctly oriented normals, no degenerate triangles found.
- Smoker_Assembly_Closed_Outlined.stl: binary STL, 3,868 triangles, valid correctly oriented normals, no degenerate triangles found.
- 01_Door_Bracket_1_SLA_Prototype.obj: 2,296 vertices, 4,612 triangle faces, geometry-only OBJ (no source normals/material library).
- 02_Door_Bracket_2_Hex_Capture_SLA_Prototype.obj: 3,210 vertices, 6,440 triangle faces, geometry-only OBJ (no source normals/material library).
- Creality K2 SE / K1C / Anycubic Photon CHITUBOX 3MF test files: each contains 4 build objects, 230,488 vertices and 461,016 triangles; every local triangle index is valid.

Critical 3MF bug found and fixed:
- Old parser concatenated all 3MF vertices globally while leaving each object's indices local, so objects after the first could point at the wrong vertices.
- New 3MF parser preserves per-object index spaces, applies correct global offsets, follows build items, supports component references/transforms, validates indices, and then flattens the build for Filament.

Additional v0.4.0 work:
- Existing imported normals are normalized before rendering; invalid normals fall back to generated normals.
- Added AMF mesh import (plain XML and compressed AMF/XML containers).
- Added common X3D IndexedFaceSet / IndexedTriangleSet / TriangleSet import with basic Transform support.
- UI/file picker/help updated for AMF and X3D.
- Normal generation was optimized after the verified v0.4.0 correctness build to avoid per-triangle temporary allocations on very large meshes.

3D-printing format priority:
- Primary working mesh formats: STL, 3MF, OBJ, AMF, GLB/glTF, X3D, PLY (ASCII), OFF.
- STEP/STP and IGES/IGS require a CAD/B-rep kernel rather than a triangle parser. Official Open CASCADE Android Kotlin/JNI sample was identified as the intended native path for future STEP/IGES support.
- OBJ MTL/textures, binary PLY, full 3MF material/texture extensions, and slicer-specific 3MF metadata remain follow-up work.


## v0.5.0 STEP/STP recovery and native-integration procedure
- Selected CAD kernel: **Open CASCADE Technology (OCCT) 8.0.1**, pinned to upstream tag `V8.0.1`.
- Architecture: STEP/STP -> OCCT `STEPControl_Reader` -> OCCT B-rep tessellation -> packed triangle mesh -> existing `MeshData` -> existing GLB encoder -> Filament renderer.
- Filament remains the only presentation renderer; OCCT is a headless CAD import/tessellation dependency.
- Initial Android ABI: `arm64-v8a`.
- The first STEP attempt was preserved on branch `archive/failed-occt-step-attempt-2026-09-25`.
- That attempt proved OCCT 8.0.1 itself cross-compiles successfully with the GitHub Android NDK. The APK build failed afterward because Android cross-CMake did not resolve an installed OCCT header through `find_path()`, even though the header existed.
- Recovery fix: use the explicit installed include directory `app/occt/arm64-v8a/include/opencascade` and verify `STEPControl_Reader.hxx` with `EXISTS`, avoiding NDK root-path interference.
- CI rule for native dependencies: build/cache/upload OCCT in a dedicated job first; the Android APK job downloads that completed artifact. A later bridge/compiler failure must not force another OCCT rebuild.
- STEP regression fixture: `test-fixtures/occt_screw.step`, pinned from OCCT `V8.0.1` `data/step/screw.step`; use it as the first known-good STEP device test before testing larger/user CAD files.
- v0.5.0 CI compilation/package verification succeeded on run `36184091026`.
- Do not merge v0.5.0 into the verified line until STEP/STP and the v0.4.0 regression set are tested on-device.
- Do not poll the same long-running workflow repeatedly. Inspect the final job result/log once it completes; if it fails, fix the exact reported failure before starting another run.

## Master Instruction Library adoption
- Adopted current canonical library: `auxz2jz/master-instruction-library` on 2026-09-26.
- Mapping record: `MASTER_RULE_ADOPTION.md`.
- Shared multi-agent/platform coordination: `CROSS_PLATFORM_COORDINATION.md`.
- Android diagnostic gaps/implementation order: `DIAGNOSTICS.md`.
- Android release/guided testing mapping: `TESTING.md`.
- No source reorganization or structural migration was performed for adoption.
- Existing Android source/build layout remains authoritative.
- Windows/PC implementation status: **NOT STARTED**; future Windows work must use an isolated ownership area and separate baseline/candidate records.

## Current task — STL compatibility and format corpus
User test evidence after installing v0.5.0:
- STEP/STP geometry loads, but displays gray. Current root cause: the OCCT bridge uses `STEPControl_Reader` and emits only tessellated positions/indices; STEP/XCAF color/material metadata is not preserved.
- User reports STL is still not supported. Source inspection shows the STL parser/extension route exists, but Android file selection uses `EXTRA_MIME_TYPES`, which can hide valid STL files whose provider reports an unlisted STL MIME type.

Implementation plan for the next candidate:
1. Preserve v0.4.0 VERIFIED and v0.5.0 CANDIDATE checkpoints.
2. Remove MIME filtering from the internal `ACTION_OPEN_DOCUMENT` picker while retaining extension-based importer validation.
3. Add common STL MIME aliases to Android Open-With intent handling.
4. Do not change the working STL parser unless test evidence from the generated corpus proves a parser defect.
5. Generate one consistent test object in every currently supported extension for repeatable device testing.
6. Record STEP color preservation as a separate follow-up feature; do not mix a large XCAF/material refactor into the STL compatibility fix.

Expected source changes:
- `app/src/main/java/com/edgar/viewer3d/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- version/build metadata and test/project documentation only as needed.

## Current task and exact next action
Current task: adopt the Master Instruction Library without changing verified behavior.

Adoption documentation is complete. No Android source was changed by the adoption itself.

**Exact next development/test action:** finish the v0.8.1 build, then build v0.8.2 with direct Test access and expanded coverage. After a clean compile, create the next feature version for named Front/Back/Left/Right/Top/Bottom/Isometric views, including its guided test in the same version.

## v0.5.1 STL picker compatibility candidate
User screenshot evidence on 2026-09-26 shows both generated STL fixtures visible in Android Files but **greyed out / unselectable** while other formats remain selectable. This confirms the failure occurs at Android document-picker filtering before `StlParser` receives the file.

Targeted fix:
- internal `ACTION_OPEN_DOCUMENT` picker now uses `*/*` without `EXTRA_MIME_TYPES` filtering;
- extension and parser validation remain authoritative after selection;
- Android Open-With manifest includes additional common STL MIME aliases;
- STL parser source itself was not changed because current evidence does not implicate parsing.

v0.5.1 build:
- versionCode: 6
- versionName: 0.5.1
- GitHub Actions run: `36293472169`
- build result: **SUCCESS**
- APK artifact: `Android3DViewer-v0.5.1-debug-arm64`
- artifact ID: `10922617848`
- artifact ZIP SHA-256: `c9f5c11efadb6b5ededbe5cb27b11bcc7dc99565b53616446f6c3cdecc3f5d29`
- extracted APK SHA-256: `489cf4072ca8ff8a45646b912fe2a898fc7bbf03386769252abf2f04680386ba`
- status: **CANDIDATE — STL PICKER FIX USER-VERIFIED; FULL RELEASE REGRESSION STILL PENDING**

Format regression pack:
- one asymmetric 40 x 30 x 20 mm L-shaped object generated in GLB, embedded glTF, binary STL, ASCII STL, OBJ, 3MF, AMF, X3D, ASCII PLY, OFF, STEP and STP;
- both STL variants validated independently before device testing;
- STEP/STP round-tripped through CadQuery/Open CASCADE at the expected dimensions.

User verification on 2026-09-26: both generated STL files became selectable and STL loading now works in v0.5.1. The STL picker compatibility defect is CLOSED. Full v0.5.1 release regression is still pending, so v0.4.0 remains the overall VERIFIED baseline.

**Exact next action:** test the existing glTF/GLB animation controls with a dedicated three-clip animation fixture (whole-model spin, arm swing, top-block bounce). Verify `Info` reports 3 animations, `Anim` pauses/resumes, and `Next` cycles 1→2→3→1.

## Animation controls user verification — 2026-09-26
User tested the generated three-clip GLB fixture and confirmed the animation controls work.

Verified behavior:
- animated GLB loads;
- `Anim` play/pause works;
- `Next` cycles embedded animation clips;
- multi-animation playback is visibly functional.

Feature status:
- glTF/GLB animation play/pause: **USER-VERIFIED**
- next-animation cycling: **USER-VERIFIED**

This verification is feature-specific. The overall last fully regression-verified release remains v0.4.0 until the complete v0.5.1 regression checklist is run.

**Next feature:** auto-rotate / turntable viewing with adjustable speed and direction. Implement on a separate v0.6.0 feature branch, preserving the v0.5.1 checkpoint.

## v0.6.0 implementation plan
Goal: add continuous auto-rotate/turntable viewing with adjustable speed and direction while preserving all existing viewer controls.

Design:
- keep the existing top/bottom button layout unchanged;
- put Auto-rotate controls inside the existing **Display** dialog;
- keep a reference to the same Filament ORBIT `Manipulator` used by ModelViewer;
- synthesize small orbit drag updates from the existing Choreographer frame loop;
- manual touch immediately stops Auto-rotate before normal touch orbit/pan/zoom proceeds;
- loading a new model or pressing Fit stops Auto-rotate to avoid stale manipulator state;
- speed options: Slow / Normal / Fast;
- direction options: Left / Right;
- no renderer/importer rewrite.

Expected changed files:
- `app/src/main/java/com/edgar/viewer3d/MainActivity.kt`
- `app/build.gradle.kts`
- `.github/workflows/android-build.yml`
- roadmap/testing/help documentation only as needed.

Test ID: `T-014` in `TESTING.md`.

## v0.6.0 auto-rotate user verification — 2026-09-27
User installed the v0.6.0 APK and reported that Auto-rotate "seems to work."

Build record:
- GitHub Actions run: `36299156628`
- result: **SUCCESS**
- build source commit: `b1b559a1f61cb59b32f96d9e0882b98e3594d63b`
- APK artifact: `Android3DViewer-v0.6.0-debug-arm64`
- artifact ID: `10925590680`
- artifact ZIP digest: `sha256:ea52a71b2097b0b65c2c31ae79c39f453bf92fd6796484933c4cc2a5c554c994`

Feature verification:
- Auto-rotate / turntable: **USER-VERIFIED**
- existing manual orbit/pan/zoom remained usable during this test
- full application regression remains incomplete; v0.4.0 remains the last fully regression-verified baseline

Next feature: Perspective / Orthographic projection switch as v0.7.0 on a separate branch.

## v0.7.0 projection implementation plan
Goal: add a real Perspective / Orthographic camera switch while preserving orbit, pan, pinch zoom, Fit, Auto-rotate, animation, importers, and rendering.

Design:
- keep the existing UI layout unchanged;
- add `Projection: Perspective/Orthographic` inside the existing **Display** dialog;
- use Filament `Camera.Projection.ORTHO` for orthographic mode;
- use ModelViewer's existing lens projection for perspective mode;
- compute orthographic half-height from the ORBIT manipulator's current eye-to-target distance and Filament's 24 mm vertical sensor / 28 mm default focal length relationship so pinch zoom remains visually meaningful;
- reapply orthographic projection during frames so Android surface resize cannot silently restore perspective;
- switching back to Perspective explicitly restores ModelViewer's lens projection;
- Auto-rotate remains compatible in either projection mode;
- no importer, OCCT, STL, animation, or GLB conversion changes.

Expected changed files:
- `app/src/main/java/com/edgar/viewer3d/MainActivity.kt`
- `app/build.gradle.kts`
- `.github/workflows/android-build.yml`
- roadmap/project documentation only as needed.

Test ID: `T-015` in `TESTING.md`.

## v0.7.0 projection device-test failure — 2026-09-27
User device test result:
- switching Perspective <-> Orthographic works;
- orbit, pan, rotate and Fit remain usable;
- Orthographic starts extremely close to the model;
- pinch zoom has no visible effect in Orthographic.

Root cause confirmed against Filament 1.77.1 `OrbitManipulator`:
- ORBIT `scroll()` moves both camera eye and target together along the gaze direction;
- therefore eye-to-target distance remains approximately constant;
- v0.7.0 incorrectly used eye-to-target distance as the orthographic half-height basis;
- in Filament's ORBIT bookmark, target is only one unit in front of eye, so v0.7.0 produced an orthographic half-height of roughly `1 * 12 / 28 = 0.43`, much too tight for the normalized two-unit model;
- because that eye-to-target distance does not change during scroll, pinch zoom also appeared disabled.

Corrective design for v0.7.1:
- keep the model normalized around viewer world center `(0,0,-4)`;
- obtain current eye and gaze from the shared camera manipulator;
- compute depth to the fixed model center using the dot product of `modelCenter - eye` with the normalized gaze direction;
- derive orthographic half-height from that depth using the same 24 mm sensor / current focal-length relationship;
- this depth changes when Filament scroll moves the camera forward/back, but remains stable under image-plane pan and orbit around the model;
- no importer, renderer, STL, STEP, animation or auto-rotate changes.

v0.7.0 projection status: **FAILED DEVICE TEST — DO NOT PROMOTE**.
Next candidate: **v0.7.1 projection framing/zoom fix**.

## v0.8.0 diagnostics and guided-testing architecture
User supplied a program-agnostic diagnostic/testing architecture and explicitly required it to be adapted to this software's actual features rather than copied from another project.

Inspection found the actual viewer interaction surface:
- Open / Android Files / Open With
- Recent
- Fit
- Info
- Shot
- Anim / Next
- Quality
- Display backgrounds, lighting, sun, Auto-rotate speed/direction, Projection
- one-finger orbit
- two-finger pan/pinch
- background model read/import
- GLB repair/conversion
- native STEP processing
- screenshot callback/save
- automatic render/animation/auto-rotate state

No text-entry controls, keyboard shortcuts, sliders, or import-cancel action currently exist, so diagnostics do not invent them.

v0.8.0 source adds:
- central structured JSONL diagnostic sessions with UUID, UTC/elapsed time, sequence, app/build, request/operation correlation, active test/step and structured details;
- bounded recent-event history and bounded session retention;
- safe environment/input metadata;
- chronological caught-error logging with stack traces;
- uncaught-crash preservation and previous-crash export;
- semantic user-action / request / state / result separation;
- result verification for model display, screenshot output, touch camera changes and auto-rotate;
- staged STEP/OCCT diagnostics;
- permanent guided-test definitions for Core Viewer, Display Controls, Animation and File Workflow;
- objective evidence gating plus human visual confirmation where needed;
- Expected Behavior Failed / Blocked / Cancel controls;
- persisted guided-test current-step/result state for interrupted tests;
- explicit local Export Diagnostics and Export Test + Diagnostics ZIP;
- privacy rule: no automatic upload and no source 3D model content in the package.

Every future important feature must add/update its action logging, internal result logging, error path, test criteria, PASS/FAIL conditions and diagnostic-export relevance.

Known v0.8.0 gaps that remain honest roadmap work:
- parser-specific progress percentages are not universal;
- import cancellation is not implemented;
- long-operation stall watchdog is not yet implemented;
- detailed in-native-loop OCCT progress is not exposed;
- GPU/FPS/backend diagnostics remain future work.

The v0.7.1 projection fix was checkpointed before diagnostics work. v0.8.0 inherits it but does not make it verified.

## v0.8.0 first real diagnostic package — 2026-09-27
User exported and supplied the first real v0.8.0 diagnostic ZIP after running the built-in `VIEWER_CORE` guided test.

Observed package result:
- app version 0.8.0 / build code 10;
- guided test `VIEWER_CORE`: **PASS**;
- `CORE_LOAD`: PASS using `test_object_binary.stl`;
- `CORE_ORBIT`: PASS with objective camera delta;
- `CORE_PAN_ZOOM`: PASS with objective multi-touch camera delta;
- `CORE_FIT`: PASS;
- `CORE_INFO`: PASS;
- `CORE_SCREENSHOT`: PASS;
- screenshot output verified non-empty at 3,022,988 bytes;
- no ERROR events;
- no WARNING events;
- event sequence was continuous from 1 through 127;
- the STL input was recognized as 60 vertices / 20 triangles with expected 40 x 30 x 20 source bounds.

This proves that the core guided-test flow, structured event trace, result evidence, screenshot verification, package creation, and user export path are functioning on-device.

Two diagnostic-system defects were found from the package itself:
1. the exported `events.jsonl` ended at `EXPORT_REQUESTED` because `EXPORT_COMPLETED` was written only after the ZIP snapshot was created;
2. completed `guided_test_results.json` retained stale `evidenceKeysForCurrentStep` even though no step was active.

v0.8.0 diagnostic status: **PARTIAL USER VERIFICATION — CORE SUITE PASSED, EXPORT METADATA DEFECTS FOUND**.

v0.8.1 corrective scope:
- create a two-pass diagnostic export so the final ZIP contains a self-contained `export_result.json` and a trace including the persisted-export stage;
- final export destination is rewritten and verified before success is returned;
- partial destination is removed if finalization fails;
- terminal guided-test states clear transient evidence;
- `evidenceKeysForCurrentStep` includes only required evidence for an actually active step.

No renderer/importer/viewer feature behavior is intentionally changed by v0.8.1.

## v0.8.2 testing access and coverage plan
User feedback from first guided-test use:
- Test This Version is too hidden inside Help;
- access can feel obstructed by the viewer's on-screen controls;
- long Help/test dialogs do not scroll comfortably;
- every important existing/new feature must have an associated test rather than only the original four guided suites.

v0.8.2 scope:
- make **Test** a permanent bottom-row button;
- move Help and Export Diagnostics into a compact Testing Center so Test is one tap away;
- active tests use the same Test button as the direct review/check-result entry point;
- make long instruction/review/help content use explicit ScrollView-backed dialog content;
- make the test-selection list compact and easier to scroll;
- expand guided coverage for supported file-format importers and robustness workflows;
- add parser-specific diagnostic evidence for binary vs ASCII STL so those two parser paths can be tested independently;
- preserve existing viewer controls/features; Help is relocated, not removed;
- do not start the named-view feature until this testing-access candidate compiles cleanly.

Future rule:
Every important new feature must ship with its diagnostic events and a guided/regression test in the same feature version. A feature cannot move from CANDIDATE to VERIFIED without its defined test.

## v0.8.2 on-device guided-test results
The user ran the v0.8.2 guided suites and supplied exported diagnostic ZIPs.

Verified PASS suites from supplied packages:
- VIEWER_CORE: all 6 steps PASS;
- DISPLAY_CONTROLS: all 6 steps PASS, including corrected Projection behavior;
- ANIMATION: all 3 steps PASS;
- FORMAT_IMPORTS: all 12 current format steps PASS (GLB, glTF, binary STL, ASCII STL, OBJ, 3MF, AMF, X3D, PLY, OFF, STEP, STP);
- FILE_WORKFLOW: both steps PASS.

ROBUSTNESS:
- model replacement PASS;
- invalid-file safe failure PASS;
- recovery after failure PASS;
- Android Open With BLOCKED in that run because required OPEN_WITH_RECEIVED / MODEL_DISPLAYED evidence was not observed.

DIAGNOSTIC_SYSTEM:
- guided step FAIL due to a test-navigation design defect;
- while a guided test was active, the permanent Test button opened only the current-step review;
- the test instruction required Test → Export Diagnostics, but Export Diagnostics was unreachable while the test was active;
- the user correctly pressed Expected Behavior Failed;
- the subsequent exported package itself contains export_result.json with status COMPLETED, proving the exporter worked and the first real failure was the guided-test navigation path, not ZIP creation.

v0.8.3 corrective scope:
- while a guided test is active, Test continues to return directly to the current step review so the user's place is preserved;
- the user does not re-enter the test-selection menu or restart the test;
- DIAGNOSTIC_SYSTEM's Do Step button launches Export Diagnostics directly;
- after a successful export when DIAGNOSTIC_EXPORT_COMPLETED is the current required evidence, the app automatically opens the same step review;
- after PASS, the controller advances to the next step as before;
- test criteria are unchanged; no threshold/evidence weakening.

The next named-views feature must inherit this v0.8.3 navigation correction before release.

## Anti-loop / development rules
1. Never silently remove a working format or feature to fix another feature.
2. Make one coherent version step at a time and update the roadmap status.
3. Preserve a buildable checkpoint before risky renderer/importer refactors. Native/CAD integrations must be developed on a feature branch while `main` stays on the last tested APK.
4. Prefer tests and diagnostics over repeated speculative rewrites.
5. If a build fails, read the exact compiler/action log and fix the actual failing API/file.
6. Do not repeatedly retry the same failed fix without changing the hypothesis.
7. Keep importers separated from renderer/UI so format bugs do not destabilize the viewer.
8. New user-facing features should include a short Help entry or discoverable control.

## Versioning
Use semantic development versions: v0.1.0, v0.2.0, etc. Minor releases should represent a testable feature group, not arbitrary edits.

## Testing checklist for every viewer release
- App starts without a model.
- File picker opens.
- GLB loads.
- At least one generated/imported STL loads.
- Orbit / pan / zoom work.
- Fit/reset works.
- Rotating the phone does not lose the renderer.
- Opening a second model replaces the first cleanly.
- Invalid/unsupported files show an error instead of crashing.
- Screenshot works.
- Recent file can be reopened when Android permission persists.
- Animated GLB can play/pause when animation exists.
