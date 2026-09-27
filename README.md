# 3D Viewer for Android

Canonical repository for Edgar's Android 3D model viewer / model inspector.

## Stable and candidate versions
- **Android VERIFIED baseline:** v0.4.0
  - source commit: `82accea784de4b31ebfc9af48be51bcaff6af229`
  - permanent checkpoint: `stable/v0.4.0-tested`
- **Android CANDIDATE:** v0.5.0 STEP/STP via OCCT
  - feature branch: `feature/occt-step-v0.5.0`
  - CI build succeeded
  - not user-verified yet
- **Windows/PC:** NOT STARTED

## Current viewer formats
Verified/working Android line includes:
- GLB / embedded glTF 2.0
- STL (binary and ASCII)
- OBJ geometry
- 3MF multi-object/component geometry
- AMF geometry
- X3D common triangle geometry
- ASCII PLY
- OFF

The v0.5.0 candidate adds STEP/STP through Open CASCADE Technology (OCCT) and still requires device testing before it can replace the verified baseline.

## Architecture
Google Filament is the canonical Android renderer. Mesh formats use the existing `MeshData -> GLB -> Filament` pipeline. The v0.5.0 candidate uses OCCT headlessly for CAD translation/tessellation; OCCT does not replace Filament or the app UI.

## Required startup order

This existing project adopts the current Master Instruction Library without moving or reorganizing the working Android source.

Before substantial work:

1. Read `auxz2jz/master-instruction-library/INSTRUCTION_INDEX.md` and all applicable mandatory files.
2. Read `MASTER_RULE_ADOPTION.md`.
3. Read `CROSS_PLATFORM_COORDINATION.md`.
4. Read `PROJECT_MEMORY.md`.
5. Read `3D_VIEWER_ROADMAP.md`.
6. Read `TESTING.md`.
7. Read `DIAGNOSTICS.md`.
8. Identify the Android last VERIFIED baseline and latest CANDIDATE before editing source.

## Important project documents

- `PROJECT_MEMORY.md` — Android checkpoint/handoff/source of truth
- `3D_VIEWER_ROADMAP.md` — Android platform roadmap
- `MASTER_RULE_ADOPTION.md` — mapping to the current Master Instruction Library
- `CROSS_PLATFORM_COORDINATION.md` — shared Android/Windows feature and ownership coordination
- `TESTING.md` — release/guided-testing mapping
- `DIAGNOSTICS.md` — diagnostics-standard mapping and incremental plan
- `STEP_TEST_PLAN.md` — v0.5.0 STEP/STP candidate testing on the feature branch
- `THIRD_PARTY_NOTICES.md` — third-party notices for the STEP candidate

## Development rule
A successful build is a **CANDIDATE**, not a VERIFIED release. Keep the exact v0.4.0 verified source intact until a newer candidate is physically tested and confirmed by the user.

## v0.8.0 diagnostic candidate
The diagnostics branch adds program-specific structured diagnostics, Help -> Test This Version, and explicit local Export Diagnostics. Diagnostics remain local until the user exports them; source 3D model contents are not included automatically. A build is still only a CANDIDATE until device testing succeeds.
