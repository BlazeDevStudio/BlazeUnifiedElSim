package com.blaze.circuitlab.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.hypot

/** Board layout planning with approximate footprints; dimensions are NOT verified fabrication geometry. */
data class MmPoint(val x: Double, val y: Double) {
    fun rotated(degrees: Int): MmPoint {
        val rad = degrees * PI / 180.0
        return MmPoint(x*cos(rad)-y*sin(rad), x*sin(rad)+y*cos(rad))
    }
}

data class FootprintTemplate(
    val libraryId: String,
    val title: String,
    val supported: Set<PartType>,
    /** Approximate local pad centers; NOT an authoritative KiCad footprint definition. */
    val padOffsets: Map<String, MmPoint>,
    /** Explicit physical pad -> simulator terminal mapping. */
    val terminals: Map<String,Int>
)

object FootprintCatalog {
    private val originalAvailable = listOf(
        FootprintTemplate("BlazeBasic:R_0402", "Resistor 0402 reference package", setOf(PartType.R), mapOf("1" to MmPoint(-0.5,0.0), "2" to MmPoint(0.5,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:R_0603", "Resistor 0603 reference package", setOf(PartType.R), mapOf("1" to MmPoint(-0.75,0.0), "2" to MmPoint(0.75,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:R_0805", "Resistor 0805 reference package", setOf(PartType.R), mapOf("1" to MmPoint(-0.95,0.0), "2" to MmPoint(0.95,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:R_1206", "Resistor 1206 reference package", setOf(PartType.R), mapOf("1" to MmPoint(-1.55,0.0), "2" to MmPoint(1.55,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:R_Axial_7.62", "Axial resistor 7.62 mm pitch", setOf(PartType.R), mapOf("1" to MmPoint(-3.81,0.0), "2" to MmPoint(3.81,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:C_0402", "Capacitor 0402 reference package", setOf(PartType.C), mapOf("1" to MmPoint(-0.5,0.0), "2" to MmPoint(0.5,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:C_0603", "Capacitor 0603 reference package", setOf(PartType.C), mapOf("1" to MmPoint(-0.75,0.0), "2" to MmPoint(0.75,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:C_0805", "Capacitor 0805 reference package", setOf(PartType.C), mapOf("1" to MmPoint(-0.95,0.0), "2" to MmPoint(0.95,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:C_1206", "Capacitor 1206 reference package", setOf(PartType.C), mapOf("1" to MmPoint(-1.55,0.0), "2" to MmPoint(1.55,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:C_Disc_5.00", "Disc capacitor 5 mm pitch", setOf(PartType.C), mapOf("1" to MmPoint(-2.5,0.0), "2" to MmPoint(2.5,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:Q_TO92_EBC_2.54", "Provisional TO-92 layout, pad 1 E / 2 B / 3 C — verify specific transistor datasheet", setOf(PartType.QNPN),
            mapOf("1" to MmPoint(-2.54,0.0),"2" to MmPoint(0.0,0.0),"3" to MmPoint(2.54,0.0)),
            mapOf("1" to 2,"2" to 0,"3" to 1)),
        FootprintTemplate("BlazeBasic:M_TO92_GDS_2.54", "Provisional G/D/S TO-92-like layout; pad 1 G / 2 D / 3 S, not a manufacturer package", setOf(PartType.MNMOS),
            mapOf("1" to MmPoint(-2.54,0.0),"2" to MmPoint(0.0,0.0),"3" to MmPoint(2.54,0.0)),
            mapOf("1" to 0,"2" to 1,"3" to 2)),
        FootprintTemplate("BlazeBasic:D_DO35_7.62", "Generic diode body, 7.62 mm pitch — pad 1 cathode, pad 2 anode", setOf(PartType.D),
            mapOf("1" to MmPoint(3.81,0.0), "2" to MmPoint(-3.81,0.0)), mapOf("1" to 1,"2" to 0)),
        FootprintTemplate("BlazeBasic:LED_D3", "LED 3 mm through hole, pad 1 cathode, pad 2 anode", setOf(PartType.LED), mapOf("1" to MmPoint(1.27,0.0), "2" to MmPoint(-1.27,0.0)), mapOf("1" to 1,"2" to 0)),
        FootprintTemplate("BlazeBasic:LED_D5", "LED 5 mm through hole, pad 1 cathode, pad 2 anode", setOf(PartType.LED), mapOf("1" to MmPoint(1.27,0.0), "2" to MmPoint(-1.27,0.0)), mapOf("1" to 1,"2" to 0)),
        FootprintTemplate("BlazeBasic:LED_0805", "LED 0805, pad 1 cathode and pad 2 anode", setOf(PartType.LED), mapOf("1" to MmPoint(0.95,0.0), "2" to MmPoint(-0.95,0.0)), mapOf("1" to 1,"2" to 0)),
        FootprintTemplate("BlazeBasic:L_Axial_7.62", "Reference through-hole inductor 7.62 mm, verify actual device geometry", setOf(PartType.L),
            mapOf("1" to MmPoint(-3.81,0.0), "2" to MmPoint(3.81,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("BlazeBasic:SW_SPST_2Pad", "Generic SPST switch 2 pad reference; verify actual switch", setOf(PartType.SW), mapOf("1" to MmPoint(-2.54,0.0), "2" to MmPoint(2.54,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("Resistor_SMD:R_0805_2012Metric", "0805 resistor", setOf(PartType.R),
            mapOf("1" to MmPoint(-0.95,0.0), "2" to MmPoint(0.95,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P7.62mm_Horizontal", "Axial resistor 7.62 mm", setOf(PartType.R),
            mapOf("1" to MmPoint(-3.81,0.0), "2" to MmPoint(3.81,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("Capacitor_SMD:C_0805_2012Metric", "0805 capacitor", setOf(PartType.C),
            mapOf("1" to MmPoint(-0.95,0.0), "2" to MmPoint(0.95,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("Capacitor_THT:C_Disc_D5.0mm_W2.5mm_P5.00mm", "Disc capacitor 5 mm", setOf(PartType.C),
            mapOf("1" to MmPoint(-2.5,0.0), "2" to MmPoint(2.5,0.0)), mapOf("1" to 0,"2" to 1)),
        FootprintTemplate("LED_THT:LED_D3.0mm", "Through-hole LED 3 mm", setOf(PartType.LED),
            // The alpha LED is anode on the left, cathode on the right.
            mapOf("1" to MmPoint(1.27,0.0), "2" to MmPoint(-1.27,0.0)), mapOf("1" to 1,"2" to 0))
    )
    @Volatile private var bundledAvailable: List<FootprintTemplate>? = null
    val available: List<FootprintTemplate> get() = bundledAvailable ?: originalAvailable
    internal fun install(items: List<FootprintTemplate>) { bundledAvailable = items.toList() }
    fun forPart(p: Part):List<FootprintTemplate> = available.filter{p.type in it.supported}
    fun byId(id:String):FootprintTemplate? = available.find{it.libraryId==id}
}

data class BoardPlacement(
    val partUuid: String,
    val footprintId: String,
    var xMm: Double,
    var yMm: Double,
    var rotation: Int = 0
)

data class BoardLayout(
    var widthMm: Double = 70.0,
    var heightMm: Double = 50.0,
    val placements: MutableList<BoardPlacement> = mutableListOf(),
    val routes: MutableList<BoardRoute> = mutableListOf()
) {
    fun detached():BoardLayout = BoardLayout(widthMm,heightMm,placements.map{it.copy()}.toMutableList(),routes.map{it.copy(bends=it.bends.toList())}.toMutableList())
}

/** Ratsnest preview from authoritative schematic nets, not actual copper or completed routes. */
object BoardPlanner {
    data class Pad(val componentUuid:String,val ref:String,val pad:String,val net:Int,val at:MmPoint)
    data class Airwire(val net:Int,val a:Pad,val b:Pad,val distanceMm:Double)
    data class Preview(val pads:List<Pad>,val airwires:List<Airwire>,val warnings:List<String>)

    fun pads(c:Circuit):List<Pad> {
        val graph=Topology.build(c)
        return c.board.placements.flatMap{pl->
            val p=c.parts.single{it.uuid==pl.partUuid}
            val t=FootprintCatalog.byId(pl.footprintId)!!
            t.padOffsets.entries.sortedBy{it.key}.map{(pad,offset)->
                val transformed=offset.rotated(pl.rotation)
                Pad(p.uuid,p.ref,pad,graph.net(p,t.terminals.getValue(pad)),MmPoint(pl.xMm+transformed.x,pl.yMm+transformed.y))
            }
        }
    }

    fun validate(c:Circuit) {
        val b=c.board
        require(b.widthMm.isFinite() && b.heightMm.isFinite() && b.widthMm in 10.0..500.0 && b.heightMm in 10.0..500.0) {"Invalid board dimensions"}
        require(b.placements.size<=c.parts.size && b.placements.size<=200) {"Too many footprints"}
        require(b.placements.map{it.partUuid}.distinct().size==b.placements.size) {"Duplicate physical component"}
        for(pl in b.placements) {
            val p=c.parts.singleOrNull{it.uuid==pl.partUuid}?:error("Orphan board footprint")
            val tmpl=FootprintCatalog.byId(pl.footprintId)?:error("Unsupported footprint: ${pl.footprintId}")
            require(p.type in tmpl.supported) {"Incorrect component/footprint pairing"}
            require(p.footprint==tmpl.libraryId) {"Schematic and PCB footprint assignments disagree: ${p.ref}"}
            require(pl.xMm.isFinite()&&pl.yMm.isFinite()&&pl.xMm in 0.0..b.widthMm&&pl.yMm in 0.0..b.heightMm){"Footprint outside board: ${p.ref}"}
            require(pl.rotation in setOf(0,90,180,270)) {"Invalid board rotation"}
            require(tmpl.padOffsets.keys==tmpl.terminals.keys && tmpl.terminals.values.toSet()==p.pins().indices.toSet()) {"Invalid physical pin mapping"}
            for(point in tmpl.padOffsets.values){val at=point.rotated(pl.rotation)
                require(pl.xMm+at.x in 0.0..b.widthMm && pl.yMm+at.y in 0.0..b.heightMm){"Pad outside board: ${p.ref}"}
            }
        }
        // Check the same strict pin-to-pad contract used by the schematic/PCB handoff.
        BoardContract.resolvePads(c,b.placements.map { pl ->
            val t=FootprintCatalog.byId(pl.footprintId)!!
            PhysicalPadMapping(pl.partUuid,pl.footprintId,t.terminals)
        })
        BoardRouting.resolve(c,pads(c))
    }

    fun assign(c:Circuit, partUuid:String,templateId:String) {
        val p=c.parts.singleOrNull{it.uuid==partUuid}?:error("Component not found")
        val tmpl=FootprintCatalog.byId(templateId)?:error("Unknown footprint")
        require(p.type in tmpl.supported) {"Footprint not compatible with ${p.type}"}
        val before=c.detached()
        try {
            val existing=c.board.placements.indexOfFirst{it.partUuid==partUuid}
            val candidate=BoardPlacement(partUuid,templateId,
                (15.0+(c.board.placements.size%5)*10).coerceAtMost(c.board.widthMm-10),
                (15.0+(c.board.placements.size/5)*10).coerceAtMost(c.board.heightMm-10))
            if(existing<0)c.board.placements.add(candidate) else {
                val prev=c.board.placements[existing]
                c.board.placements[existing]=candidate.copy(xMm=prev.xMm,yMm=prev.yMm,rotation=prev.rotation)
            }
            p.footprint=templateId
            validate(c)
        }catch(e:Exception) {
            c.board.placements.clear();c.board.placements.addAll(before.board.placements)
            p.footprint=before.parts.single{it.uuid==p.uuid}.footprint
            throw e
        }
    }
    fun preview(c:Circuit):Preview {
        validate(c)
        val pins=pads(c)
        val routes=BoardRouting.resolve(c,pins)
        val air=BoardRouting.residualAirwires(pins,routes)
        val absent=c.parts.filter{it.type!=PartType.GND&&c.board.placements.none{pl->pl.partUuid==it.uuid}}
            .map{"${it.ref} has no physical footprint; not represented on PCB"}
        val unvalidated=if(c.board.placements.isNotEmpty())listOf("Approximate pad/track clearance screening only; no full DRC, zones, plated holes or manufacturing validation") else emptyList()
        return Preview(pins,air,absent+unvalidated)
    }
}
