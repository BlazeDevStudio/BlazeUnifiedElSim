package com.blaze.circuitlab

import android.content.res.AssetManager

/**
 * Single boundary for reading the application's versioned, offline KiCad resources.
 *
 * The native Activity handles UI and SAF only; this class owns asset names, validated
 * paths and bytes. The injectable readers also make package/board exports testable
 * with deterministic in-memory assets, without Android device or mock frameworks.
 */
internal class KiCadAssetRepository(
    private val listAssets: (String) -> List<String>?,
    private val readAsset: (String) -> ByteArray
) {
    constructor(assets: AssetManager) : this(
        { path -> assets.list(path)?.toList() },
        { path -> assets.open(path).use { it.readBytes() } }
    )

    private companion object {
        const val ROOT = "kicad/BlazeBasic.pretty"
        const val MAX_FILES = 50
        const val MAX_FILE_SIZE = 100_000
        val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.kicad_mod")
        val PACKAGE_FILES = listOf("fp-lib-table", "sym-lib-table", "BlazeCircuitLab.kicad_sym", "README.txt")
    }

    private fun checkedRead(path: String): ByteArray =
        readAsset(path).also { bytes ->
            require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_SIZE) {
                "KiCad resource is empty or oversized: $path"
            }
        }

    fun footprints(): Map<String, ByteArray> {
        val names = listAssets(ROOT) ?: error("Bundled KiCad footprint directory unavailable")
        val selected = names.filter { it.endsWith(".kicad_mod") }.sorted()
        require(selected.isNotEmpty() && selected.size <= MAX_FILES) {
            "Bundled KiCad footprint count is invalid"
        }
        require(selected.distinct().size == selected.size) { "Duplicate bundled KiCad footprint filename" }
        val files = linkedMapOf<String, ByteArray>()
        for (name in selected) {
            require(SAFE_NAME.matches(name) && ".." !in name) {
                "Unsafe bundled KiCad footprint filename: $name"
            }
            files["BlazeBasic.pretty/$name"] = checkedRead("$ROOT/$name")
        }
        return files
    }

    fun forPackage(): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        for (name in PACKAGE_FILES) files[name] = checkedRead("kicad/$name")
        files.putAll(footprints())
        return files
    }
}
