# 3D Viewer for Android

Canonical repository for Edgar's Android 3D model viewer.

## Stable and development versions
- **Stable/device-tested:** v0.4.0 on `main`
- **Current feature work:** v0.5.0 STEP/STP support on `feature/occt-step-v0.5.0`

## Current viewer formats
- GLB / embedded glTF 2.0
- STL (binary and ASCII)
- OBJ geometry
- 3MF multi-object/component geometry
- AMF geometry
- X3D common triangle geometry
- ASCII PLY
- OFF
- STEP / STP is being integrated through Open CASCADE Technology (OCCT) on the v0.5.0 feature branch.

## Architecture
Google Filament is the canonical renderer. Mesh formats are converted into the existing `MeshData -> GLB -> Filament` pipeline. OCCT is used headlessly for CAD translation/tessellation; it does not replace Filament or the app UI.

## Working procedure
Read **PROJECT_MEMORY.md** first before future work, then **3D_VIEWER_ROADMAP.md**. Keep `main` at the last tested APK while risky native/CAD work is developed and verified on a feature branch.

## APK testing
GitHub Actions produces versioned debug APK artifacts. v0.4.0 is the last device-tested baseline. v0.5.0 must pass feature-branch CI and STEP/STP device testing before it replaces v0.4.0 on `main`.
