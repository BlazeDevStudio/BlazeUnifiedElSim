# v0.2.2 — manual routing development checkpoint

### Added
- PadKey/BoardRoute model for one-layer manual PCB routes with stable pad anchors.
- Touch 'Route pads' tool, green copper rendering, remaining-airwire computation and remove-last-route action.
- Simplified F.Cu short/clearance checks against other-net tracks and pad positions, track-width and board-boundary validation.
- Project v4 routes JSON schema with v1-v3 source-level import paths.
- New regression cases for pad mapping, route geometry, crossing nets, rollback and solver isolation.
- CI Android Gradle/JUnit tests (not executed) and 5 JUnit source tests.

### Fixed
- Deleting a schematic part removes copper routes referencing its pads.
- Incompatible schematic edits are rejected instead of leaving invalid saved routing.
- Editing mode change clears stale pad selection.

### Still missing
- Android APK and device tests, real KiCad validation, fabrication-grade PCB rules, layer/via/zone operations, completed PCB export and MCU emulation.
