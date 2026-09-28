# Android 3D Viewer Testing

This project follows the Master Instruction Library Guided Testing Standard plus the viewer-specific diagnostic/testing rules adopted by the user.

## Current status

**v0.8.0 CANDIDATE adds a permanent in-app Test This Version framework.**

Tests were created from this 3D viewer's real controls and workflows rather than copied from another application.

## Baseline and feature checkpoints

Last fully regression-verified baseline:
- Android v0.4.0
- source commit 82accea784de4b31ebfc9af48be51bcaff6af229
- checkpoint stable/v0.4.0-tested

Later feature-specific verification:
- v0.5.1 STL picker/import behavior: user-verified
- glTF/GLB Anim + Next: user-verified with a three-clip model
- v0.6.0 Auto-rotate: user-verified
- v0.7.0 Projection: failed device test
- v0.7.1 Projection fix: built/checkpointed, device retest pending
- v0.8.0 Diagnostics/guided testing: VIEWER_CORE user run PASS; export-result metadata defects found
- v0.8.1 Diagnostics export/result fix: checkpointed; build/device validation pending
- v0.8.2 Testing UI + coverage: candidate; direct Test access and expanded guided suites

Compilation success is never user verification.

## Permanent guided-test architecture

Implemented components:
- GuidedTestDefinition
- GuidedTestStep
- GuidedStepResult
- GuidedTestController

Each step defines a stable ID, title, WHAT TO DO, EXPECTED result, required objective evidence, whether human visual confirmation is required, and an optional timeout field.

Result states are PASS, FAIL, PARTIAL, BLOCKED, and NOT_RUN.

Starting a guided test starts a fresh diagnostic session. Meaningful test progress is persisted. Machine-readable output records the current step if interrupted.

## False-positive protection

A button press does not pass a test.

Examples:
- Open does not pass until a model is actually displayed.
- Shot does not pass until a non-empty PNG is verified.
- Auto-rotate does not pass until the camera actually moves.
- Anim requires the playback state change plus tester confirmation of visible behavior.
- Projection requires projection-state/touch evidence plus visual confirmation.

If required objective evidence is missing, the UI refuses to mark the step PASS.

## In-app guided suites

### VIEWER_CORE
1. CORE_LOAD — supported model actually displays
2. CORE_ORBIT — one-finger gesture actually changes camera
3. CORE_PAN_ZOOM — multi-touch gesture actually changes camera
4. CORE_FIT — Fit state is applied and visually confirmed
5. CORE_INFO — model information is actually populated/presented
6. CORE_SCREENSHOT — non-empty PNG is actually saved

### DISPLAY_CONTROLS
1. DISPLAY_QUALITY — Filament quality state changes
2. DISPLAY_BACKGROUND — renderer background state changes
3. DISPLAY_LIGHTING — light intensities are applied
4. DISPLAY_SUN — sun intensity changes
5. DISPLAY_AUTOROTATE — Auto-rotate produces an actual camera change
6. DISPLAY_PROJECTION — projection changes and multi-touch navigation evidence occurs; tester confirms framing/appearance

### ANIMATION
1. ANIM_LOAD — displayed model reports at least two animations
2. ANIM_TOGGLE — playback state changes and tester confirms response
3. ANIM_NEXT — active animation index changes and tester confirms clip

### FILE_WORKFLOW
1. FILE_PICKER_LOAD — Android Files returns a supported file and the viewer displays it
2. FILE_RECENT — a Recent selection is made and the model displays again

## Guided-test controls

The v0.8.0 candidate exposes testing through Help:

- Test This Version
- Do Step
- Looks Correct / Verify Result
- Expected Behavior Failed
- Blocked
- Cancel Test
- Export Test + Diagnostics

During an active test, Help becomes the step-review entry point.

## Machine-readable results

guided_test_results.json includes test ID, diagnostic session ID, overall status, completed/in-progress state, current step if interrupted, step results, durations, messages, and measured evidence values.

guided_test_results.txt is the human-readable counterpart.

## Historical release regression IDs

The existing manual IDs remain useful:
- T-001 Launch
- T-002 File picker
- T-003 GLB load
- T-004 STL load
- T-005 3MF multi-object
- T-006 Orbit/pan/zoom
- T-007 Fit
- T-008 device rotation/configuration
- T-009 replace model
- T-010 invalid file handling
- T-011 screenshot
- T-012 Recent
- T-013 animation
- T-014 Auto-rotate
- T-015 Perspective / Orthographic projection

Known results:
- T-013 manual PASS
- T-014 manual PASS
- T-015 v0.7.0 manual FAIL
- T-015 v0.7.1 retest pending

## STEP/STP tests

STEP_TEST_PLAN.md remains the detailed plan:
- T-STEP-001 picker accepts .step
- T-STEP-002 pinned OCCT screw loads visibly
- T-STEP-003 Info reports non-zero geometry
- T-STEP-004 orbit/pan/zoom/Fit work
- T-STEP-005 screenshot works
- T-STEP-006 another model replaces STEP
- T-STEP-007 .stp uses same importer
- T-STEP-008 Open With works
- T-STEP-009 invalid STEP fails safely
- T-STEP-010 a user-generated STEP/STP loads

STEP gray rendering is not currently a geometry failure; color metadata preservation remains a separate feature gap.

## Every future feature

Every important future user-facing or background feature must add/update:
- user-action logging when applicable
- software-request event
- relevant state transition
- actual result verification
- error logging
- guided test or regression step
- PASS conditions
- FAIL conditions
- diagnostic-export relevance

A feature is CANDIDATE when implemented/buildable and VERIFIED only after required testing succeeds.

## First real guided-test package result — v0.8.0

The user supplied a real exported diagnostic ZIP from the built-in VIEWER_CORE test.

Result: **PASS** for all six steps: CORE_LOAD, CORE_ORBIT, CORE_PAN_ZOOM, CORE_FIT, CORE_INFO, and CORE_SCREENSHOT. The trace contained no ERROR or WARNING events, and screenshot output was objectively verified as non-empty.

The package also exposed two framework defects: stale completed-test evidence keys and no self-contained final export result inside the ZIP. These are corrective targets for v0.8.1 rather than reasons to weaken the test criteria.

v0.8.1 retest requirements:
- run at least one guided test to completion;
- export Test + Diagnostics;
- confirm guided_test_results.json has currentStepId=null and an empty evidenceKeysForCurrentStep after completion;
- confirm export_result.json exists and reports COMPLETED;
- confirm the package event trace includes the export persistence stage;
- confirm no new errors/warnings are introduced by the export finalization pass.

## v0.8.2 testing access / coverage

User feedback showed that Test This Version was too hidden under Help and long dialogs were awkward to scroll. v0.8.2 changes the permanent bottom toolbar so **Test** is directly available. During an active test, that same button opens the current step review. Help and Export Diagnostics remain available through a compact Testing Center.

Long Help, step-instruction, step-review, and result content now use explicit ScrollView-backed dialog content rather than relying on default message scrolling.

Additional permanent guided suites:
- FORMAT_IMPORTS — GLB, embedded glTF, binary STL, ASCII STL, OBJ, 3MF, AMF, X3D, ASCII PLY, OFF, STEP, STP
- ROBUSTNESS — replace model, invalid supported file, recover with valid model, Android Open With
- DIAGNOSTIC_SYSTEM — verified local diagnostic export

Binary and ASCII STL now emit separate test evidence so one parser path cannot falsely pass the other.

DISPLAY_AUTOROTATE now requires objective evidence for actual camera movement, a speed-state change, and a direction-state change.

Permanent rule: every important new feature must add/update its guided/regression test in the same version that introduces the feature.

## v0.8.2 real device suite results

Supplied diagnostic packages show:
- VIEWER_CORE: PASS
- DISPLAY_CONTROLS: PASS
- ANIMATION: PASS
- FORMAT_IMPORTS: PASS for all 12 current formats
- FILE_WORKFLOW: PASS
- ROBUSTNESS: first three steps PASS, Android Open With BLOCKED due to missing Open-With evidence
- DIAGNOSTIC_SYSTEM: FAIL caused by an unreachable Export Diagnostics action while the guided test was active

The DIAGNOSTIC_SYSTEM failure is a test-navigation false block, not an exporter failure. The package generated immediately afterward contains export_result.json with status COMPLETED.

### v0.8.3 retest
1. Start Diagnostics / export.
2. Tap Test.
3. Confirm the Active Test menu exposes Export Diagnostics.
4. Choose Export Diagnostics.
5. Confirm a non-empty ZIP is saved.
6. Confirm the app automatically presents the current step review after export completes.
7. Verify Result must PASS only after DIAGNOSTIC_EXPORT_COMPLETED evidence is present.
8. Export the completed test diagnostics and confirm no loop occurs.
