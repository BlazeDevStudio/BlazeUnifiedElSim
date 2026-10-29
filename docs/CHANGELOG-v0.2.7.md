# Blaze CircuitLab v0.2.7 — source milestone

- Added solver-backed N-channel MOSFET (MNMOS), separate gate/drain/source terminal identities, channel current and Jacobian.
- Added first original MOSFET symbol/footprint mapping and four Vth presets. Bundled catalog counts: 89 electronics, 23 footprint references, four MCU board references.
- Added NMOS common-source demo, KiCad schematic, SPICE netlist, and self-contained experimental KiCad package.
- New validation for floating insulated gates; no gate-to-channel artificial DC connection in reachability test.
- Added version 8 project storage and explicit `mosfetK` handling; previous import variants retained.
- Adjusted Android simulation timer lifecycle to avoid activity-loop callbacks while paused or destroyed.
- Expanded standalone Kotlin regression suite and Android Gradle JUnit test sources.
- **Not verified:** Gradle/Android SDK compilation, APK, real device, native KiCad/ERC, ngspice, real electronics measurements.
