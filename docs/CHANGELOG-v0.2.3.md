# v0.2.3 — Native embedded library workflows (source-only)

- Added a searchable, compiled electronics preset library (55 presets using seven real pre-existing numerical solver types); preset identities are version-5 persisted.
- Added 14 original local `.kicad_mod` footprints, native KiCad symbol library for all seven types, and KiCad project-local footprint/symbol library tables.
- Added browsable footprint selection and physical assignment to the existing PCB workspace; old externally named footprints still work as metadata aliases.
- Expanded `.kicad_sch` experimental export to AC source, capacitor, and switch in addition to prior V, R, LED and GND.
- Added a self-contained KiCad schematic+local-library ZIP export, without falsely providing routed PCB files.
- Added factual Arduino Uno R3, classic Nano, ESP8266 NodeMCU and ESP32-DevKitC V4 pin lookup, design-only pin-to-component annotations, CSV export, and some voltage/strapping warnings.
- Added version 5 JSON persistence for catalog IDs and MCU planning annotations, preserving v1–v4 migration code.
- Added portable JSON manifests and samples for the bundled source and engineering metadata.
- Extended JVM smoke suite to 48 checks, all passed. Android-side source checks passed against stubs, not SDK.
- No APK, hardware validation, real KiCad opening/ERC, or firmware emulator delivered.
