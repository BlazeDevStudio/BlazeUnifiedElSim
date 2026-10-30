package com.blaze.circuitlab.core

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/** KiCad 9 board handoff for the existing single-layer PCB workspace.
 *
 * Only original locally bundled BlazeBasic footprint geometries are embedded. Nets are derived
 * from the authoritative schematic graph; ratsnest connections are NEVER fabricated as copper.
 * This is an editable board handoff, NOT a fabrication guarantee or DRC approval.
 */
object KiCadBoard {
    data class Export(val pcb:String,val warnings:List<String>,val mappedPads:Int,val routedSegments:Int)
    private fun id(s:String)=UUID.nameUUIDFromBytes(s.toByteArray(StandardCharsets.UTF_8)).toString()
    private fun f(v:Double):String { require(v.isFinite());return String.format(Locale.US,"%.5f",v).trimEnd('0').trimEnd('.').ifEmpty{"0"} }
    private fun q(s:String):String {
        require(s.none{it.code<32}) { "Control character in KiCad text" }
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""
    }
    /** Find the matching closing paren without being confused by quoted strings in pads. */
    private fun closeParen(s:String,at:Int):Int {
        require(at in s.indices && s[at]=='(')
        var depth=0;var quoted=false;var escaped=false
        for(i in at until s.length){
            val ch=s[i]
            if(quoted){if(escaped)escaped=false else if(ch=='\\')escaped=true else if(ch=='"')quoted=false}
            else if(ch=='"') quoted=true
            else if(ch=='(')depth++
            else if(ch==')'){depth--;if(depth==0)return i;if(depth<0)break}
        }
        error("Malformed bundled footprint expression")
    }
    private val padStart=Regex("\\(pad\\s+\"([^\"]+)\"\\s+")
    private fun embed(raw:String,part:Part,pl:BoardPlacement,template:FootprintTemplate,netByPad:Map<PadKey,Pair<Int,String>>):String {
        val source=raw.trim()
        require(source.startsWith("(footprint ") && closeParen(source,0)==source.length-1) { "Malformed original footprint: ${template.libraryId}" }
        val start=Regex("^\\(footprint\\s+\"[^\"]+\"")
        require(start.find(source)!=null) { "Missing footprint header" }
        var fp=source.replaceFirst(start,"(footprint ${q(template.libraryId)}")
            .replaceFirst(Regex("\\s*\\(version\\s+\\d+\\)"),"")
            .replaceFirst(Regex("\\s*\\(generator\\s+\"[^\"]+\"\\)"),"")
        val spans=padStart.findAll(fp).map { m -> Triple(m.groupValues[1],m.range.first,closeParen(fp,m.range.first)) }.toList()
        require(spans.map{it.first}.toSet()==template.padOffsets.keys && spans.size==template.padOffsets.size) { "Original KiCad pad identities differ from catalog: ${template.libraryId}" }
        for((number,_,end) in spans.sortedByDescending{it.third}){
            val (net,name)=netByPad.getValue(PadKey(part.uuid,number))
            fp=fp.substring(0,end)+" (net $net ${q(name)})"+fp.substring(end)
        }
        val startProps="""
            (at ${f(pl.xMm)} ${f(pl.yMm)} ${pl.rotation})
            (uuid ${q(id("pcb-fp:${part.uuid}"))})
            (property "Reference" ${q(part.ref)} (at 0 -3 0) (layer "F.SilkS") (effects (font (size 1 1) (thickness 0.15))))
            (property "Value" ${q(part.value.toString())} (at 0 3 0) (layer "F.Fab") (effects (font (size 1 1) (thickness 0.15))))
        """.trimIndent()
        return fp.dropLast(1)+"\n$startProps\n)"
    }
    fun export(c:Circuit,footprintFiles:Map<String,ByteArray>):Export {
        c.validate();BoardPlanner.validate(c)
        require(c.board.placements.isNotEmpty()){ "Place at least one physical footprint before exporting a PCB" }
        val graph=Topology.build(c)
        val pads=BoardPlanner.pads(c)
        val routed=BoardRouting.resolve(c,pads)
        val airwires=BoardRouting.residualAirwires(pads,routed)
        val nets=pads.groupBy{it.net}.entries.sortedBy{entry->entry.value.minOf{it.ref+":"+it.pad}}
        val netInfo=nets.mapIndexed{index,entry->
            val name=if(entry.key==graph.groundNet)"GND" else "Net-(${entry.value.minWith(compareBy<BoardPlanner.Pad>{it.ref}.thenBy{it.pad}).let{it.ref+"-Pad"+it.pad}})"
            entry.key to ((index+1) to name)
        }.toMap()
        val byPad=pads.associate{PadKey(it.componentUuid,it.pad) to netInfo.getValue(it.net)}
        val embedded=c.board.placements.sortedBy{pl->c.parts.single{it.uuid==pl.partUuid}.ref}.map { pl ->
            val part=c.parts.single{it.uuid==pl.partUuid}
            require(pl.footprintId.startsWith("BlazeBasic:")) { "PCB export requires bundled footprint geometry: ${pl.footprintId}. External reference-only files cannot be embedded." }
            val template=FootprintCatalog.byId(pl.footprintId)?:error("Unsupported physical footprint")
            val file="BlazeBasic.pretty/${pl.footprintId.substringAfter(':')}.kicad_mod"
            val bytes=footprintFiles[file]?:error("Missing bundled KiCad footprint file: $file")
            require(bytes.size in 1..100_000){"Unexpected footprint file size: $file"}
            embed(bytes.toString(Charsets.UTF_8),part,pl,template,byPad)
        }
        val segments=routed.flatMap { path -> path.points.zipWithNext().mapIndexed{index,(a,b)->
            val net=netInfo.getValue(path.net).first
            "(segment (start ${f(a.x)} ${f(a.y)}) (end ${f(b.x)} ${f(b.y)}) (width ${f(path.route.widthMm)}) (layer ${q(path.route.layer)}) (net $net) (uuid ${q(id("pcb-segment:${path.route.uuid}:$index"))}))"
        }}
        val board=c.board
        val result="""(kicad_pcb
          (version 20241229) (generator "blaze_circuitlab")
          (general (thickness 1.6))
          (paper "A4")
          (layers (0 "F.Cu" signal) (31 "B.Cu" signal) (44 "Edge.Cuts" user))
          (setup (pad_to_mask_clearance 0))
          (net 0 "")
          ${netInfo.values.sortedBy{it.first}.joinToString("\n"){(n,name)->"(net $n ${q(name)})"}}
          ${embedded.joinToString("\n")}
          (gr_rect (start 0 0) (end ${f(board.widthMm)} ${f(board.heightMm)}) (stroke (width 0.05) (type default)) (fill none) (layer "Edge.Cuts") (uuid ${q(id("board-outline:${c.title}"))}))
          ${segments.joinToString("\n")}
        )"""
        require(KiCad.balanced(result)){"Unbalanced generated board"}
        val missing=c.parts.filter{it.type!=PartType.GND && c.board.placements.none{pl->pl.partUuid==it.uuid}}
        val warnings=mutableListOf<String>()
        if(missing.isNotEmpty())warnings.add("No physical PCB placement for: ${missing.joinToString(", "){it.ref}}")
        if(airwires.isNotEmpty())warnings.add("${airwires.size} unrouted copper connections remain; never assume ratsnest is copper")
        warnings.add("BlazeBasic footprints are provisional and not manufacturer-verified; board has no fabrication-grade DRC")
        return Export(result,warnings,pads.size,segments.size)
    }
}
