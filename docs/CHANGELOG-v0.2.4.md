# v0.2.4 changelog

- Introduced true independent DC current source (MNA RHS).
- Introduced ideal inductor with backward-Euler transient current state.
- Expanded native symbols, tools, demos, project libraries, KiCad symbol definitions and physical inductor reference footprint.
- Added experimental linear SPICE netlist export with explicit unsupported-LED rejection.
- Corrected KiCad component value precision for tiny capacitances and inductances.
- Added analytical regression coverage, per-model validation and Android Gradle JUnit regression tests.
- Retained v5 project JSON schema and v1–v4 import compatibility.
- Real Android compilation, native KiCad ERC and ngspice execution remain pending.
