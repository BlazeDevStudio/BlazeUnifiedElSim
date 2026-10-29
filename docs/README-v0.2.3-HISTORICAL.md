# Blaze CircuitLab — v0.2.3 native Android library integration (development source)

**The native Android app is the product; the untouched original HTML alpha lives only in `legacy/` as a reference.** This release continues directly from the v0.2.2 manual-routing source. It is not a finished EveryCircuit + KiCad + Altium + Arduino replacement, an installable APK, or a verified electronics design tool.

## Bundled local libraries (included in `app/src/main/assets` and/or compiled Kotlin)

| Library | Real data included | Actual connected workflow | Limit |
|---|---|---|---|
| Electronics | 55 original value/type presets, seven existing solver models | Native search -> select preset -> tap Canvas to place -> edit value -> live MNA simulation -> save JSON | Not 55 independent device models: ideal R/C/sources, finite-R switch, approximate LED |
| KiCad symbols | `kicad/BlazeCircuitLab.kicad_sym`, 7 original schematic symbols + `sym-lib-table` | Export `.kicad_sch` for all seven existing component types, including RC and switch demos | Real KiCad CLI/ERC/opening still unverified |
| PCB footprints | 14 actual original `.kicad_mod` files under `kicad/BlazeBasic.pretty`; `fp-lib-table` | Search footprint -> assign to supported schematic component -> pads/ratsnest/manual F.Cu route -> KiCad schematic ZIP including needed library files | Reference geometry, not manufacturer-verified land patterns; not fabrication-ready; five older external KiCad footprint IDs remain compatibility aliases |
| Arduino / ESP | Uno R3, classic Nano, ESP8266 NodeMCU 1.0 and ESP32-DevKitC V4 pin maps; 3.3/5V GPIO cautions; functional lookup metadata | Native MCU library -> choose board -> search pins -> bind a schematic terminal as a **planning annotation** -> export CSV | **No instruction execution, firmware compilation, emulator, I2C/SPI/UART bus simulation or physical connection stamping** |
| Raw catalog data | `catalog/electronics.json`, `catalog/footprints.json`, `catalog/mcu_pinouts.json` | Auditable packaged catalog lists reflecting Kotlin registry | Generated data snapshots, read-only to users in this milestone |

The app performs no network lookups to browse these libraries; it has no login or Internet permission. External references in metadata are descriptive source links, not runtime dependencies.

## Example end-to-end workflows

1. Tap **Parts Library**, search `330 resistor` or `white LED`, choose entry and tap schematic canvas to insert the component. The actual solver handles the new element. Use **Edit value** to adjust parameters. Save/import the native JSON file with `catalogId` preserved.
2. Select a physical resistor or LED, tap **Footprints**, filter `BlazeBasic`, and assign a compatible local footprint. Switch to **PCB Placement** to view its pad positions, airwires, and manual route tool; the same circuit topology supplies pad net identities.
3. Tap **Export KiCad** for a `.kicad_sch` or **KiCad ZIP + Library** for `.kicad_sch`, original symbols and local physical footprint library. This is a **schematic+footprints package, not a finished PCB**. Actual KiCad opening/ERC/netlist equivalence is still required.
4. Tap **MCU Library**, select Uno/Nano/ESP8266/ESP32 and search `SPI`, `PWM`, `ADC`, or a pin name. With a schematic component selected, bind an MCU pin to its terminal to record a design-planning relationship; **no MCU signal is electrically simulated**. Export the mapping via **MCU Pin CSV**.

## Original functionality preserved

The v0.2.2 native schematic editor, solver (R, C, LED, DC/AC voltage sources, switch, GND), junctions, native oscilloscope preview, JSON autosave and migrations, PCB placement, ratsnest and experimental single-layer manual copper route remain. The original HTML alpha is unchanged under `legacy/`, never executed in the Android app.

## Project schema

Native JSON schema is now **version 5**. It adds optional `catalogId` to an existing supported `Part`, plus optional MCU board ID and schematic-terminal planning bindings. Older native v2–4 and legacy alpha v1 imports retain their existing pathways. A version-5 project is not claimed backward-readable by v0.2.2. Android JSON runtime/migration testing is pending.

## Verification — completed locally

- `bash tools/run-jvm-smoke.sh` — **47/47 passed** (solver regressions, topology, PCB, locally packaged footprints, catalog search/placement, MCU pin planning, local KiCad ZIP, symbol library balance checks).
- `bash tools/check-native-syntax-with-stubs.sh` — passed, using temporary Android API stubs. This **is not SDK compilation** and does not test touch, storage, or devices.
- `tools/ExportLibraryAssets.kt`, `tools/ExportCatalogAssets.kt`, `tools/ExportSamples.kt` generate the corresponding included library and sample assets from code.
- Sample KiCad schematics/ZIP files and Uno pin CSV are under `samples/`.

## Not yet validated

No Android SDK/Gradle available in this local execution environment: **APK not built**. There has been no Android emulator/hardware test, no KiCad CLI/ERC/netlist or manufacturing validation, no CPU emulation, no production safety approval. The CI workflow under `.github/workflows/android-debug.yml` is included but **not executed**. The original separate Android alpha code archive was not supplied; we continued the previously delivered v0.2.2 native source package.

## Reproducible build (external SDK/CI)

Requires Android SDK Platform 35, Build Tools 35.0.0, JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and Kotlin 1.9.25. After installing Android SDK and Gradle:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
# Artifact expected only if the build succeeds: app/build/outputs/apk/debug/app-debug.apk
```

## License and engineering notes

The 14 `BlazeBasic` footprints and seven Blaze symbols are new minimal reference drawings created for this package, not rebranded or bundled official KiCad/Altium/Arduino libraries. Official KiCad community libraries are **CC-BY-SA 4.0** if redistributed as a library collection; no such third-party collection has been copied into this package. See `docs/LICENSING-AND-ACCURACY.md`. Always verify actual pad dimensions, pin numbers, ground paths, DRC/ERC, board and hardware safety before physical fabrication. No Codex used.
