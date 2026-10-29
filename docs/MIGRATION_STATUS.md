# Migration status — 2026-10-29

**Canonical GitHub destination:** `BlazingSystems/BlazeUnifiedElSim`  
**Working branch:** `migration/circuitlab-v0.2.9`

## Confirmed in GitHub
- `README.md` — native Android project identity and honest release criteria
- `legacy/BlazeCircuitLab-offline-original.html` — source original alpha for historical preservation only
- `samples/Powered_Opamp_Rail_Limiter.kicad_sch` — native-formatted experimental schematic example
- `docs/handover/v0.2.9-README.md` — release handover and test history
- `docs/handover/v0.2-offline-alpha-README.md` — earlier handover
- `tools/import_snapshot.py` — safe source ZIP importer (dry run by default)
- `tests/test_import_snapshot.py` — importer regression tests
- `.github/workflows/migration-gate.yml` — explicit Android source-presence gate and debug build workflow

## Locally verified but NOT YET PUSHED TO GITHUB
- Native Kotlin source v0.2.9 archive `BlazeCircuitLab-NATIVE-ANDROID-v0.2.9-POWERED-OPAMP-SOURCE.zip` with **133 entries / 703708 extracted bytes**.
- The earlier native release snapshots v0.2.0 through v0.2.8 and independent experimental library/example archives.
- Source archive integrity (ZIP CRC), dry-run and repeated safe local extraction **passed**.
- Current v0.2.9 `tools/run-jvm-smoke.sh`: **122/122 passed** locally after extraction.
- `python -m unittest discover -s tests`: **5/5 importer safety checks passed** locally.

**The GitHub branch is not yet buildable.** It lacks `settings.gradle.kts`, `app/` and the other native source files. A passing import test does not qualify as an Android APK build.

## Why this remains incomplete
The connected GitHub integration can create UTF-8 files and Git blobs, but the local build container cannot establish a Git network connection. Its source ZIP file bytes have not been transferred into the connected GitHub interface. The import script and locally extracted tree preserve a safe, reproducible migration route. Do not substitute a newly generated skeleton for the existing source.

## Remaining required gates
1. Transfer the exact native v0.2.9 ZIP contents to this branch while preserving its relative layout and all historical files; verify checksums.
2. Make the Gradle source-presence gate pass, then run `:app:testDebugUnitTest` and `:app:assembleDebug` in GitHub Actions.
3. Verify APK installation, application startup, offline operation, and touch/UI behavior on emulator or real device.
4. Independently validate KiCad schematic exports and PCB/net equivalence with the real KiCad CLI.
5. Preserve earlier released source archives in a historical release or archive folder without confusing them with current runnable source.
