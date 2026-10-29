package com.blaze.circuitlab

import android.content.res.AssetManager
import com.blaze.circuitlab.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Validated, on-device catalog loader. No online requests, hidden fallback or reflection.
 * The app MUST load the three installed schema-v2 files before accepting projects.
 * These JSON indexes are the source of truth; Kotlin's original lists remain only for
 * standalone JVM model tests and deterministic release-asset generation.
 */
object OfflineCatalogs {
    private const val LIMIT = 2_000_000
    @Volatile var loaded: Boolean = false
        private set

    private fun json(assets: AssetManager, path: String): JSONObject =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { source ->
            val contents = source.readText()
            require(contents.length in 2..LIMIT) { "Invalid size: $path" }
            JSONObject(contents).also { root ->
                require(root.getInt("schema") == 2) { "Unsupported $path schema (need v2)" }
            }
        }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.string(key: String): String = getString(key).also {
        require(it.isNotBlank() && it.length <= 300) { "Empty/overlong catalog field: $key" }
    }
    private fun JSONObject.finite(key: String): Double = getDouble(key).also {
        require(it.isFinite()) { "Invalid numerical catalog value: $key" }
    }

    fun install(assets: AssetManager) {
        loaded = false
        val electronics = json(assets,"catalog/electronics.json").getJSONArray("presets").objects().map { p ->
            ElectronicPreset(
                p.string("id"),p.string("title"),p.string("group"),
                PartType.valueOf(p.string("simulatorModel")),p.finite("defaultValue"),
                p.string("unit"),p.string("kicadSymbol"),p.string("description"),
                p.getJSONArray("footprints").strings(),p.getBoolean("approximateModel"),
                p.finite("frequency")
            )
        }
        val footprintsRoot = json(assets,"catalog/footprints.json")
        val footprints = footprintsRoot.getJSONArray("footprints").objects().map { f ->
            val id = f.string("id")
            val supported = f.getJSONArray("supported").strings().map { PartType.valueOf(it) }.toSet()
            val terminalsObject = f.getJSONObject("padToTerminal")
            val offsetsObject = f.getJSONObject("padOffsetsMm")
            val terminalKeys = terminalsObject.keys().asSequence().toSet()
            val offsetKeys = offsetsObject.keys().asSequence().toSet()
            require(terminalKeys.isNotEmpty() && terminalKeys == offsetKeys) { "Missing pad coordinates/mapping for $id" }
            val terminals = terminalKeys.associateWith { terminalsObject.getInt(it) }
            val offsets = offsetKeys.associateWith { pad ->
                val point = offsetsObject.getJSONObject(pad)
                MmPoint(point.finite("x"),point.finite("y")).also {
                    require(kotlin.math.abs(it.x) <= 100 && kotlin.math.abs(it.y) <= 100) { "Pad offset outside 100mm: $id" }
                }
            }
            require(supported.isNotEmpty()) { "Footprint has no electrical types: $id" }
            for (type in supported) {
                val count = Part(ref="TEST",type=type,x=0,y=0).pins().size
                require(terminals.values.toSet() == (0 until count).toSet()) {
                    "Footprint pad-to-terminal map does not cover $type: $id"
                }
            }
            val localFile = if(f.isNull("localKiCadFile")) null else f.string("localKiCadFile")
            if(id.startsWith("BlazeBasic:")) {
                val expected = "kicad/BlazeBasic.pretty/${id.substringAfter(':')}.kicad_mod"
                require(localFile == expected) { "Incorrect local KiCad footprint path: $id" }
            }
            if(localFile != null) {
                require(localFile.startsWith("kicad/BlazeBasic.pretty/") && !localFile.contains("..")) {
                    "Unsafe or external local footprint path: $id"
                }
                assets.open(localFile).use { stream ->
                    require(stream.read() != -1) { "Empty footprint file: $localFile" }
                }
            }
            FootprintTemplate(id,f.string("title"),supported,offsets,terminals)
        }
        val mcuRoot = json(assets,"catalog/mcu_pinouts.json")
        require(mcuRoot.getString("mode") == "reference-and-planning-only") { "Firmware support incorrectly advertised" }
        val boards = mcuRoot.getJSONArray("boards").objects().map { b ->
            val pins = b.getJSONArray("pins").objects().map { pin ->
                McuPin(pin.string("name"),pin.getJSONArray("functions").strings().toSet(),
                    pin.string("kind"),pin.getString("caution"),
                    if(pin.isNull("voltageV")) null else pin.finite("voltageV"),
                    pin.getString("header"))
            }
            require(pins.map{it.name}.distinct().size == pins.size) { "Repeated pin name on ${b.string("id")}" }
            McuBoard(b.string("id"),b.string("title"),b.finite("logicVoltageV"),
                b.string("description"),pins,b.string("reference"))
        }
        require(electronics.isNotEmpty() && electronics.size <= 30_000)
        require(footprints.isNotEmpty() && footprints.size <= 30_000)
        require(boards.isNotEmpty() && boards.size <= 500)
        require(electronics.map{it.id}.distinct().size == electronics.size) { "Duplicate electronics IDs" }
        require(footprints.map{it.libraryId}.distinct().size == footprints.size) { "Duplicate footprint IDs" }
        require(boards.map{it.id}.distinct().size == boards.size) { "Duplicate board IDs" }
        require(electronics.map{it.simulationType}.toSet() == PartType.values().toSet()) { "Required native model missing from catalog" }
        val footprintIds = footprints.map { it.libraryId }.toSet()
        for(e in electronics) {
            require(e.value.isFinite() && e.frequency > 0 && e.frequency.isFinite()) { "Invalid circuit preset: ${e.id}" }
            require(e.suggestedFootprints.all { it in footprintIds }) { "Unresolved footprint for ${e.id}" }
            require(e.suggestedFootprints.all { id ->
                e.simulationType in footprints.first { it.libraryId == id }.supported
            }) { "Wrong physical pad type for ${e.id}" }
        }
        // Commit only after validation of ALL catalogs to avoid partial-library state.
        ElectronicLibrary.install(electronics)
        FootprintCatalog.install(footprints)
        McuLibrary.install(boards)
        loaded = true
    }
}
