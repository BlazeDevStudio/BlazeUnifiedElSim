# Blaze Unified Electronics Simulator (Blaze CircuitLab)

Native offline-first Android electronics engineering laboratory. This tree is the verified local extraction of the v0.2.9 native snapshot, prepared for migration into `BlazingSystems/BlazeUnifiedElSim`.

## Contents

- `app/`: Native Kotlin Android application, core MNA solver, circuit editor, PCB model, embedded catalogs and KiCad support.
- `tools/run-jvm-smoke.sh`: Standalone Kotlin core regression runner.
- `tools/import_snapshot.py`: Safe source archive importer. Defaults to dry-run, refuses conflicts and traversal.
- `tests/test_import_snapshot.py`: Five importer security and compatibility tests.
- `.github/workflows/android-debug.yml`: Original APK compilation CI from v0.2.9 source archive.
- `docs/handover/native-v0.2.9-README.md`: Original release handover from the ZIP, preserving provenance.
- `legacy/`: Original alpha preserved as a historical reference, not the active app runtime.

## Local evidence (2026-10-29)

- Extracted native v0.2.9 ZIP: 133 files and 703,708 uncompressed bytes.
- `bash tools/run-jvm-smoke.sh`: **122/122 passed** on extracted source.
- `python3 -m unittest discover -s tests -v`: **5/5 passed**.
- No Android SDK compilation, emulator launch or real-device verification was executed in this container.
- KiCad exports and footprints remain experimental pending actual KiCad/ERC verification.

This ZIP is complete local source; it is not a certified Android APK, and it is not proof that all contents have already been committed to GitHub. See https://github.com/BlazingSystems/BlazeUnifiedElSim/pull/1 for current transfer status.

## KiCad 9 PCB handoff (v0.3.1 development)

The native editor's **PCB → KiCad** action exports a `.kicad_pcb` with the
physical footprints actually placed in the app, explicit physical pad-to-
schematic-net assignments, rectangular board outline and actual manually drawn
`F.Cu` track segments. The KiCad ZIP export also includes the board when all
placements have original bundled `BlazeBasic` footprint geometry. External
reference-only footprints are never fabricated into a board. Unrouted ratsnest
connections are reported, **not silently converted to copper**.

`kicad-cli pcb export svg` and a native `pcbnew` load/pin/net/track contract
check are CI gates. Footprints are reference geometry, not verified physical
packages. **No manufacturing DRC, Gerber readiness, drill tolerance approval,
or actual microcontroller firmware execution is claimed.**
