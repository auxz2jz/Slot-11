# PROJECT MEMORY — Android 3D Viewer

## Canonical repository
- Repository: **auxz2jz/Slot-11**
- Project: Android 3D Viewer / Model Inspector
- Package: `com.edgar.viewer3d`
- Current development version: **v0.3.0**
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

## Anti-loop / development rules
1. Never silently remove a working format or feature to fix another feature.
2. Make one coherent version step at a time and update the roadmap status.
3. Preserve a buildable checkpoint before risky renderer/importer refactors.
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
