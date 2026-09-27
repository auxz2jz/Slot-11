# Android 3D Viewer Diagnostics

This file maps Slot-11 to the Master Instruction Library `DIAGNOSTICS_STANDARD.md`.

## Current status

**DOCUMENTED / NOT YET IMPLEMENTED AS A FULL DIAGNOSTIC SYSTEM**

The current app has useful user-facing error/status behavior, but repository inspection found no persistent structured diagnostic subsystem.

Existing signals include:

- status text during load/display
- Toast feedback
- AlertDialog error messages
- importer exceptions
- model statistics
- screenshot results
- GitHub Actions build logs

These are useful but do not satisfy the Master Diagnostics Standard because they are not a persistent correlated event history.

## Required event model for future implementation

Use one central event logger whenever practical.

Minimum semantic categories:

- `USER_ACTION`
- `OPERATION_START`
- `STATE_TRANSITION` / `PROGRESS`
- `OPERATION_RESULT`
- `ERROR`
- `TEST_VERIFICATION` / `TEST_RESULT`
- `EXPORT_DIAGNOSTICS`

Useful fields:

- sequence number
- UTC timestamp
- monotonic elapsed time
- app session ID
- event ID
- correlation ID
- operation/run ID
- test session ID / step ID
- category
- severity
- screen/module/control
- requested operation
- safe parameters
- state before/after
- progress
- duration
- result/success
- error type/message
- stack-trace reference
- app version/build

## Priority operations to instrument

### Model open/import

Record separately:

1. user selected/opened file;
2. extension/MIME/precondition result;
3. importer selected;
4. import started;
5. parsing/conversion/tessellation progress where practical;
6. Filament model load started;
7. visible asset/result validation;
8. final success/failure and duration.

For STEP/STP specifically, include:

- OCCT native library availability
- input copied to temporary file
- STEP read status
- roots transferred
- tessellation started/completed
- resulting vertex/triangle counts
- mesh bridge unpack/validation
- final Filament load result

### File picker / Open With

Record request, return/cancel, permission result, safe file metadata, and subsequent load correlation.

### Fit / display / quality / animation controls

Record the semantic user action and resulting state. A button tap alone must not be treated as success.

### Screenshot

Record request, destination creation, bytes written/stream completion, and final result.

### Recent files

Record selection, persisted URI access result, and reopen result.

## Persistent Action Trace

Future implementation should add:

- bounded persistent JSONL or equivalent trace
- current + rotated previous trace
- bounded recent-event buffer
- prompt flush for operation starts, major state changes, errors, test results, and crash markers

Do not allow unlimited log growth.

## Error/crash preservation

Incrementally add:

- central caught-error logging with stack traces/cause chains where useful
- active operation/correlation context
- global uncaught-exception preservation when supported
- recent diagnostic events in crash record
- normal Android crash handling must continue afterward

## Progress/stall diagnostics

Long operations such as large 3MF/STEP import should report meaningful progress where technically available.

When possible record:

- current import stage
- processed objects/faces/triangles
- elapsed time
- generated vertices/triangles
- output bytes
- last meaningful progress time

A future watchdog may record a stall when an operation remains active without meaningful progress. Add this only after the basic event logger is stable.

## Result validation

A process returning without exception is not sufficient.

Examples:

Model load PASS evidence:
- importer/conversion completed
- output mesh/GLB is non-empty
- Filament asset exists
- expected model statistics are sane
- no correlated fatal error occurred

Screenshot PASS evidence:
- output destination created
- image bytes written
- write completed successfully

STEP PASS evidence:
- OCCT read/transfer completed
- tessellation produced non-zero geometry
- bridge validation passed
- Filament asset loaded

## Diagnostic export

A future **Export Diagnostics** control should produce a ZIP containing, when applicable:

- `README.txt`
- `summary.txt`
- `events.jsonl`
- `action_trace.txt`
- `test_report.txt`
- `test_report.json`
- `errors.txt`
- crash record
- device/app metadata
- import/operation result report
- recent pre-failure events

Do not automatically include the user's private source 3D models.

The export operation must log its own STARTED / COMPLETED / FAILED result.

## Privacy/redaction

Do not persist unnecessary:

- passwords/tokens
- private account data
- precise location
- full sensitive file paths/content URIs
- source model contents

Prefer safe display names, extensions, sizes, generated IDs, and redacted paths.

## Incremental implementation order

Do not perform a broad diagnostic rewrite.

Recommended logical steps:

1. central structured logger + bounded persistent trace;
2. model-open/import correlation and result events;
3. caught-error + stack-trace preservation;
4. screenshot/recent/display semantic events;
5. crash preservation;
6. Export Diagnostics;
7. guided-test integration;
8. progress/stall monitoring for long imports.

Each step should be its own candidate/change set and must preserve verified viewer behavior.

## Current release rule

v0.4.0 remains the verified baseline.

v0.5.0 STEP/STP is a built but unverified candidate. Do not add unrelated diagnostic implementation to that candidate before its first device validation unless diagnostics are needed to investigate a concrete failure.
