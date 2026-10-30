# UnifiedELSIM native Android — codebase hygiene ledger

This document tracks bounded, source-level refactors **before** work on new simulator models or firmware emulation.
Historical imports, ZIP archives, manifests and the public source provenance are immutable evidence: do not
reformat, regenerate or delete those resources as part of cleanup.

## 2026-10-31 / pass 1 — native KiCad I/O boundary

**Baseline:** draft PR #1, head `b5293de`. Verified native Android Gradle/JVM, emulator and KiCad workflows
at that exact head before changing source. No runtime failure was used to justify speculative edits.

**Issue:** `MainActivity.kt` duplicated bundled-footprint loading for PCB and KiCad ZIP export.
That mixed Android resource I/O with editor UI, and each path maintained a separate name/path policy.

**Refactor (preserve original exports):**
- Add `KiCadAssetRepository.kt` with one injectable, offline asset-reading boundary.
- Route both `exportKiCadBoard` and `exportKiCadPackage` through the same footprint discovery,
  path validation, deterministic ordering and bounded byte reads.
- Make packaged metadata loading explicit. No network, new formats, model changes, signature,
  manufacturing approval or production claims.
- Seven JVM regression cases exercise board-only footprint loading, full ZIP metadata, actual bundled
  source assets, absent resources, malformed paths, duplicates, and empty/oversized assets.
- Retain the previously validated same-stem `.kicad_pro` and bundled `fp-lib-table`.

**Acceptance gates:** Android `testDebugUnitTest` and debug build, emulator offline UI smoke, real
KiCad 9 DRC and library registration, source/historical preservation. Report exact-head run IDs,
and keep draft until all required gates pass.

## Cleanup queue (not done yet)

1. **Activity responsibilities:** separate document picker/export lifecycle from the Android view/controller;
   keep per-request payload identity and cancellation/rotation semantics intact.
2. **Large test runner:** split `tools/JvmSmoke.kt` into topical suites without deleting evidence or relaxing
   numerical/physical-pin assumptions; run both suite counts before/after.
3. **Core model and solver:** isolate circuit validation, topology, MNA integration and model constraints;
   preserve numerical tolerances and fail-closed floating-node behavior.
4. **Catalog schema:** align hand-authored offline JSON with source generators, include schema/footprint
   cross-checks and preserve all original pin mappings.

**Explicitly outside cleanup:** inventing AVR/ESP instruction execution; qualifying supplier footprints,
calling one demonstration board production-ready, releasing an unsigned APK, deleting old migrations or
merging draft PR #1.
