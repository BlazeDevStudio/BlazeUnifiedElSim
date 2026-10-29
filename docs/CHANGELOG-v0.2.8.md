# v0.2.8 — Four-terminal controlled voltage amplifier (VCVS)

Native Kotlin core adds a SPICE E-equivalent finite-gain dependent voltage source. IN+, IN-, OUT+, OUT- have distinct authoritative topology pins. Ideal input impedance, unpowered output, no rail saturation, no offset/noise, no bandwidth or manufacturer part mapping. It is **not** a complete operational amplifier.

Added native symbol, search presets, KiCad simulation-only symbol, SPICE E netlist, and gain-10 demo. No PCB footprint is invented. Project JSON remains schema version 8: no storage schema change was needed, and older projects retain their existing interpretation.

No APK/device/KiCad/ngspice validation should be assumed from Kotlin smoke checks.
