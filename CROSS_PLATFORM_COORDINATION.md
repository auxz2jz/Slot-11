# Cross-Platform Coordination — 3D Viewer / Model Inspector

This file is the shared product/feature coordination layer for Android/mobile ChatGPT, normal ChatGPT, and any future Windows/PC agent working in `auxz2jz/Slot-11`.

**Principle:** ONE PRODUCT / SHARED INTENT / SEPARATE PLATFORM IMPLEMENTATIONS.

## Current platform status

- **Android VERIFIED:** v0.4.0, source `82accea784de4b31ebfc9af48be51bcaff6af229`.
- **Android CANDIDATE:** v0.5.0 STEP/STP via OCCT, CI build succeeded, not user-verified.
- **Windows/PC:** NOT STARTED.

## Ownership zones without moving existing files

### Android-owned

- `app/**`
- root/app Gradle files
- `.github/workflows/android-build.yml`
- `PROJECT_MEMORY.md`
- `3D_VIEWER_ROADMAP.md`
- `TESTING.md`
- `DIAGNOSTICS.md`
- `STEP_TEST_PLAN.md`
- Android test fixtures
- Android candidate/verified records and APK artifacts

### Shared coordination/reference

- `README.md`
- `MASTER_RULE_ADOPTION.md`
- `CROSS_PLATFORM_COORDINATION.md`
- `THIRD_PARTY_NOTICES.md` where a dependency affects shared product/legal understanding

Before changing a shared file, fetch/read its latest repository version and make the smallest necessary edit.

### Windows/PC-owned

No Windows implementation exists yet.

When Windows work begins, create a clearly isolated `windows/` area or another explicitly agreed Windows-only path containing Windows source, build files, memory/checkpoint, roadmap, tests, diagnostics, candidate records, and release artifacts.

A Windows/PC agent must not reorganize or move the existing Android project merely to create matching directory names.

## Shared product vision

Provide a phone/desktop-friendly but progressively professional 3D model viewer and inspector that can:

- open common 3D printing, mesh, and CAD formats;
- display models with reliable shading/material behavior;
- orbit, pan, zoom, fit, and inspect geometry;
- expose file/model statistics;
- support screenshots and useful display modes;
- support animation where the format provides it;
- grow toward measurements, clipping, annotations, scene/object controls, diagnostics, repair analysis, and comparison;
- preserve reproducible testing and diagnostics across platforms.

Platform UI, libraries, rendering engines, import kernels, performance techniques, and version numbers may differ.

## Shared feature catalog

Stable IDs describe product intent, not identical platform code.

### F-001 — Common model import
Open supported mesh/model files and present valid geometry.

Android: VERIFIED/PARTIAL by format  
Windows: NOT STARTED

### F-002 — Reliable 3D navigation
Orbit, pan, zoom, and fit-to-view.

Android: VERIFIED  
Windows: NOT STARTED

### F-003 — Model/file information
Show format, size, geometry statistics, bounds/dimensions, units where known, and animation count where applicable.

Android: VERIFIED/PARTIAL  
Windows: NOT STARTED

### F-004 — Robust shading/material presentation
Render useful non-black geometry, generate/repair normals when appropriate, preserve available colors/material intent, and provide lighting controls.

Android: VERIFIED/PARTIAL  
Windows: NOT STARTED

### F-005 — Recent-file workflow
Reopen previously granted/recent models where platform permissions allow.

Android: VERIFIED  
Windows: NOT STARTED

### F-006 — Screenshot/image capture
Save an image of the current rendered model view.

Android: VERIFIED  
Windows: NOT STARTED

### F-007 — Quality/display controls
Provide practical performance/quality and background/lighting controls.

Android: VERIFIED  
Windows: NOT STARTED

### F-008 — Model animation
Play/pause/cycle available model animations and later add richer animation inspection.

Android: VERIFIED/PARTIAL  
Windows: NOT STARTED

### F-009 — 3D-printing interchange compatibility
Prioritize STL, 3MF, OBJ, AMF, GLB/glTF and other formats commonly encountered in 3D-printing workflows.

Android: VERIFIED/PARTIAL by format  
Windows: NOT STARTED

### F-010 — CAD STEP/STP import
Translate STEP/STP CAD B-rep data into displayable geometry while retaining normal viewer controls.

Android: CANDIDATE v0.5.0  
Windows: NOT STARTED

### F-011 — Named views / projection controls
Perspective/orthographic and front/back/left/right/top/bottom/isometric views.

Android: PLANNED  
Windows: NOT STARTED

### F-012 — Advanced display inspection
Wireframe, solid+edges, X-ray/transparency, normals/face orientation, axes/grid/ground, material overrides.

Android: PLANNED/PARTIAL  
Windows: NOT STARTED

### F-013 — Scene/object controls
Hierarchy, selection, highlight, hide/show/isolate, transforms, exploded view, per-object overrides.

Android: PLANNED  
Windows: NOT STARTED

### F-014 — Measurement and annotations
Distance, angle, radius/diameter, bounds, area/volume where valid, coordinates, labels, notes, and export.

Android: PLANNED  
Windows: NOT STARTED

### F-015 — Section/clipping inspection
Clipping planes, section presets/box, caps where feasible, and cross-section measurement.

Android: PLANNED  
Windows: NOT STARTED

### F-016 — Mesh diagnostics/repair assistance
Counts, degenerates, duplicate geometry, non-manifold/open boundaries, normals, self-intersection/thin-wall investigation, repair suggestions.

Android: PLANNED  
Windows: NOT STARTED

### F-017 — Model library/productivity
Favorites, thumbnails, folder resources, export/convert, comparison/overlay, batch thumbnails.

Android: PLANNED/PARTIAL  
Windows: NOT STARTED

### F-018 — Built-in diagnostics
Persistent correlated event logging, crash/error preservation, result validation, diagnostic export, privacy/redaction.

Android: PLANNED — documentation adopted  
Windows: NOT STARTED

### F-019 — Guided version testing
Version-specific step-by-step testing with persistent progress, automatic evidence where possible, human confirmation, failure control, and test reports.

Android: PLANNED — documentation adopted  
Windows: NOT STARTED

## Shared requirements

1. A successful build is not a user-verified release.
2. Each platform maintains its own VERIFIED baseline and CANDIDATE versions.
3. Imported geometry must not silently corrupt other working formats.
4. Existing verified format/navigation behavior must be regression-tested after importer or renderer changes.
5. Private source models must not be automatically included in diagnostics.
6. Diagnostics must distinguish user action, attempted operation, actual state/result, error, and test result.
7. Android and Windows may use different renderers/import libraries while implementing the same shared product behavior.
8. Shared feature status must never falsely mark another platform as implemented or verified.
9. STEP/STP requires a genuine CAD/B-rep translation path; platform implementations may choose different suitable CAD kernels.
10. Platform artifacts must remain clearly named and separate.

## Shared decisions

### D-001 — Preserve existing Android layout
Keep the current root Android project and `app/` source tree. Do not migrate it merely to match an example `android/` structure.

### D-002 — Add Windows non-disruptively
When Windows development starts, add an isolated Windows-owned area rather than moving Android.

### D-003 — Filament remains Android presentation renderer
On Android, Google Filament remains the established presentation pipeline unless a future evidence-based architecture change is separately approved/checkpointed.

### D-004 — OCCT is Android CAD-import infrastructure
The Android v0.5.0 candidate uses OCCT headlessly for STEP/STP translation/tessellation while keeping Filament for presentation. Windows is not required to use the same implementation library.

### D-005 — Separate platform versions
Android and Windows version numbers may diverge. Feature IDs provide shared continuity.

### D-006 — Separate platform verification
Android device testing never verifies Windows, and Windows testing never verifies Android.

## Worker startup/handoff procedure

Any worker resuming this repository should:

1. Read `auxz2jz/master-instruction-library/INSTRUCTION_INDEX.md` and all applicable mandatory files.
2. Read `MASTER_RULE_ADOPTION.md`.
3. Read this file.
4. Determine which platform the current task belongs to.
5. Read that platform's project memory/checkpoint, roadmap, testing, and diagnostics.
6. Identify the last user-verified baseline and latest legitimate candidate for that platform.
7. Review shared features/decisions added since the previous session.
8. Do not modify another platform's source/baseline without explicit authorization.
9. Fetch the latest shared file before editing and preserve unrelated content.
10. Record reusable cross-platform discoveries here; keep platform-specific build/compiler details in platform-owned files.

## Future Windows initialization

When the first Windows/PC agent begins:

- create Windows-specific memory/checkpoint, roadmap, testing, and diagnostics records;
- set Windows last verified baseline to NONE until the user physically tests a Windows build;
- review F-001 through F-019 and mark each Windows status honestly;
- reuse shared behavior/format/test intent where useful without copying Android implementation assumptions blindly;
- keep Windows EXE/installer/source artifacts separate from Android APKs.

## Structural migration

No migration is planned now.

If a future structural move would affect Android and Windows ownership areas, checkpoint both platforms, document every path move/build impact, and obtain explicit user direction before proceeding.
