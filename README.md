# 3D Viewer for Android

Canonical repository for Edgar's Android 3D model viewer.

## Version
Current development line: **v0.1.0**

## Core goals
- Fast native Android 3D model viewing.
- Open models from Android Files and from other apps via **Open with**.
- First-class GLB / glTF 2.0 rendering using Google Filament.
- Import common mesh formats (STL, OBJ, PLY, OFF, 3MF) into the same rendering pipeline.
- Touch orbit, pan, zoom, fit-to-view, animation playback, model statistics, screenshots, quality controls, and diagnostics.
- Keep expanding toward a full inspection viewer: measurements, section planes, exploded views, annotations, AR, mesh repair diagnostics, and more formats.

Read **PROJECT_MEMORY.md** first before future work, then **3D_VIEWER_ROADMAP.md**.


## APK testing
GitHub Actions builds a debug APK on every push. Open the latest successful **Android debug APK** workflow run and download the **Android3DViewer-v0.1.0-debug** artifact.

The first verified successful v0.1.0 APK build was produced from commit `b0cf1fe44ea49b423641041cc1faeca656677652`.
