# Blaze CircuitLab v0.2.7 — Native Android electronics development source

This release **continues the existing v0.2.6 Kotlin Android project**. The original HTML alpha is retained unchanged under `legacy/` for history, not used as the runtime.

**There is no Android APK in this ZIP.** The Android SDK and Gradle are not available in this local environment, and attempts to access `dl.google.com` failed DNS resolution. No Android emulator/device install, KiCad CLI/ERC, or ngspice run occurred. Do not treat this source release as an operational equivalence to EveryCircuit, KiCad, Altium, or Arduino.

## Changes since 0.2.6

- **MNMOS:** educational three-terminal N-channel enhancement MOSFET, terminal order **gate (0), drain (1), source (2)**. Scalar current is drain-to-source current. Its solver is the idealized symmetric square-law channel (triode and saturation) with analytic MNA derivatives and bounded Newton steps.
- Component **value = Vth in volts**, `mosfetK` = square-law transconductance parameter in amperes per square volt. Defaults are Vth=2.0 V and K=0.002 A/V². Vth range 0.1–10 V; K range 1e-9–10 A/V². Nonfinite parameters are rejected.
- Simulation correctly rejects an **unwired insulated gate** as floating. Off-state channel current remains zero in the educational model.
- New Canvas symbol, editor tool, model editor, search results, common-source resistor-load demo, explicit pad-to-terminal mapping, KiCad symbol, experimental SPICE `.model NMOS(Level=1)`, and sample export.
- Project JSON schema **v8** adds `mosfetK`. Existing version 1–7 import logic is retained in Android `ProjectJson`, but Android runtime migration tests still need to run against the SDK/device.
- Android simulation tick avoids rescheduling while paused and rejects stale callbacks after Activity destruction.
- Existing NPN, diode, R/C/L, sources, switch, LED, schematic editing, PCB planning and MCU pin-planning features are retained.

### NMOS electrical-model boundaries

**No body diode**, subthreshold leakage, gate current, Cgs/Cgd, channel-length modulation, breakdown, SOA, thermal losses or actual manufacturer part matching. The nominal G/D/S three-pad TO-92-like footprint is an *original provisional geometry reference only*, not a recommended physical pinout. **Check a selected part's actual datasheet before physical PCB production**. ngspice's Level 1 NMOS (bulk tied to source) may produce a **different result**, especially for reverse drain bias, because it models device junctions not included here.

### Built-in offline libraries

- 89 electronics presets representing 12 different solver component types. A preset is **not** an additional semiconductor model.
- 23 footprint reference choices, including 18 original bundled `BlazeBasic.pretty/*.kicad_mod` files; not manufacturer-qualified.
- Four Arduino/ESP pinout reference boards for **pin planning**, not MCU CPU/firmware emulation.
- KiCad symbol library, example `.kicad_sch` files, and SPICE netlists included. These have **not** been opened by the official KiCad or ngspice application in this environment.

## Reproduce verification

JDK 17 + Kotlin CLI:

```bash
bash tools/run-jvm-smoke.sh
bash tools/check-native-syntax-with-stubs.sh
```

The Kotlin JVM smoke suite exercises numerical, connectivity, exports, and PCB mapping. The second script compiles Android-facing source against **temporary API stubs**, not the Android SDK. Native `.kicad_sch` export and `.kicad_mod` assets are not CLI-validated.

## Compile a debug Android APK with proper toolchain

Requires JDK 17, Android SDK platform 35, build tools 35.0.0, and Gradle 8.9. From the project directory:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

If successful, check `app/build/outputs/apk/debug/app-debug.apk`, then launch it on an Android device (including offline startup, rotation, canvas touch, autosave, KiCad file export). No Gradle wrapper distribution or prebuilt APK is included. The `.github/workflows/android-debug.yml` candidate can build an APK after being committed to a repository with GitHub Actions enabled, but no run or artifact is asserted here.

**No Codex was used.** No manufacturer accuracy or fabricated hardware verification is claimed.
