package com.blaze.circuitlab.core

/** Offline engineering BOM for a schematic and its optional PCB placements.
 * Informational only: no invented manufacturer part numbers, sourcing, inventory,
 * footprint dimensional certifications, or PCB manufacturing approval.
 */
object Bom {
    private fun csv(value: String): String {
        val safe = if(value.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun unit(part: Part): String = when(part.type) {
        PartType.R -> "ohm"
        PartType.C -> "F"
        PartType.L -> "H"
        PartType.I, PartType.D -> "A"
        PartType.V, PartType.AC, PartType.LED, PartType.MNMOS -> "V"
        PartType.QNPN, PartType.EVCVS, PartType.OPAMP -> "ratio"
        PartType.SW -> "state"
        PartType.GND -> "reference"
    }

    fun export(c: Circuit): String {
        c.validate()
        Topology.build(c)
        BoardPlanner.validate(c)
        val placements = c.board.placements.associateBy { it.partUuid }
        val cols = listOf("Reference", "Model", "Value", "Unit", "Catalog ID", "Footprint ID", "PCB placement", "Offline footprint status", "Engineering note")
        val lines = c.parts.sortedWith(compareBy<Part>({it.type.name}, {it.ref})).map { p ->
            val placement = placements[p.uuid]
            val f = p.footprint?.let { id ->
                FootprintCatalog.byId(id) ?: error("Unknown physical footprint on ${p.ref}: $id")
            }
            val simulationOnly = p.type in setOf(PartType.V, PartType.AC, PartType.I, PartType.GND, PartType.EVCVS, PartType.OPAMP)
            val status = when {
                simulationOnly -> "SCHEMATIC_ONLY"
                f == null -> "NO_FOOTPRINT"
                !f.libraryId.startsWith("BlazeBasic:") -> "EXTERNAL_REFERENCE_NOT_BUNDLED"
                placement == null -> "NOT_PLACED"
                else -> "BUNDLED_REFERENCE_UNVERIFIED"
            }
            val warning = when(status) {
                "SCHEMATIC_ONLY" -> "Ideal electrical/source part; requires a real physical circuit before PCB fabrication"
                "NO_FOOTPRINT" -> "Select a physical package and verify manufacturer pinout"
                "EXTERNAL_REFERENCE_NOT_BUNDLED" -> "Footprint file not bundled; verify the official KiCad library version"
                "NOT_PLACED" -> "Schematic footprint specified but absent from PCB placements"
                else -> "Reference geometry only; check datasheet and manufacturing rules"
            }
            listOf(p.ref, p.type.name, p.value.toString(), unit(p), p.catalogId.orEmpty(),
                p.footprint.orEmpty(), placement?.let { "${it.xMm},${it.yMm} mm; ${it.rotation} deg" }.orEmpty(),
                status,warning).joinToString(",",transform=::csv)
        }
        return (listOf(cols.joinToString(",",transform=::csv)) + lines).joinToString("\r\n",postfix="\r\n")
    }
}