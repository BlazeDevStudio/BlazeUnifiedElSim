# Blaze CircuitLab migration — native v0.2.9

Target: `BlazingSystems/BlazeUnifiedElSim` • Branch: `migration/circuitlab-v0.2.9` • Draft PR #1.

The native v0.2.9 source (Kotlin editor, MNA solver, PCB/footprint models, KiCad/SPICE exports, offline component/MCU catalogs, Android project/build scripts and tests) has now been prepared as Git objects for this branch. The original alpha remains archived under `legacy/`. Three source paths that differed from earlier migration scaffolding are independently preserved under `docs/provenance/` without discarding the previous authored branch versions.

The Kotlin suite previously passed 122/122 locally and the source importer 5/5. This is historical local evidence, not a fresh GitHub CI or Android runtime result. GitHub Actions Android compilation, APK existence, KiCad CLI/ERC, emulator and real-device tests remain to be confirmed.

Do not merge/release until GitHub tree consistency and Android build gates are verified. Historical release archives are a separate migration item; don't confuse archived ZIPs with the runnable source tree.
