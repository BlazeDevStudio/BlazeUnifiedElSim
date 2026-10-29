# v0.2.5 — Experimental Shockley diode model

- Added solver-backed `D` component, nonlinear Shockley diode Newton stamping and damped iterations, with dedicated ideality and temperature properties (fixed Is).
- Native editor tool, four bundled semiconductor presets, original diode symbol, reference footprint, polarity-aware PCB pad mapping, one diode example schematic and SPICE file.
- JSON schema v6 stores model properties; v1–v5 decoder paths remain, no old projects are rewritten until loaded and saved.
- Expanded KiCad symbol library and SPICE per-diode model export, with explicit diagnostic for inconsistent per-instance temperatures.
- 14 Kotlin smoke regressions added (78 total); Android JUnit tests added for use in actual SDK CI, not run here.
- Unchanged original alpha source and previous numerical fixtures.
- Not achieved: APK compilation, actual KiCad/netlist ERC, ngspice compatibility execution, physical diode measurements, MCU emulation, full PCB manufacturing DRC.
