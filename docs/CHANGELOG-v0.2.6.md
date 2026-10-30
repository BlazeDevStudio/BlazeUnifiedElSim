# v0.2.6 — NPN transistor native model

- Added true three-terminal `QNPN` device type, B/C/E pin geometry, topology, nonlinear Ebers–Moll model, Jacobian linearization, Newton damping, and physically calculated branch current.
- Added editable forward β, Is, reverse β, temperature, NPN sample, model-aware readout, preset library records and provisional three-pad E/B/C footprint with reference geometry.
- Added model serialization in project schema v7 (migrating v1–v6), native KiCad QNPN symbol and instance pin associations, and preliminary SPICE `.model NPN` handoff.
- Added 11 test cases. Existing 78 tests still pass.
- Bundled sample NPN schematic, SPICE, self-contained library handoff and updated local catalog files.
- No APK, physical testing, native KiCad/ERC, ngspice or fabrication validation is claimed.
