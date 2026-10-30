# Blaze CircuitLab — v0.2.2 checkpoint (2026-10-28)

## Direct evidence from this session

- Unzipped exact user-context v0.2.1 source archive and inspected Kotlin native, core, JSON storage and CI.
- Baseline `bash tools/run-jvm-smoke.sh`: **23/23 PASS** before changes.
- Implemented pad-anchored manual front-layer copper `BoardRoute`, approximate same-net validation, different-net geometric short checks and residual ratsnest computation.
- Implemented `NativeBoardView` route mode with tap-pad-to-pad, green copper rendering, unconnected orange airwires, plus Activity controls and undo integration.
- Versioned Android JSON schema moved from v3 to v4, accepting v1/v2/v3 input. Implemented but **NOT runtime-tested** due absence of Android SDK/working org.json JVM runtime.
- Added 11 new JVM smoke checks: test total **34/34 PASS**.
- Android-facing Kotlin source compiled against **temporary API stand-ins only**; this is neither Android SDK compilation nor UI execution.
- Preserved legacy HTML SHA-256 `8683c220f57bacb56e330aa3b0384923ef3cff60a57d88829e0dcf4469e7b34b` identical to uploaded copy.
- Added Gradle/JUnit unit tests and CI test invocation; **JUnit suite/CI NOT EXECUTED** in this environment.

## Not available/verified

- `gradle`, Android SDK, emulator, `adb`, and `kicad-cli` were not installed here; **NO APK built or tested**.
- Native KiCad import/ERC/network verification NOT done. Sample KiCad S-expression remains experimental.
- PCB approximate pad offsets are unverified against authentic footprint definitions; no fabrication-grade DRC, copper zones, vias, multiple copper layers, or manufacturing outputs.
- No real Arduino/ESP firmware execution or full numerical SPICE model.

## Release status

**Development only. NO-GO for APK release, KiCad interoperability certification or PCB fabrication.**

## Source/API warnings

Route references use part UUID and pad number; a PCB route becomes invalid if a source schematic change reassigns nets, changes pad geometry so route segments are diagonal, or removes a referenced pad. The application rejects/rolls back such changes rather than silently keeping invalid copper. Approximate geometry is not proof of manufacture safety.
