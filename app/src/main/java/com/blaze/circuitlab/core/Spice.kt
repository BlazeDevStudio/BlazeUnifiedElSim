package com.blaze.circuitlab.core

import java.math.BigDecimal

/** Experimental SPICE handoff using the same authoritative topology as MNA and KiCad.
 * Does not claim validation in ngspice. Unsupported nonlinear LED models are rejected, not invented.
 */
object Spice {
    data class Export(val netlist: String, val warnings: List<String>)
    private fun num(v:Double)=BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
    fun export(c:Circuit, stepSeconds:Double=0.001, stopSeconds:Double=0.1):Export {
        require(stepSeconds.isFinite() && stopSeconds.isFinite() && stepSeconds>0 && stopSeconds>=stepSeconds && stopSeconds<=3600.0) {
            "Invalid transient settings"
        }
        require(c.parts.all{it.ref.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,30}"))}) {
            "SPICE references must contain only letters, digits or underscore and start with a letter"
        }
        val g=Topology.build(c)
        require(g.groundNet!=null) { "SPICE export requires an explicit ground reference" }
        require(c.parts.none{it.type==PartType.OPAMP}) { "Powered rail-limited op-amp has no validated SPICE equivalent yet; use a verified macro-model before export" }
        require(c.parts.none{it.type==PartType.LED}) { "LED simulation model has no validated equivalent SPICE model; remove/replace LEDs before SPICE export" }
        fun net(n:Int)=if(n==g.groundNet)"0" else "N${n+1}"
        val diodes=c.parts.filter{it.type==PartType.D}
        val transistors=c.parts.filter{it.type==PartType.QNPN}
        val mosfets=c.parts.filter{it.type==PartType.MNMOS}
        require((diodes+transistors).map{it.temperatureC}.distinct().size<=1) { "SPICE export cannot express differing semiconductor instance temperatures; normalize temperature or export separately" }
        val warnings=mutableListOf<String>()
        val lines=mutableListOf("* Blaze CircuitLab experimental SPICE export", "* ${c.title.replace(Regex("[\r\n*]")," ").take(100)}")
        for(part in c.parts.sortedBy{it.ref}){
            if(part.type==PartType.GND)continue
            val a=net(g.net(part,0));val b=net(g.net(part,1))
            val value=num(part.value)
            val line=when(part.type){
                PartType.EVCVS->{
                    val op=net(g.net(part,2));val on=net(g.net(part,3))
                    // SPICE E: output+ output- control+ control- voltage gain.
                    "E_${part.ref} $op $on $a $b $value"
                }
                PartType.MNMOS->{
                    val s=net(g.net(part,2))
                    // SPICE M syntax is D G S B. Bulk tied to source; Blaze omits intrinsic body diode.
                    "M_${part.ref} $b $a $s $s BCL_NMOS_${part.ref}"
                }
                PartType.QNPN->{
                    val e=net(g.net(part,2))
                    // SPICE Q syntax is collector base emitter model. Physical pin mapping is separate.
                    "Q_${part.ref} $b $a $e BCL_NPN_${part.ref}"
                }
                PartType.D->{
                    // Each schematic diode gets its own explicit Is/n model; anode is pin0 and cathode pin1.
                    "D_${part.ref} $a $b BCL_MODEL_${part.ref}"
                }
                PartType.R->"${if(part.ref.startsWith("R",true))part.ref else "R_${part.ref}"} $a $b $value"
                PartType.C->"${if(part.ref.startsWith("C",true))part.ref else "C_${part.ref}"} $a $b $value"
                PartType.L->"${if(part.ref.startsWith("L",true))part.ref else "L_${part.ref}"} $a $b $value"
                PartType.I->"${if(part.ref.startsWith("I",true))part.ref else "I_${part.ref}"} $a $b $value"
                // Original alpha DC voltage source has + at RIGHT pin.
                PartType.V->"${if(part.ref.startsWith("V",true))part.ref else "V_${part.ref}"} $b $a $value"
                PartType.AC->"${if(part.ref.startsWith("V",true))part.ref else "V_${part.ref}"} $b $a SIN(0 $value ${num(part.frequency)})"
                PartType.SW->{
                    warnings.add("${part.ref}: finite-resistance switch is approximated by a resistor in SPICE")
                    "R_SWITCH_${part.ref} $a $b ${if(part.closed)"0.05" else "1e10"}"
                }
                PartType.GND,PartType.LED,PartType.OPAMP->error("Filtered")
            }
            // SPICE component names begin with element letter; reference suffixes are escaped to safe identifiers.
            lines.add(line)
        }
        if(diodes.isNotEmpty() || transistors.isNotEmpty()) {
            lines.add(".temp ${num((diodes+transistors).first().temperatureC)}")
            for(d in diodes.sortedBy{it.ref}) {
                lines.add(".model BCL_MODEL_${d.ref} D(Is=${num(d.value)} N=${num(d.diodeIdeality)})")
            }
            warnings.add("Shockley diode is idealized; SPICE and Blaze models omit breakdown, capacitance, series resistance and manufacturer tolerances")
        }
        for(q in transistors.sortedBy{it.ref}) {
            lines.add(".model BCL_NPN_${q.ref} NPN(Is=${num(q.transistorIsA)} Bf=${num(q.value)} Br=${num(q.transistorReverseBeta)})")
        }
        if(transistors.isNotEmpty())warnings.add("NPN exports a simplified Ebers-Moll model; SPICE physical effects, convergence and operating points may differ")
        for(m in mosfets.sortedBy{it.ref}){
            // SPICE level=1 uses Id=KP/2 * Vov² in saturation (lambda=0 by default).
            lines.add(".model BCL_NMOS_${m.ref} NMOS(Level=1 Vto=${num(m.value)} Kp=${num(m.mosfetK)} Lambda=0)")
        }
        if(mosfets.isNotEmpty())warnings.add("NMOS export ties bulk to source, but SPICE includes body junctions absent from Blaze; expect reverse-bias differences")
        lines.add(".tran ${num(stepSeconds)} ${num(stopSeconds)}")
        lines.add(".end")
        warnings.add("SPICE export has not been executed in ngspice; IC defaults and transient startup may differ from Blaze MNA")
        return Export(lines.joinToString("\n")+"\n",warnings)
    }
}
