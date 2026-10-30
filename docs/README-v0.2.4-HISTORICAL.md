# Blaze CircuitLab — v0.2.4 native numerical models and export (source preview)

**Primary product: native Kotlin Android. No WebView runtime, no network required.** This builds directly on the owner-supplied v0.2.3 source ZIP. Previous source history, legacy HTML alpha and bundled libraries are preserved. **No APK has been produced or tested here.**

## Concrete additions in v0.2.4

- Nine implemented electrical component types: `V`, `AC`, `R`, `C`, `LED` (piecewise approximate), `SW` (finite-resistance approximate), `GND`, **`I` independent DC current source**, **`L` inductor**.
- A true current source contributes its requested left-to-right current to the MNA RHS. The inductor uses a backward-Euler Norton equivalent: `g = dt/L`, `i_new = i_previous + g*(Va-Vb)`. Inductor branch current is stored per stable component UUID for future time steps. This models an ideal inductor, not parasitic resistance, coupling or saturation.
- Native Canvas symbols and insertion tools for current sources/inductors, numeric editing, and RL/current-source example circuits. Simulation is computed by the existing Kotlin MNA engine.
- **77 searchable electronics presets** (55 previous + 15 inductors + 7 current sources), **9 embedded KiCad symbols**, **15 original bundled BlazeBasic KiCad footprints** (+ 5 previous external KiCad reference mappings), 4 Arduino/ESP pinout-reference catalogs. IDE firmware compilation and instruction emulation are not present.
- A preliminary `BlazeBasic:L_Axial_7.62` reference pad layout, with explicit pad 1/2 to simulator terminal 0/1. No footprint is assigned to the ideal current source, which is not a physical device.
- KiCad serializer supports all nine types; symbol property values now preserve tiny units (e.g., 1 nF and 1 µH) without rounding to zero. KiCad 9 native opening/ERC has **not** been run.
- Experimental **SPICE `.cir` export** for linear independent DC/AC voltage sources, DC current sources, R/C/L and switches approximated by resistor on/off values. The unsupported nonlinear LED is **rejected** rather than silently replaced by a fake model. ngspice compatibility has **not** been run.
- New RL current rise and independent current-source samples, shipped in `samples/`, along with the three preserved alpha examples.

## Validation

Run in this folder with installed Kotlin/JDK:

```sh
bash tools/run-jvm-smoke.sh
bash tools/check-native-syntax-with-stubs.sh
```

64 of 64 Kotlin smoke tests passed locally. Kotlin tests check MNA polarity, first RL transient step against a discrete analytic calculation, RL rise and free decay, source parameters, library placement, PCB mapping, KiCad syntax serialization and SPICE netlist structure. The Android API-stub compile only checks Kotlin source compatibility against **mock Android APIs**; it does not build or execute an APK.

**Real Android release gate:** Install JDK 17, Gradle 8.9, Android SDK Platform 35 and Build Tools 35.0.0; run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`. An unchanged workflow under `.github/workflows/android-debug.yml` can run that build if this project is put in an authorized repository. Neither local Android SDK compilation nor that CI workflow has run here. `ngspice` and `kicad-cli` were also unavailable; there is no external EDA validation or physical-device testing.

## Limitations and safety

This is still a **development source preview**, NOT a mature EveryCircuit/KiCad/Altium/Arduino replacement. PCB routing is a single-front-layer experimental geometry preview, with no fabrication DRC/Gerber or validated manufacturer parts. MCU catalog entries are pin-planning references only; they do not execute firmware or drive nets. The simulation engine contains ideal and approximate models; it does not prove real-world electrical or thermal safety. Do not fabricate boards based only on these results.

`legacy/BlazeCircuitLab-0.1.0-original-offline.html` remains unchanged. `docs/README-v0.2.3-HISTORICAL.md` holds the old handoff README; all other earlier docs are retained.
