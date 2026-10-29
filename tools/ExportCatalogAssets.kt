import com.blaze.circuitlab.core.*
import java.io.File

/** Developer build-time export of the very same catalog backing the native Android browser.
 * Does not fetch or copy upstream libraries. Kotlin registry is the authoritative index.
 */
fun j(s:String):String="\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""
fun main(){
    val dir=File("app/src/main/assets/catalog").also{it.mkdirs()}
    val parts=ElectronicLibrary.presets.joinToString(",\n") { e ->
        "{\"id\":${j(e.id)},\"title\":${j(e.title)},\"group\":${j(e.group)},"+
        "\"simulatorModel\":${j(e.simulationType.name)},\"defaultValue\":${e.value},"+
        "\"unit\":${j(e.unit)},\"kicadSymbol\":${j(e.kicadSymbol)},"+
        "\"approximateModel\":${e.approximateModel},"+
        "\"description\":${j(e.description)},"+
        "\"footprints\":[${e.suggestedFootprints.joinToString(","){j(it)}}]}"
    }
    File(dir,"electronics.json").writeText("{\"schema\":1,\"presets\":[\n$parts\n]}\n")
    val boards=McuLibrary.boards.joinToString(",\n") { b ->
        val pins=b.pins.joinToString(",\n") { p ->
            "{\"name\":${j(p.name)},\"kind\":${j(p.kind)},\"functions\":[${p.functions.joinToString(","){j(it)}}],"+
            "\"caution\":${j(p.caution)},\"voltageV\":${p.voltageV?.toString()?:"null"}}"
        }
        "{\"id\":${j(b.id)},\"title\":${j(b.title)},\"logicVoltageV\":${b.logicVoltage},"+
            "\"description\":${j(b.description)},\"reference\":${j(b.source)},\"pins\":[\n$pins\n]}"
    }
    File(dir,"mcu_pinouts.json").writeText("{\"schema\":1,\"mode\":\"reference-and-planning-only\",\"boards\":[\n$boards\n]}\n")
    val footprints=FootprintCatalog.available.joinToString(",\n") { t ->
        "{\"id\":${j(t.libraryId)},\"title\":${j(t.title)},\"supported\":[${t.supported.joinToString(","){j(it.name)}}],"+
            "\"padToTerminal\":{${t.terminals.entries.joinToString(","){j(it.key)+":"+it.value}}},"+
            "\"localKiCadFile\":${if(t.libraryId.startsWith("BlazeBasic:"))j("kicad/BlazeBasic.pretty/${t.libraryId.substringAfter(':')}.kicad_mod") else "null"}}"
    }
    File(dir,"footprints.json").writeText("{\"schema\":1,\"footprints\":[\n$footprints\n]}\n")
    println("Wrote ${ElectronicLibrary.presets.size} electronics presets, ${McuLibrary.boards.size} MCU boards and ${FootprintCatalog.available.size} footprint references")
}
