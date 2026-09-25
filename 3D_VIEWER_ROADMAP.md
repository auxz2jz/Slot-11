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
- DONE glTF animation play/pause and next animation.
- TODO Auto-rotate with adjustable speed/direction.
- TODO Perspective / orthographic projection switch.
- TODO Front/back/left/right/top/bottom/isometric named views.
- TODO Save/restore camera bookmarks.

## 2. File formats
- DONE GLB.
- PARTIAL glTF 2.0: embedded/data resources work; external sidecars need folder resolution.
- DONE STL binary + ASCII.
- PARTIAL OBJ: geometry works; MTL/materials/textures pending.
- PARTIAL PLY: ASCII triangle meshes work; binary pending.
- DONE OFF triangle/polygon meshes.
- PARTIAL 3MF: mesh geometry works; components/materials/textures pending.
- TODO Draco and Meshopt extension validation.
- TODO FBX (likely Assimp-backed importer).
- TODO Collada DAE.
- TODO USD / USDA / USDC / USDZ investigation.
- TODO STEP / STP and IGES / IGS via CAD kernel/conversion path.
- TODO VRML/WRL.
- TODO X3D.
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
- TODO Cancel import.
- TODO Import memory budget.
- TODO Progressive/deferred resource loading.
- TODO LOD controls.
- TODO FPS/frame-time diagnostics.
- TODO GPU/backend diagnostics.
- TODO Crash-safe last-session recovery.
- TODO Automated importer fixtures and parser tests.
