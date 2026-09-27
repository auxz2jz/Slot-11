# Android 3D Viewer Testing

This file maps Slot-11 to the Master Instruction Library `GUIDED_TESTING_STANDARD.md`.

## Current status

The project currently uses manual release checklists and candidate-specific plans.

Existing test documentation:

- release checklist in `PROJECT_MEMORY.md`
- v0.5.0 STEP/STP plan in `STEP_TEST_PLAN.md`
- STEP fixture `test-fixtures/occt_screw.step`

There is currently no in-app **Test This Version** workflow. That is a documented future safeguard, not a reason to rewrite working source immediately.

## Baseline and candidate under test

### VERIFIED baseline

- Android v0.4.0
- source commit `82accea784de4b31ebfc9af48be51bcaff6af229`
- checkpoint branch `stable/v0.4.0-tested`
- user-tested and confirmed working

### Current candidate

- Android v0.5.0 STEP/STP via OCCT
- successful build source `5625660add9ee76d708ba90acf83e1729e0f493a`
- feature branch `feature/occt-step-v0.5.0`
- CI build succeeded
- status: **CANDIDATE / UNTESTED ON USER DEVICE**

Compilation success must never be recorded as user verification.

## Standard release regression test IDs

These stable IDs describe test intent. UI labels should use the actual visible app labels.

### T-001 — Launch
**WHAT TO DO:** Launch the app with no model selected.  
**EXPECTED:** App opens without crashing and shows the viewer controls.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-002 — File picker
**WHAT TO DO:** Tap **Open**.  
**EXPECTED:** Android Files picker opens and can return to the app.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-003 — GLB load
**WHAT TO DO:** Open a known-good GLB.  
**EXPECTED:** Visible correctly shaded model appears; no fatal error.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-004 — STL load
**WHAT TO DO:** Open a known-good STL.  
**EXPECTED:** Geometry and shading appear correctly.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-005 — 3MF multi-object load
**WHAT TO DO:** Open a known-good multi-object 3MF.  
**EXPECTED:** All parts appear in correct positions with valid geometry.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-006 — Orbit/pan/zoom
**WHAT TO DO:** Orbit with one finger, then pan/pinch zoom with two fingers.  
**EXPECTED:** Camera responds without losing the model.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-007 — Fit
**WHAT TO DO:** Tap **Fit**.  
**EXPECTED:** Model returns to usable framing.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-008 — Rotation/configuration
**WHAT TO DO:** Rotate the phone while a model is loaded.  
**EXPECTED:** Viewer remains usable and model is not lost.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-009 — Replace model
**WHAT TO DO:** Open a second model.  
**EXPECTED:** New model replaces the previous model cleanly.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-010 — Invalid file handling
**WHAT TO DO:** Attempt to open an invalid/unsupported model.  
**EXPECTED:** Readable error; app remains running and can load a valid model afterward.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-011 — Screenshot
**WHAT TO DO:** Load a model and tap **Shot**.  
**EXPECTED:** PNG is saved and app reports success.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-012 — Recent
**WHAT TO DO:** Reopen a previously granted model through **Recent**.  
**EXPECTED:** Model reloads when Android permission remains valid.  
**Result source today:** MANUAL_PASS / MANUAL_FAIL.

### T-013 — Animation
**WHAT TO DO:** Open the generated three-clip animated GLB, use **Anim** and **Next**.  
**EXPECTED:** **Anim** pauses/resumes motion and **Next** cycles distinct embedded animation clips.  
**Latest result:** **MANUAL_PASS — user verified 2026-09-26.**

### T-014 — Auto-rotate / turntable
**WHAT TO DO:** Load a static asymmetric model, open **Display**, enable Auto-rotate, change speed and direction, then touch-drag the model.
**EXPECTED:** Model/camera orbits continuously; speed changes are obvious; direction reverses; manual touch safely stops auto-rotate so normal orbit/pan/zoom remains usable.
**Latest result:** **MANUAL_PASS — user verified 2026-09-27.**

### T-015 — Perspective / Orthographic projection
**WHAT TO DO:** Load the asymmetric test model, open **Display**, switch Projection between Perspective and Orthographic, then orbit/pan/pinch zoom in both modes.
**EXPECTED:** Perspective shows normal depth convergence; Orthographic removes perspective foreshortening while keeping the model framed. Orbit/pan/pinch zoom remain usable. Switching back to Perspective restores the normal camera projection.
**v0.7.0 result:** **MANUAL_FAIL — 2026-09-27.** Mode switching/orbit/pan/Fit worked, but Orthographic framed far too tightly and pinch zoom had no visible effect.
**Retest target:** v0.7.1 after depth-to-model-center projection fix.

## v0.5.0 STEP/STP candidate tests

`STEP_TEST_PLAN.md` remains the detailed STEP-specific plan.

Stable test IDs:

- `T-STEP-001` file picker accepts `.step`
- `T-STEP-002` pinned OCCT screw STEP loads visibly
- `T-STEP-003` Info reports non-zero geometry
- `T-STEP-004` orbit/pan/zoom/Fit work after STEP import
- `T-STEP-005` screenshot works after STEP import
- `T-STEP-006` another model can replace STEP cleanly
- `T-STEP-007` `.stp` extension uses same importer
- `T-STEP-008` Android Open With works for STEP/STP
- `T-STEP-009` invalid STEP fails safely
- `T-STEP-010` at least one user-generated STEP/STP loads successfully

The candidate must also pass T-001 through T-013 where applicable before merge to the verified line.

## Manual failure control

Until an in-app guided test exists, the user can report a problem in chat or provide screenshots/files.

Record a manual failure with:

- test ID/step
- app version/build
- what was tapped
- expected behavior
- actual behavior
- model/file type
- whether the app crashed
- any diagnostic package or screenshot supplied

Do not mark the test PASS because a button was pressed or because CI built the APK.

## Future in-app Test This Version

Implement incrementally after the current STEP candidate is validated.

The eventual in-app workflow should persist:

- test session ID
- test definition/version
- app version/build
- start/end time
- current/completed step
- result source
- automatic evidence
- tester notes
- first failed step
- overall result

Each step should include:

- stable step ID
- WHAT TO DO
- EXPECTED RESULT
- automatic verification when practical
- PASS/FAIL criteria
- timeout when appropriate
- evidence to collect
- diagnostic export instructions

Result-source values should include:

- AUTO_VERIFIED
- AUTO_FAIL
- MANUAL_PASS
- MANUAL_FAIL
- MANUAL_OVERRIDE
- BLOCKED
- SKIPPED
- UNTESTED

## Test reports

Future guided testing should export both:

- human-readable TXT
- structured JSON

Failed tests should link to the relevant diagnostic session/event IDs.

## Feature completion rule

Every new user-facing feature must define its test before being marked DONE.

A feature is:

- IMPLEMENTED/CANDIDATE when code exists and builds;
- VERIFIED only after required testing succeeds, including user visual confirmation where needed.

Raw chronological diagnostics override a false-positive summary.

## Supported-format corpus test
Use the generated `Android3DViewer_Supported_Format_Test_Pack.zip` and `FORMAT_TEST_CORPUS.md`.

Recommended order:
1. binary STL
2. ASCII STL
3. GLB
4. embedded glTF
5. OBJ
6. 3MF
7. AMF
8. X3D
9. ASCII PLY
10. OFF
11. STEP
12. STP

For every failure, record the exact filename, whether the file appeared in Android Files, whether selection returned to the app, and the exact error dialog/status text.

STEP gray rendering is not itself a geometry-test failure in v0.5.x; color metadata preservation is currently a separate TODO.
