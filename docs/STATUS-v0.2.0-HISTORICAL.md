# Development verification — 2026-10-27

| Scope | Evidence | Status |
|---|---|---|
| User-supplied alpha | Unmodified `legacy/BlazeCircuitLab-0.1.0-original-offline.html` | Preserved |
| Native Kotlin domain/core compile | Kotlin 1.9.0 JVM compiler | PASS |
| Kotlin core regressions | `tools/run-jvm-smoke.sh`, 18 tests | PASS |
| Numerical alpha comparison | Old HTML `BlazeSim.solve` values for LED/R/RC/SW checked | PASS within 1e-9 A |
| Explicit junction/unmarked crossings | Unit smoke tests | PASS |
| KiCad exported file structural sanity | S-expression balanced and deterministic; sample output | PASS limited |
| Native Android Kotlin source, mock API compile | Kotlin syntax/type-level stub check only; no Android SDK | PASS limited; not an APK build |
| Native KiCad open/ERC/netlist | No KiCad executable in environment | NOT RUN |
| Android Activity, Canvas editor build | No Android SDK/Gradle | NOT BUILT / NOT VERIFIED |
| Legacy v1 JSON migration on Android | Implemented source, not executed against Android JSON runtime | UNVERIFIED |
| Android emulator/device interactions | No Android SDK/emulator/ADB | NOT RUN |
| PCB pad mapping contract | Runtime Kotlin tests | PASS limited |
| PCB editor/manufacturing exports | Not implemented | UNSUPPORTED |
| Real Arduino/ESP emulator | Backend interface only | UNSUPPORTED |

No APK, native KiCad validation, Android acceptance, firmware emulation or physical safety proof is claimed.
