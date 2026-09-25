# v0.5.0 STEP/STP Device Test Plan

Run this checklist on the first v0.5.0 APK before merging STEP support to `main`.

## A. Regression check — existing v0.4.0 formats
1. Launch with no model.
2. Open a known-good STL and confirm geometry/shading.
3. Open a known-good multi-object 3MF and confirm all parts remain correctly positioned.
4. Open GLB and confirm lighting, orbit, pan, zoom, Fit, Info and screenshot.
5. Reopen a Recent model.

Any regression in the v0.4.0 formats blocks the v0.5.0 merge.

## B. Known-good STEP baseline
Use `test-fixtures/occt_screw.step`.

Expected:
- File picker accepts `.step`.
- Loading does not crash the app.
- Status reports STEP through the OCCT importer.
- A visible screw model appears.
- Orbit, pan, pinch zoom and Fit work through the normal Filament viewer.
- Info reports non-zero vertices and triangles.
- Screenshot works.
- Opening another model afterward replaces the STEP model cleanly.

## C. Extension coverage
- Duplicate/rename the same fixture to `.stp` and confirm the STP extension follows the same importer.
- Try Open With from Android Files for both `.step` and `.stp`.

## D. Failure behavior
- Open a text file renamed to `.step`.
- The app must show a readable import error and remain running.
- Then open a valid STL/GLB to confirm the failed STEP load did not poison the renderer.

## E. Real CAD models
After the known-good fixture passes, test at least:
- a simple single-part STEP exported from Fusion 360 or similar;
- a multi-body/assembly STEP;
- a curved model to inspect tessellation quality;
- one larger STEP to observe load time and memory behavior.

## Merge gate
Do not merge v0.5.0 into `main` until:
- GitHub Actions produces the v0.5.0 arm64 APK;
- the existing v0.4.0 regression checks pass;
- the pinned OCCT screw STEP opens successfully on-device;
- at least one user-generated STEP/STP opens successfully.
