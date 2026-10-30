package com.blaze.circuitlab.core

/** Explicit PCB footprint mapping contract. This is NOT a PCB editor or a Gerber generator. */
data class PhysicalPadMapping(
    val componentUuid:String,
    val footprintLibraryId:String,
    /** Actual physical pad name -> solver component pin index. Must be validated by the user. */
    val padToTerminal:Map<String,Int>
)

/** Data-only PCB handoff: links physical pad references to the authoritative circuit graph. */
object BoardContract {
    data class PhysicalNet(val componentRef:String,val pad:String,val electricalNet:Int)
    fun resolvePads(c:Circuit,mapping:List<PhysicalPadMapping>):List<PhysicalNet>{
        val topology=Topology.build(c)
        val duplicated=mapping.groupBy{it.componentUuid}.filterValues{it.size>1}
        require(duplicated.isEmpty()){ "Duplicate footprint assignment" }
        return mapping.flatMap{assignment->
            val part=c.parts.singleOrNull{it.uuid==assignment.componentUuid}?:error("Unknown schematic component")
            require(part.type!=PartType.GND && part.type!=PartType.V && part.type!=PartType.AC && part.type!=PartType.EVCVS && part.type!=PartType.OPAMP && part.type!=PartType.I) {
                "Simulation-only sources/ground require explicit physical conversion"
            }
            require(assignment.footprintLibraryId.contains(':') && assignment.footprintLibraryId.length<160) {"Invalid footprint library id"}
            require(assignment.padToTerminal.isNotEmpty()){"No pad-to-pin mapping"}
            require(assignment.padToTerminal.keys.all{it.isNotBlank()}){"Empty physical pad name"}
            require(assignment.padToTerminal.values.all{it in part.pins().indices}) {"Footprint pad points to invalid electrical pin"}
            require(assignment.padToTerminal.values.toSet().size==part.pins().size) {"Incomplete terminal mapping"}
            assignment.padToTerminal.toSortedMap().map{(pad,i)->PhysicalNet(part.ref,pad,topology.net(part,i))}
        }
    }
}

/** A future real firmware runner must implement this contract; none is implemented yet. */
interface FirmwareBackend {
    val supportedCpu:String
    fun loadCompiledFirmware(bytes:ByteArray):Boolean
    fun advanceInstructionBudget(maxInstructions:Int):Long
    fun gpioLevels():Map<Int,Boolean>
}
