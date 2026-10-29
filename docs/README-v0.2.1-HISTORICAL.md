# 0.2.1 continuation update

This is a **continuation of the verified v0.2 Kotlin native source**, not an HTML application.

New native PCB placement mode: explicit footprint selection for resistor/capacitor/LED, editable placements and board size, footprint rotation, displayed pad centers and topology-derived unrouted airwires. Data is saved with the circuit using schema v3 and the v1/v2 import path remains. Geometry shown is a visual planning approximation and must not be treated as fabrication data. Actual routing, PCB KiCad handoff, copper DRC, Gerber/Excellon, and firmware emulation remain unavailable.

Android build instructions: use Android Studio or run the new `.github/workflows/android-debug.yml` in a GitHub repository (manual `workflow_dispatch`). The CI workflow was **not executed** here. A successful CI run would upload a debug APK; it would not constitute emulator/physical-device acceptance. See `docs/STATUS-v0.2.1.md` and `docs/CHANGELOG-v0.2.1.md`.

---

# BLAZE CIRCUITLAB — NATIVE ANDROID DEVELOPMENT FOUNDATION

**Version:** 0.2.1-native-pcb-preview (source-development checkpoint, not a release).
**Origin:** user-supplied `BlazeCircuitLab-offline.html` (alpha 0.1) preserved unchanged in `legacy/`.
**Architecture:** native Android `Activity` + `android.graphics.Canvas` editor, pure Kotlin numerical/domain engine; **no WebView and no HTML runtime dependency**.

This package is intentionally **not** an HTML wrapper. The legacy HTML is included only as a historical reference and regression baseline. This is the first native implementation toward the intended EveryCircuit-class simulator + KiCad interoperability + Altium-class PCB workspace + real Arduino/ESP firmware emulation. Those products' proprietary code and design assets are not included.

## Implemented in this source checkpoint

- Android-native schematic drawing and grid; tactile component selection/placement; orthogonal wiring; explicit junctions; component move/re-route with invalid moves rejected; rotation with diagonal-wire rejection; pan, fit, pinch-to-zoom; selection inspector and value editing.
- Local educational modified nodal analysis (MNA): DC and sinusoidal voltage sources, resistors, capacitors using backward Euler, simplified LEDs, finite-resistance switches, ground. Background executor runs bounded solves and posts results to the UI.
- Native live readout and a selected-component-voltage oscilloscope trace (basic time-history visualization, not a professional calibrated instrument).
- Canonical Kotlin electrical topology graph; disconnected crossings stay disconnected, ambiguous unmarked T junctions rejected.
- Android private autosave with `AtomicFile`, native JSON import/export through Android Storage Access Framework, import size limit and schema validation. Migrates alpha v1 and native v2 JSON to native schema v3, adding junctions at legacy implicitly connected pin/wire contacts.
- Experimental KiCad-9-style `.kicad_sch` serializer for DC source, resistor, LED, ground, wires and junctions. No fabrication/footprint claims. Unsupported types fail explicitly instead of being silently dropped.
- PCB **data contract** for explicit physical pad-to-schematic-terminal mapping; PCB editor and fabrication exports are NOT implemented.
- `FirmwareBackend` interface only. No Arduino/ESP firmware emulation. Do not confuse UI logic with real MCU instruction execution.

## Not verified / not implemented

- **Android APK not built**: Android SDK, Gradle and emulator/device are absent from the current execution environment. The Android-facing code is source-only, not compiled or run against an Android SDK yet.
- **KiCad native opening/ERC not performed**: KiCad CLI and GUI unavailable. Only balanced S-expression and Kotlin logical unit tests were run.
- **PCB interactive editor, track routing, vias, DRC, KiCad `.kicad_pcb`, Gerber/Excellon**: not implemented.
- **Arduino, ESP8266 and ESP32 instruction/firmware execution**: not implemented.
- UI and native lifecycle/manual-touch interaction: Android emulator and physical device acceptance still required.
- Current waveform renderer is a single-channel simplified oscilloscope, not a signal-integrity or high-accuracy instrument.

## Build on a machine with Android Studio / Android SDK

Requirements: Android SDK Platform 35, Android Build Tools for API 35, JDK 17+, **Gradle 8.9+**, Kotlin Gradle plugin 1.9.25, Android Gradle Plugin 8.7.3. Gradle dependencies may need downloading **during development**, but the built app has no INTERNET permission and no cloud dependency.

1. Open this directory as a Gradle project in Android Studio and install SDK Platform 35 if prompted.
2. With Gradle installed and accessible, run: `gradle :app:assembleDebug`.
3. For a reproducible wrapper on your build machine, run `gradle wrapper --gradle-version 8.9`, then `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`).
4. If successful, the **debug-only** APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
5. Install/test on an Android 8+ ARM64 device and an emulator. Test screen rotation, gestures, offline startup, save/import, invalid circuit warnings, background/resume and KiCad export.

**A Gradle Wrapper binary is not bundled** because it could not be generated in this environment; no APK compilation success is claimed.

## Run the platform-independent numerical checks here

From this project directory, run: `bash tools/run-jvm-smoke.sh` (requires `kotlinc` JVM 1.9+ and Java).

The script compiles the core with Kotlin JVM and tests solver behavior, alpha reference numerics, topology crossings/junctions, experimental serialization, and strict PCB mapping. It also writes a native schematic sample at `tools/LED_Current_Limiter.kicad_sch`.

## Engineering architecture

- `core/Circuit.kt`: project schema/parts/terminals, demos, resource/parameter validation.
- `core/Topology.kt`: authoritative connectivity/net graph. Never infer independent nets inside UI or exporter.
- `core/MnaSolver.kt`: isolated electrical numerical engine and diagnostics.
- `core/KiCad.kt`: native electrical graph -> candidate KiCad schematic serializer.
- `core/Interoperability.kt`: PCB pad mapping invariant and an unimplemented firmware backend interface.
- `core/Board.kt`: constrained board placement, candidate footprint catalog, pin-to-pad check, and topology-derived unrouted ratsnest.
- `NativeBoardView.kt`: native Android Canvas preview and footprint dragging (not routed copper).
- `NativeCircuitView.kt`: hardware-accelerated-capable Android Canvas UI and touch edit controls.
- `MainActivity.kt`: native controls, background solver scheduling, instrument readouts, Android Storage Access Framework.
- `ProjectJson.kt`: versioned JSON storage and legacy alpha migration.

**Important:** Native source and data model are evolving from the original alpha semantics; this is **not** a line-for-line port of `app.js` or `simulator.js`, because the current user requested that HTML not remain the main architecture. Existing native Android ZIP sources were not available in the received upload. The original HTML is preserved byte-for-byte.

## Milestone continuation (the real amalgamation)

1. v0.2 first release gate: actually compile APK, run emulator/physical-device regressions, validate KiCad sample in KiCad 9 and compare exported netlist with solver graph.
2. Dedicated high-performance renderer/tools for editable symbols and complex nets; proper undo/redo persistence, gestures and grid settings.
3. Professional simulation backend: ngspice-style validated numerical models, Newton-Raphson, L/C, BJT/MOSFET and multi-channel instruments; preserve reproducible trace results.
4. Linked native PCB editor: footprints/pads, ratsnest, exact physical mapping, routing, DRC, and real native KiCad handoff.
5. Separate CPU/firmware emulation backend for **actual compiled MCU binaries**, timing and peripherals, supported device-by-device. No fake GPIO demos marketed as emulation.

## License / brand

Original Blaze alpha retained; new development code follows owner project direction. No copied proprietary EveryCircuit/Altium graphical assets, code, firmware or KiCad internals. Third-party code additions must have license review before release. This package does not assert SPICE-grade simulation or manufacturing/physical safety validation.

### Additional local syntax check (not Android build)

`bash tools/check-native-syntax-with-stubs.sh` compiles the entire Kotlin source tree against minimal test-only stand-ins under `tools/android-api-stubs/`. This detects Kotlin syntax/type-level mistakes in the native code, but the stand-ins **do not model Android behavior**, prove API correctness, run an emulator, or produce an APK. They are not included in the Android app module or APK.
## Checksum note
`docs/SHA256SUMS-v0.2.1.txt` is the authoritative hash list for this source checkpoint. The v0.2.0 manifest is historical only and does not describe modified v0.2.1 files.
