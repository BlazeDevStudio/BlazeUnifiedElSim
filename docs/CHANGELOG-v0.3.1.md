# Blaze Unified Electronics Simulator — v0.3.1 development

## Native KiCad PCB export

- Added `KiCadBoard.export` to serialize an actual editable KiCad 9 board: board outline, original bundled footprints, stable source-derived nets, actual pad numbers, and explicit manually routed F.Cu copper segments.
- Embedded the referenced `BlazeBasic` `.kicad_mod` files directly; verified pad IDs against the runtime footprint index. A reference-only external footprint cannot masquerade as a bundled physical model.
- Exposed **PCB → KiCad** through the Android Storage Access Framework. ZIP handoffs include the `.kicad_pcb` only if the source project has placed bundled footprints; no invented copper on unrouted nets.
- Preserved the original simulator and schematic source, board placement/route history, JSON schema v9 and release archives.
- Added four core regression tests (129/129 local, from 125/125), a reproducible board sample, and a new real-KiCad CLI + pcbnew pin/net/track CI check.

## Unresolved release blockers

The generated board is **not manufacturing-qualified**. Original footprints are preliminary reference geometry. Real DRC/clearance, exact manufacturer pad sizes, copper zones, vias, plated drill manufacturing tolerances, routing completeness, and Gerber/drill verification are not covered. Arduino/ESP pin reference libraries are **not firmware emulators**. Physical device acceptance and review are required before a production build.
