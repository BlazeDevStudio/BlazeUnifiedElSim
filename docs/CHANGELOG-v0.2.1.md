# 0.2.1 Native PCB Placement Preview

- Preserves original v0.2 native project layout and v0.1 legacy HTML reference.
- Adds native Android Canvas PCB placement surface, distinct from schematic view.
- Adds board dimensions and explicit footprint picker for R/C/LED, with controlled board placement and rotation.
- Adds authoritative schematic-net-derived unrouted airwire planning and approximate 2D pad locations.
- Adds validations for pad-to-terminal polarity, partial mappings, component type mismatches, orphan placement, board out-of-bounds and duplicate placements.
- Adds version 3 project storage for board settings and placements, with source-side v1/v2 decoder paths.
- Adds 5 new PCB-focused JVM tests (18 -> 23), preserves 18 previous regressions.
- Adds a GitHub Actions Android debug APK build recipe (unrun, no APK claimed).
- No HTML-to-native runtime dependency, no Codex, no fabrication-readiness claims.
