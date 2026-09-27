# Master Instruction Library Adoption

**Adopted:** 2026-09-26  
**Project:** Android 3D Viewer / Model Inspector  
**Repository:** `auxz2jz/Slot-11`

This existing project adopts the current rules from `auxz2jz/master-instruction-library` without reorganizing, renaming, moving, or rewriting working source merely to resemble the examples in the Master Instruction Library.

## Master rules read for this adoption

- `INSTRUCTION_INDEX.md` — `0674c836505331c3d02eac8dd1567726e6c82152`
- `CORE_DEVELOPMENT_RECOVERY_RULES.md` — `a6a1e0b31934337dbb04ed26f6e73958e38921d3`
- `DIAGNOSTICS_STANDARD.md` — `6dcc32a063e5c6450c3c9168aff2d00e636d34df`
- `GUIDED_TESTING_STANDARD.md` — `8cec501afbce5f91809d8b960ef5db626c0600e4`
- `CROSS_PLATFORM_COLLABORATION_STANDARD.md` — `f288adc91ada3580a2d36f7b6cec58078ca42a7b`
- `RULE_INTAKE_WORKFLOW.md` — `74b8381bd0bb6f7634352c55a0754ecdc94c10f9`
- `RULE_CHANGELOG.md` — `e351c0428da9f71166e055c655ac1a3669ba216e`

The Cross-Platform standard is applicable because this repository may be worked on by Android/mobile ChatGPT, normal ChatGPT, and a future Windows/PC agent.

## Pre-adoption verified checkpoint

The repository was inspected before adoption changes.

### Android last user-verified baseline

- Version: **v0.4.0**
- Status: **VERIFIED**
- Exact source commit: `82accea784de4b31ebfc9af48be51bcaff6af229`
- Permanent checkpoint branch: `stable/v0.4.0-tested`
- Successful GitHub Actions run: `36183659084`
- APK artifact: `Android3DViewer-v0.4.0-debug`
- Artifact ID: `10884956702`
- Artifact ZIP SHA-256: `0755e7228a1b6cfab7a4f99532dff1a5f2d44130865b092d27e0fc56367edf1e`
- Extracted APK SHA-256: `ec60c88a9cc767dacbd4e547370eb2c3bf63dbb222aa058e962ff54531c5d515`

The user physically tested v0.4.0 and identified it as the latest working version.

### Latest Android candidate

- Version: **v0.5.0 STEP/STP via OCCT**
- Status: **CANDIDATE — CI BUILT, NOT USER VERIFIED**
- Feature branch: `feature/occt-step-v0.5.0`
- Successful build source commit: `5625660add9ee76d708ba90acf83e1729e0f493a`
- GitHub Actions run: `36184091026`
- Run result: **SUCCESS**
- APK artifact: `Android3DViewer-v0.5.0-debug-arm64`
- Artifact ID: `10886586045`
- Artifact ZIP SHA-256: `b9bec51841664ec009c86c303054a7c44bbb9d2a32297b6c4aa050f06eb6244d`
- Extracted APK SHA-256: `c4187753d08fd49311c0db74c7bcd8666a84102e88e92b205be19c77b8ad8ea8`
- Later feature-branch commits through `a0226cdf4e3b15daf7f106405e919ade3403c2fa` changed only project memory, the STEP test plan, and STEP test fixtures; they did not change Android application source after the successful build.

v0.5.0 must not replace v0.4.0 as the verified baseline until the user tests it on-device.

## Existing structure mapped to the Master rules

The existing layout is retained.

| Existing path | Master-rule role | Ownership |
| --- | --- | --- |
| `app/**` | Android implementation source | Android worker |
| root Gradle files and `app/build.gradle.kts` | Android build configuration | Android worker |
| `.github/workflows/android-build.yml` | Android CI/build workflow | Android worker |
| `PROJECT_MEMORY.md` | Android project memory/checkpoint/handoff | Android worker |
| `3D_VIEWER_ROADMAP.md` | Android platform roadmap | Android worker |
| `TESTING.md` | Android testing/guided-test documentation | Android worker |
| `DIAGNOSTICS.md` | Android diagnostic standard mapping/plan | Android worker |
| `STEP_TEST_PLAN.md` | v0.5.0 STEP-specific candidate test plan | Android worker |
| `test-fixtures/**` | Test data/fixtures | Android/reference |
| `THIRD_PARTY_NOTICES.md` | Third-party dependency/license record | Shared/reference |
| `README.md` | Repository entry point | Shared entry point |
| `CROSS_PLATFORM_COORDINATION.md` | Shared product/feature/ownership coordination | Shared |
| `MASTER_RULE_ADOPTION.md` | Master-rule mapping and adoption record | Shared |

A future Windows/PC implementation should be added in a new isolated `windows/` area or another explicitly agreed Windows-only path. Do not move the existing Android project merely to create symmetry.

## Core development/recovery mapping

Already present:

- durable `PROJECT_MEMORY.md`
- exact stable checkpoint branch
- verified v0.4.0 source commit and artifact hashes
- separate v0.5.0 candidate branch
- roadmap
- known format limitations
- anti-loop/recovery history for the OCCT STEP integration
- CI build workflow and artifacts
- STEP regression fixture and candidate test plan

Safeguards adopted for all future work:

1. Read the Master Instruction Library first.
2. Read this adoption file and `CROSS_PLATFORM_COORDINATION.md`.
3. Read `PROJECT_MEMORY.md`, roadmap, `TESTING.md`, and `DIAGNOSTICS.md`.
4. Identify the platform being worked on.
5. Identify that platform's last user-verified baseline and latest legitimate candidate.
6. Record the current task, implementation plan, and expected changed files before substantial source changes.
7. Checkpoint before risky/refactoring/architecture changes.
8. Make the smallest evidence-based change.
9. Build/test before unrelated feature work.
10. Record result, failures, diagnostics, candidate status, and exact next action before ending work.

## Diagnostics standard mapping

### Present today

The app currently has user-visible status text, Toasts, AlertDialogs, model statistics, screenshot output, importer exceptions, and CI logs.

### Not yet implemented as Master-standard diagnostics

Repository inspection found no central structured persistent event logger, no `USER_ACTION`/`OPERATION_START`/`RESULT` event stream, no bounded Action Trace, no global crash preservation, no diagnostic ZIP export, and no stall/watchdog system.

These gaps are documented in `DIAGNOSTICS.md`.

They must be added incrementally. Do not rewrite working import/render code merely to add diagnostics, and do not inject unrelated diagnostic refactors into the current STEP candidate before its device test unless specifically required to diagnose a failure.

## Guided testing standard mapping

### Present today

- release regression checklist in `PROJECT_MEMORY.md`
- explicit v0.5.0 STEP/STP device plan in `STEP_TEST_PLAN.md`
- real STEP regression fixture in `test-fixtures/occt_screw.step`
- user visual confirmation is already treated as authoritative for VERIFIED status

### Not yet implemented

There is no in-app **Test This Version** workflow, persistent test-session state, automatic result-source classification, or TXT/JSON test report export.

These gaps and the incremental adoption plan are documented in `TESTING.md`.

## Cross-platform/multi-agent adoption

See `CROSS_PLATFORM_COORDINATION.md`.

Key rules:

- Android and Windows maintain separate source ownership, candidate versions, verified baselines, checkpoints, builds, tests, diagnostics, and artifacts.
- Windows/PC is currently **NOT STARTED**.
- A future Windows/PC agent must not modify `app/**`, Android Gradle/build files, Android project memory, Android tests/diagnostics, or Android baseline records without explicit authorization.
- Android work must not modify a future Windows implementation without explicit authorization.
- Shared-file changes must start from the latest repository version and preserve unrelated edits.
- Shared feature intent propagates through stable feature IDs; platform implementations may differ.

## Structural migration decision

**No structural migration is being performed.**

Moving the current Android project into an example `android/` folder would add build/path risk without improving the working app. The Master Library explicitly allows equivalent existing layouts.

If a structural migration becomes genuinely beneficial later:

1. record the reason and exact path-move plan;
2. checkpoint all affected platform states;
3. preserve every verified baseline;
4. identify every build/workflow path affected;
5. obtain explicit user direction before moving working source;
6. perform migration separately from feature work;
7. rebuild and retest all affected verified behavior.

The preferred non-disruptive expansion is to leave Android where it is and add a separate Windows-only area when Windows development actually starts.
