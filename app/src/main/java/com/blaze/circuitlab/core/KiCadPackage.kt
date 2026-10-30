package com.blaze.circuitlab.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Self-contained KiCad schematic handoff: the original BlazeBasic footprint library is bundled.
 * A schematic + footprints ZIP is not a routed PCB nor fabrication-ready output.
 */
object KiCadPackage {
    fun pack(c: Circuit, footprintFiles: Map<String, ByteArray>): ByteArray {
        val result = KiCad.export(c)
        val files = footprintFiles.filterKeys { it == "fp-lib-table" || it == "sym-lib-table" || it == "BlazeCircuitLab.kicad_sym" || it == "README.txt" ||
            (it.startsWith("BlazeBasic.pretty/") && it.endsWith(".kicad_mod")) }
        require(files.keys.containsAll(listOf("fp-lib-table","sym-lib-table","BlazeCircuitLab.kicad_sym"))) {
            "Missing required bundled KiCad symbol/footprint table files"
        }
        val used=c.parts.mapNotNull{it.footprint}.filter{it.startsWith("BlazeBasic:")}
        for(id in used){require("BlazeBasic.pretty/${id.substringAfter(':')}.kicad_mod" in files){"Missing bundled footprint $id"}}
        require(files.size<=50 && files.values.sumOf{it.size.toLong()}<=5_000_000) { "Library resource limit" }
        val safe=c.title.replace(Regex("[^A-Za-z0-9_-]"),"_").take(50).ifBlank{"Circuit"}
        val board=if(c.board.placements.isNotEmpty() && c.board.placements.all{it.footprintId.startsWith("BlazeBasic:")})
            KiCadBoard.export(c,files)
        else null // External footprint IDs cannot be materialized from this ZIP.
        val entries=sortedMapOf<String,ByteArray>()
        // Same-stem project loads fp-lib-table (KIPRJMOD) for KiCad 9 CLI and GUI.
        // '{}' is native JSON with default project settings, not a waiver of DRC.
        entries["$safe.kicad_pro"]="{}\\n".toByteArray(Charsets.UTF_8)
        entries["$safe.kicad_sch"]=result.schematic.toByteArray(Charsets.UTF_8)
        if(board!=null)entries["$safe.kicad_pcb"]=board.pcb.toByteArray(Charsets.UTF_8)
        entries["README.txt"]=("Blaze CircuitLab KiCad 9 experimental schematic export.\n"+
            "Open the matching .kicad_pro project in KiCad 9 before board DRC; local footprints resolve through fp-lib-table.\n"+
            "Eleven supported circuit classes including ground are exported as editable BlazeCircuitLab symbols.\n"+
            (if(board!=null)"Includes editable .kicad_pcb with actual placed footprints and manually drawn F.Cu tracks only.\n" else "No PCB board included; no local placements or unsupported external footprints.\n")+
            "No firmware emulation, no DRC approval, no production Gerbers.\n"+
            "Physical footprint geometry is preliminary; verify sizes, ERC and DRC before manufacture.\n"+
            "WARNINGS:\n"+(result.warnings+(board?.warnings?:emptyList())).joinToString("\n")+"\n").toByteArray(Charsets.UTF_8)
        for((name,bytes) in files)entries[if(name=="README.txt")"LIBRARY-NOTES.txt" else name]=bytes
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use{zip->
            for((name,bytes) in entries){require(".." !in name && !name.startsWith('/'))
                val entry=ZipEntry(name);entry.time=0L;zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry()}
        }
        return out.toByteArray()
    }
}
