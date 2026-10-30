# Blaze CircuitLab v0.2.1 — source checkpoint

## Verified in this environment
- Existing standalone alpha HTML preserved byte-for-byte as legacy reference.
- Pure Kotlin/JVM core compilation and 23/23 smoke tests PASS, including the previous numerical regressions and five new PCB-model tests.
- Source-level compilation with temporary Android API stand-ins: see `docs/android-stub-compile-v0.2.1.txt`. **These stubs are not the Android SDK and do not execute Android UI code.**
- Local ZIP CRC and SHA-256 manifest, generated at packaging time.

## Implemented in source; real Android device unverified
- Native `NativeBoardView` has a placement canvas: board outline, footprint bodies/pads, moving footprints, and airwires computed from the authoritative schematic topology.
- Explicit chooser for resistor, capacitor, and LED footprint candidates. Component-terminal mapping is checked before saving; voltage/AC sources and GND remain simulation-only.
- Board width and height editing, rotation and rollback when invalid.
- Circuit v3 JSON stores board outline and placements; older v1/v2 reads are migrated in code (Android JSON runtime tests still required).
- Undo snapshots carry board geometry; schematic component deletion removes attached board placements.
- GitHub Actions build workflow configured, **not executed here**.

## Critical limitations
- Android SDK, Gradle, emulator and ADB are not installed here. No APK compilation, installation, startup or hardware testing.
- Native KiCad 9 opening/ERC/netlist equivalence is not tested (KiCad unavailable).
- Footprint IDs and approximate pad center positions are *candidates*, NOT validated real KiCad footprint geometry.
- Ratsnest wires are visual airwires; **no tracks, vias, copper, DRC, PCB export, Gerber, Excellon, or fabrication readiness**.
- The instrument display is educational only; no real Arduino/ESP firmware is executed.

## Next release gates
1. Run the included GitHub Actions Android debug build; fix compiler/build failures, install and test on a real Android phone and emulator.
2. Run actual KiCad 9 parsing/ERC/netlist interoperability on sample schematic; verify pin mapping.
3. Validate on-device JSON v1/v2/v3 migration, rotate device, test placement touch and file recovery.
4. Add validated KiCad PCB starter export with canonical footprint libraries, then routing/DRC in later milestones.
