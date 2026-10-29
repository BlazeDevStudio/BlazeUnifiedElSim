# Blaze Unified Electronics Simulator

**Blaze CircuitLab** is a native, offline-first Android electronics laboratory in development. This is the canonical repository for its migration and continued development.

## Intended integrated product

- Circuit schematic editor and real numerical simulation
- Electronic component and Arduino/ESP pinout libraries
- Virtual measurement instruments
- KiCad schematic and SPICE interoperability
- PCB footprints, placement and routing
- Future validated MCU firmware execution and professional PCB DRC

## Source state

Original: standalone HTML alpha v0.1.0, preserved **only for legacy provenance**.

Latest local source snapshot: **native Kotlin v0.2.9**, with 122 previously reported JVM core checks. **The full native project ZIP has not yet been imported into this GitHub branch.** Available historical files are being imported first. See [migration status](docs/MIGRATION_STATUS.md) for exact coverage.

**No validated APK build, real KiCad/ERC compatibility run, or Android physical-device test is yet established.** Do not present this repository as a released amalgamation until those checks pass.

## Engineering principles

Maintain one shared topology across solver, exports and PCB. Preserve legacy project migration compatibility and all functioning features. Do not mislabel simulations as physical proof. Keep unknown/unsupported component models out of verified library capabilities. Do not use Codex.
