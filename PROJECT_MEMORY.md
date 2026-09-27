# PROJECT MEMORY — Android 3D Viewer

## Canonical repository
- Repository: **auxz2jz/Slot-11**
- Project: Android 3D Viewer / Model Inspector
- Package: `com.edgar.viewer3d`
- Last device-tested stable version on `main`: **v0.4.0** (`82accea784de4b31ebfc9af48be51bcaff6af229`)
- Permanent tested checkpoint branch: `stable/v0.4.0-tested`
- Current feature branch checkpoint: **v0.5.1 STL picker compatibility + STEP/STP via OCCT** (`feature/occt-step-v0.5.0`)
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

**Exact next development/test action:** preserve the v0.5.1 checkpoint, then implement auto-rotate / turntable viewing as v0.6.0 with adjustable speed and direction. Do not alter the verified STL or animation paths unnecessarily.

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
