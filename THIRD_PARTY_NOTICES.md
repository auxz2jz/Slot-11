# Third-Party Notices

## Open CASCADE Technology (OCCT)

Android 3D Viewer uses **Open CASCADE Technology (OCCT) 8.0.1** for native CAD import and tessellation of STEP/STP files.

- Upstream project: https://github.com/Open-Cascade-SAS/OCCT
- Pinned source tag: `V8.0.1`
- License: GNU LGPL version 2.1 with the Open CASCADE Exception 1.0.
- License text and exception are available in the pinned upstream source as `LICENSE_LGPL_21.txt` and `OCCT_LGPL_EXCEPTION.txt`.
- The app dynamically links OCCT shared libraries built from the unmodified pinned upstream source.
- To rebuild or substitute the OCCT libraries, use the repository GitHub Actions build recipe in `.github/workflows/android-build.yml` or cross-compile the pinned source with the Android NDK and replace the shared libraries for the target ABI.

This project makes use of and is based on facilities provided by Open CASCADE Technology software.

### Why OCCT is included

The existing viewer uses Google Filament for presentation. OCCT is used only as a headless CAD kernel for STEP/STP translation and B-rep tessellation. The resulting triangle mesh is passed into the existing viewer pipeline; OCCT does not replace the app's renderer or UI.

### Initial Android scope

The first OCCT integration is built for `arm64-v8a`, matching modern 64-bit Android devices. Additional Android ABIs can be added after the STEP importer is validated on-device.
