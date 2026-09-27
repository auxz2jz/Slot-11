# Supported Format Test Corpus

Generated for Android 3D Viewer compatibility testing on 2026-09-26.

## Geometry

All files represent the same asymmetric L-shaped prism:

- nominal dimensions: 40 mm × 30 mm × 20 mm
- asymmetric profile makes wrong orientation/part placement easier to spot
- mesh variants contain 20 triangles
- STEP/STP variants are exact CAD solids of the same dimensions

## Pack contents

- `test_object.glb`
- `test_object.gltf` — embedded resources
- `test_object_binary.stl`
- `test_object_ascii.stl`
- `test_object.obj`
- `test_object.3mf`
- `test_object.amf`
- `test_object.x3d`
- `test_object.ply` — ASCII
- `test_object.off`
- `test_object.step`
- `test_object.stp`
- `README_TEST_PACK.txt`
- `SHA256SUMS.txt`
- `VALIDATION.txt`

## Independent generation/validation result

Before packaging:

- binary STL: PASS
- ASCII STL: PASS
- OBJ: PASS
- ASCII PLY: PASS
- OFF: PASS
- GLB: PASS
- STEP: PASS round-trip through CadQuery/OCP; 40 × 30 × 20 mm bounds
- STP: PASS round-trip through CadQuery/OCP; 40 × 30 × 20 mm bounds
- AMF: generated non-empty valid XML
- X3D: generated non-empty valid XML
- embedded glTF: generated non-empty valid JSON/data URI
- 3MF: package contains `3D/3dmodel.model`

## Device-test purpose

This corpus is intended to separate:

1. Android file-picker/Open-With problems;
2. extension/MIME recognition problems;
3. parser/importer failures;
4. renderer/display failures.

If a file fails, record the exact filename and error dialog text.

## Color expectations

- GLB/glTF contain explicit color data.
- STL is normally geometry-only and should use the viewer default material.
- Current OBJ test file is geometry-only because Android OBJ MTL support is not implemented yet.
- Current 3MF/AMF/X3D import paths are primarily geometry-focused.
- STEP/STP can contain CAD color metadata, but current Android OCCT v0.5.x bridge uses `STEPControl_Reader` and emits tessellated geometry only. Color metadata is not preserved yet, so neutral gray is expected.

## STEP color follow-up

Preserving STEP colors requires a separate CAD metadata path, likely XCAF/`STEPCAFControl_Reader` plus color extraction and a renderer-side material/color representation.

Do not mix that larger change into the STL file-selection compatibility fix.
