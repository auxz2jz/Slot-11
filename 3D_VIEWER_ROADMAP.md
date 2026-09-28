# Android 3D Viewer Roadmap

Legend: **DONE**, **PARTIAL**, **TODO**

## 1. Core viewer
- DONE Native Android project.
- DONE Filament GLB/glTF renderer.
- DONE GLB auto-repair for missing normals and missing explicit materials.
- DONE Orbit, pan, pinch zoom.
- DONE Fit/reset model.
- DONE File picker.
- DONE Open-with support.
- DONE Model/file info.
- DONE Recent files.
- DONE Screenshot.
- DONE Quality presets.
- DONE Background presets.
- DONE Sun brightness presets.
- DONE System-bar-safe phone layout.
- DONE Compact five-button top and bottom control rows.
- DONE Studio ambient/fill lighting presets.
- DONE glTF animation play/pause and next animation — user-verified with a three-clip GLB fixture.
- DONE v0.6.0 Auto-rotate / turntable with Slow/Normal/Fast speed and Left/Right direction — user-verified.
- CANDIDATE v0.7.1 Perspective / Orthographic projection switch with corrected orthographic framing/zoom; device retest pending.
- CANDIDATE v0.9.0 Front/Back/Left/Right/Top/Bottom/Isometric named views with objective camera-direction verification and a seven-step guided test; device validation pending.
- TODO Save/restore camera bookmarks.

## 2. File formats
- DONE GLB.
- PARTIAL glTF 2.0: embedded/data resources work; external sidecars need folder resolution.
- DONE STL binary + ASCII; imported normals normalized/validated.
- DONE v0.5.1 STL Android picker/Open-With MIME compatibility fix — user-verified.
- PARTIAL OBJ: geometry works; MTL/materials/textures pending.
- PARTIAL PLY: ASCII triangle meshes work; binary pending.
- DONE OFF triangle/polygon meshes.
- PARTIAL 3MF: multi-object builds, local index offsets, build transforms and components work; materials/textures/slicer metadata pending.
- PARTIAL AMF: triangle mesh geometry works, including plain/compressed AMF; advanced materials/constellations pending.
- TODO Draco and Meshopt extension validation.
- TODO FBX (likely Assimp-backed importer).
- TODO Collada DAE.
- TODO USD / USDA / USDC / USDZ investigation.
- PARTIAL STEP / STP: OCCT 8.0.1 Android/JNI tessellation bridge builds successfully; device validation is in progress.
- TODO STEP/STP color/material metadata preservation via an XCAF/STEPCAF-style path after geometry import is verified.
- TODO IGES / IGS through the same OCCT native bridge after STEP validation.
- TODO VRML/WRL.
- PARTIAL X3D: common IndexedFaceSet / IndexedTriangleSet / TriangleSet geometry and basic transforms work; advanced X3D scene features pending.
- TODO point-cloud modes for XYZ / PTS / LAS/LAZ where practical.

## 3. Display modes
- TODO Solid shaded.
- TODO Matcap.
- TODO Unlit.
- TODO Wireframe.
- TODO Solid + edges.
- TODO X-ray / transparency.
- TODO Normals visualization.
- DONE Automatic normal generation for GLB triangle meshes that omit NORMAL attributes.
- TODO Face orientation / backface diagnostic colors.
- TODO UV checker.
- TODO Bounding box.
- TODO World axes triad.
- TODO Grid and adjustable grid spacing.
- TODO Ground plane and shadows.
- PARTIAL Environment/IBL presets: neutral irradiance + Soft/Studio/Bright presets implemented; HDR cubemap environments pending.
- PARTIAL Custom lighting: sun intensity and studio fill setup implemented; interactive direction/color/exposure/tone mapping pending.
- TODO Material override color / metallic / roughness.

## 4. Scene & object controls
- TODO Scene hierarchy/tree for glTF.
- TODO Search nodes/meshes.
- TODO Select by tap.
- TODO Highlight selected object.
- TODO Hide/show.
- TODO Isolate.
- TODO Multi-select.
- TODO Transform inspector.
- TODO Reset object transforms.
- TODO Exploded view slider.
- TODO Per-node opacity/color override.

## 5. Measurement & annotations
- TODO Unit detection and manual unit override.
- TODO Point-to-point distance.
- TODO Edge length.
- TODO Polyline/path length.
- TODO Angle measurement.
- TODO Radius/diameter measurement where detectable.
- TODO Bounding dimensions X/Y/Z.
- TODO Surface area.
- TODO Mesh volume for watertight meshes.
- TODO Coordinate readout.
- TODO Persistent measurement labels.
- TODO Pins/notes/annotations.
- TODO Export measurements/notes.

## 6. Inspection / clipping
- TODO One clipping plane.
- TODO Multiple clipping planes.
- TODO X/Y/Z section presets.
- TODO Section-box / clipping-box.
- TODO Cap section surfaces where feasible.
- TODO Cross-section measurements.
- TODO Interior inspection mode.

## 7. Mesh diagnostics
- TODO Triangle/vertex/object counts for every format.
- TODO Degenerate triangle detection.
- TODO Duplicate vertex/face diagnostics.
- TODO Non-manifold edges.
- TODO Open boundaries / watertight check.
- TODO Reversed normals.
- TODO Self-intersection investigation.
- TODO Thin-wall indicator.
- TODO Mesh repair suggestions.
- TODO Optional safe repair operations with undo/export.

## 8. Animation
- DONE Play/pause active glTF animation.
- DONE Cycle to next animation.
- TODO Animation list by name.
- TODO Timeline/scrubber.
- TODO Playback speed.
- TODO Loop toggle.
- TODO Frame/time readout.
- TODO Morph-target controls.
- TODO Skeleton visualization.

## 9. Library / productivity
- DONE Recent files.
- TODO Favorites.
- TODO Thumbnail cache.
- TODO Model library browser.
- TODO Folder/tree access for sidecar textures/resources.
- TODO Rename/display aliases without touching source.
- TODO Share/export screenshot.
- TODO Export converted GLB.
- TODO Model comparison mode.
- TODO Two-model overlay.
- TODO Batch thumbnail generation.

## 10. Mobile extras
- TODO AR placement (ARCore-capable devices).
- TODO Gyroscope inspection mode.
- TODO Optional turntable capture.
- TODO Full-screen immersive mode.
- TODO Tablet / foldable two-pane layout.
- TODO Stylus-friendly selection/measurement.
- TODO Hardware keyboard/mouse controls.

## 11. Performance & robustness
- DONE Balanced/Quality/Performance presets.
- TODO Large-file progress indicator.
- TODO Long-operation stall watchdog/timeouts where meaningful progress signals become available.
- TODO Cancel import.
- TODO Import memory budget.
- TODO Progressive/deferred resource loading.
- TODO LOD controls.
- TODO FPS/frame-time diagnostics.
- TODO GPU/backend diagnostics.
- CANDIDATE v0.8.0 Persistent structured JSONL diagnostic sessions with request/operation correlation and bounded recent history; device validation pending.
- CANDIDATE v0.8.0 Central error logging + global uncaught-crash preservation with prior event trail; device validation pending.
- CANDIDATE v0.8.0 Explicit local Export Diagnostics ZIP with safe metadata/privacy controls; device validation pending.
- USER-TESTED v0.8.3 Guided **Test This Version** workflow with direct toolbar access, scrollable dialogs, direct return to the current step, evidence-gated PASS, expanded format/robustness/diagnostic suites, and corrected Diagnostics / Export flow.
- PARTIAL v0.8.0 Previous crash record/event trail is preserved for diagnostics; full user-state/session restoration remains TODO.
- PARTIAL Automated importer fixtures and parser tests: real-world STL/OBJ/3MF regression corpus inspected manually; repository-safe generated fixtures still needed.

## 12. Development safeguards
- REQUIRED Every important new feature must add/update its guided/regression test, result evidence, PASS/FAIL criteria, and diagnostics in the same feature version.
- DONE Adopt current Master Instruction Library mapping/documentation without moving working Android source.
- DONE Preserve exact v0.4.0 user-verified checkpoint on `stable/v0.4.0-tested`.
- DONE Separate v0.5.0 STEP candidate from verified baseline.
- DONE Add Android `TESTING.md` and `DIAGNOSTICS.md` adoption plans.
- DONE Add `CROSS_PLATFORM_COORDINATION.md`; Windows/PC remains NOT STARTED.
- TODO Implement diagnostics/guided testing incrementally after the current STEP candidate is device-tested, unless concrete failure evidence requires diagnostics first.
