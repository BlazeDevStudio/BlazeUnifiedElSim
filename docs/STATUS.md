# Blaze CircuitLab v0.2.6 — Verification status

- **Source implemented:** 3-pin NPN Ebers–Moll solver, editor symbol/placement/parameter dialog, preset library, provisional PCB pad mapping, KiCad serialization and SPICE export.
- **Passed locally:** 89/89 independent Kotlin/JVM numerical, topology, catalog and export tests. Kotlin Android-facing source compiles against temporary API stubs only. Bundled assets and sample files exist.
- **Not executed:** Android Gradle SDK compilation, APK creation, install/launch/device testing, native KiCad opening/ERC or ngspice comparison.
- **Not implemented:** genuine MCU firmware execution, professional PCB autorouting/DRC/fabrication validation, validated real-device BJT models.
- **Compatibility:** circuit JSON schema v7, decoding v1-v6 retained; legacy alpha HTML unchanged.
- **Build gate:** No Android SDK, Gradle, native KiCad CLI or ngspice present in this execution environment. No network access for downloading SDK from Google host.
