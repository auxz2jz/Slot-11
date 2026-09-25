# PROJECT MEMORY — Android 3D Viewer

## Canonical repository
- Repository: **auxz2jz/Slot-11**
- Project: Android 3D Viewer / Model Inspector
- Package: `com.edgar.viewer3d`
- Last device-tested stable version on `main`: **v0.4.0** (`82accea784de4b31ebfc9af48be51bcaff6af229`)
- Current feature branch under development: **v0.5.0 STEP/STP via OCCT** (`feature/occt-step-v0.5.0`)
- This file is the first source of truth for future work.
- Read this file before making changes. Then read `3D_VIEWER_ROADMAP.md`.

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
- Do not merge v0.5.0 into `main` until the feature-branch APK compiles successfully and STEP/STP is tested on-device.
- Do not poll the same long-running workflow repeatedly. Inspect the final job result/log once it completes; if it fails, fix the exact reported failure before starting another run.

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
