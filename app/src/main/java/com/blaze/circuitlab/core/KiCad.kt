package com.blaze.circuitlab.core

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.Locale

/** KiCad 9 S-expression candidate with simulation-only powered amplifier. Native KiCad ERC/opening remains unverified. */
object KiCad {
    data class Export(val schematic:String,val warnings:List<String>)
    private fun id(s:String)=UUID.nameUUIDFromBytes(s.toByteArray(StandardCharsets.UTF_8)).toString()
    private fun f(n:Double)=String.format(Locale.US,"%.5f",n).trimEnd('0').trimEnd('.').ifEmpty{"0"}
    // Component *values* must not use five decimal places: 1 nF and 1 uH would both export as zero.
    private fun valueNumber(n:Double):String = java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()
    private fun q(s:String):String {
        require(s.none{it.code<32 && it!='\n' && it!='\t'}){"Illegal KiCad text"}
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\t","\\t")+"\""
    }
    private fun xy(p:Point):String="(xy ${f(30+p.x*5.08/48.0)} ${f(30+p.y*5.08/48.0)})"
    private fun x(p:Point)=f(30+p.x*5.08/48.0)
    private fun y(p:Point)=f(30+p.y*5.08/48.0)
    private const val fx="(effects (font (size 1.27 1.27)))"
    private const val hidden="(effects (font (size 1.27 1.27)) (hide yes))"
    private const val stroke="(stroke (width 0) (type default))"
    private fun pin(num:String,name:String,x:String,y:String,angle:Int,type:String="passive")=
        "(pin $type line (at $x $y $angle) (length 2.54) (name ${q(name)} $fx) (number ${q(num)} $fx))"
    private fun line(vararg coords:Pair<String,String>):String = "(polyline (pts ${coords.joinToString(" "){ "(xy ${it.first} ${it.second})" }}) (stroke (width 0.254) (type default)) (fill (type none)))"
    private fun libraryPart(t:PartType):String {
        val name=when(t){PartType.V->"VDC";PartType.AC->"VSIN";PartType.R->"R";PartType.C->"C";PartType.L->"L";PartType.I->"IDC";PartType.LED->"LED";PartType.D->"D";PartType.SW->"SW_SPST";PartType.GND->"GND";PartType.QNPN->"QNPN";PartType.MNMOS->"MNMOS";PartType.EVCVS->"EVCVS";PartType.OPAMP->"OPAMP"}
        val prefix=when(t){PartType.V,PartType.AC->"V";PartType.R->"R";PartType.C->"C";PartType.L->"L";PartType.I->"I";PartType.LED,PartType.D->"D";PartType.SW->"SW";PartType.GND->"#PWR";PartType.QNPN->"Q";PartType.MNMOS->"Q";PartType.EVCVS->"E";PartType.OPAMP->"A"}
        val body=when(t){
            PartType.V,PartType.AC,PartType.I->"(circle (center 0 0) (radius 2.54) (stroke (width 0.254) (type default)) (fill (type none)))"
            PartType.L->(0..3).joinToString("") { j -> "(arc (start ${f(-2.54 + j*1.27)} 0) (mid ${f(-1.905 + j*1.27)} 1.27) (end ${f(-1.27 + j*1.27)} 0) (stroke (width 0.254) (type default)) (fill (type none)))" }
            PartType.R->"(rectangle (start -2.54 1.27) (end 2.54 -1.27) (stroke (width 0.254) (type default)) (fill (type none)))"
            PartType.C->line("-1.27" to "2.54","-1.27" to "-2.54")+line("1.27" to "2.54","1.27" to "-2.54")
            PartType.SW->line("-2.54" to "0","1.27" to "1.27")
            PartType.D->line("1.27" to "1.27","1.27" to "-1.27","-1.27" to "0","1.27" to "1.27")+line("-1.27" to "1.27","-1.27" to "-1.27")
            PartType.LED->line("1.27" to "1.27","1.27" to "-1.27","-1.27" to "0","1.27" to "1.27")+line("-1.27" to "1.27","-1.27" to "-1.27")
            PartType.QNPN->line("-2.54" to "0","0" to "0")+line("0" to "2.54","0" to "-2.54")
            PartType.MNMOS->line("-2.54" to "0","-1.27" to "0")+line("0" to "2.54","0" to "-2.54")
            PartType.EVCVS->"(rectangle (start -2.54 5.08) (end 2.54 -5.08) (stroke (width 0.254) (type default)) (fill (type none)))"
            PartType.OPAMP->line("-2.54" to "5.08","2.54" to "0","-2.54" to "-5.08","-2.54" to "5.08")
            PartType.GND->line("0" to "0","0" to "-1.27")+line("-2.54" to "-1.27","2.54" to "-1.27")
            else->error("Unsupported")
        }
        val pins=when(t){
            PartType.V,PartType.AC->pin("2","-","-5.08","0",0)+pin("1","+","5.08","0",180)
            PartType.I->pin("1","in","-5.08","0",0)+pin("2","out","5.08","0",180)
            PartType.R,PartType.C,PartType.SW,PartType.L->pin("1","~","-5.08","0",0)+pin("2","~","5.08","0",180)
            PartType.QNPN->pin("2","B","-5.08","0",0,"input")+pin("3","C","5.08","3.81",180,"passive")+pin("1","E","5.08","-3.81",180,"passive")
            PartType.EVCVS->pin("1","IN+","-5.08","3.81",0,"input")+pin("2","IN-","-5.08","-3.81",0,"input")+pin("3","OUT+","5.08","3.81",180,"output")+pin("4","OUT-","5.08","-3.81",180,"output")
            PartType.OPAMP->pin("1","IN+","-5.08","3.81",0,"input")+pin("2","IN-","-5.08","-3.81",0,"input")+pin("3","OUT","5.08","0",180,"output")+pin("4","V+","0","5.08",270,"power_in")+pin("5","V-","0","-5.08",90,"power_in")
            PartType.MNMOS->pin("1","G","-5.08","0",0,"input")+pin("2","D","5.08","3.81",180,"passive")+pin("3","S","5.08","-3.81",180,"passive")
            PartType.D,PartType.LED->pin("2","A","-5.08","0",0)+pin("1","K","5.08","0",180)
            PartType.GND->"(pin power_in line (at 0 0 90) (length 0) (name \"GND\" $fx) (number \"1\" $fx))"
            else->error("Unsupported")
        }
        val onBoard=if(t==PartType.V||t==PartType.AC||t==PartType.I||t==PartType.EVCVS||t==PartType.OPAMP||t==PartType.GND)"no" else "yes"
        return """(symbol "BlazeCircuitLab:$name" ${if(t==PartType.GND)"(power)" else ""}
           (pin_numbers (hide yes)) (pin_names (offset 0) (hide yes)) (in_bom ${if(t==PartType.GND)"no" else "yes"}) (on_board $onBoard)
           (property "Reference" ${q(prefix)} (at 0 5.08 0) $fx)
           (property "Value" ${q(name)} (at 0 -5.08 0) $fx)
           (symbol "${name}_0_1" $body)
           (symbol "${name}_1_1" $pins))"""
    }
    private fun property(name:String,value:String,x:String,y:String,hide:Boolean=false):String=
        "(property ${q(name)} ${q(value)} (at $x $y 0) ${if(hide) hidden else fx})"
    fun export(c:Circuit):Export {
        Topology.build(c)
        // KiCad wires must be split at electrically connected interior junctions and pin contact points.
        val split=c.wires.flatMap{w->
            val cut=(listOf(w.a,w.b)+c.junctions.map{it.at}.filter{Topology.onSegment(it,w)}).distinct()
                .sortedWith(if(w.a.x==w.b.x)compareBy<Point>{it.y}else compareBy<Point>{it.x})
            cut.zipWithNext().mapIndexed { i,(a,b)-> Triple(w.uuid,i,a to b) }
        }
        val root=id("root:${c.title}:${c.parts.map{it.uuid}.sorted()}")
        val symbols=c.parts.map{it.type}.distinct().sortedBy{it.name}.joinToString("\n"){libraryPart(it)}
        val sch="""(kicad_sch
          (version 20250114) (generator "blaze_circuitlab") (generator_version "0.2.9-native-candidate")
          (uuid ${q(root)}) (paper "A4")
          (lib_symbols $symbols)
          ${c.junctions.joinToString("\n"){"(junction (at ${x(it.at)} ${y(it.at)}) (diameter 0) (color 0 0 0 0) (uuid ${q(id("junction:${it.uuid}"))}))"}}
          ${split.joinToString("\n"){(sid,i,pair)->"(wire (pts ${xy(pair.first)} ${xy(pair.second)}) $stroke (uuid ${q(id("wire:$sid:$i"))}))"}}
          ${c.parts.sortedBy{it.ref}.joinToString("\n"){p->
              val name=when(p.type){PartType.V->"VDC";PartType.AC->"VSIN";PartType.R->"R";PartType.C->"C";PartType.L->"L";PartType.I->"IDC";PartType.LED->"LED";PartType.D->"D";PartType.SW->"SW_SPST";PartType.GND->"GND";PartType.QNPN->"QNPN";PartType.MNMOS->"MNMOS";PartType.EVCVS->"EVCVS";PartType.OPAMP->"OPAMP"}
              val at=if(p.type==PartType.GND)p.pins()[0] else Point(p.x,p.y)
              val pn=when(p.type){PartType.V,PartType.AC->listOf("2","1");PartType.I->listOf("1","2");PartType.LED,PartType.D->listOf("2","1");PartType.R,PartType.C,PartType.SW,PartType.L->listOf("1","2");PartType.GND->listOf("1");PartType.QNPN->listOf("2","3","1");PartType.MNMOS->listOf("1","2","3");PartType.EVCVS->listOf("1","2","3","4");PartType.OPAMP->listOf("1","2","3","4","5")}
              val value=when(p.type){PartType.GND->"GND";PartType.QNPN->"NPN BF=${valueNumber(p.value)} IS=${valueNumber(p.transistorIsA)}";PartType.MNMOS->"NMOS Vth=${valueNumber(p.value)} K=${valueNumber(p.mosfetK)}";PartType.EVCVS->"VCVS Gain=${valueNumber(p.value)}";PartType.OPAMP->"Powered Amp Gain=${valueNumber(p.value)} Headroom=${valueNumber(p.opampHeadroomV)}V";PartType.LED->"LED Vf=${valueNumber(p.value)}V";PartType.D->"D Is=${valueNumber(p.value)}A n=${valueNumber(p.diodeIdeality)}";PartType.R,PartType.C,PartType.L->valueNumber(p.value);PartType.SW->"SW_SPST";PartType.V,PartType.AC->valueNumber(p.value)+"V";PartType.I->valueNumber(p.value)+"A"}
              """(symbol (lib_id "BlazeCircuitLab:$name") (at ${x(at)} ${y(at)} ${(360-p.rotation)%360}) (unit 1)
                    (in_bom ${if(p.type==PartType.GND)"no" else "yes"}) (on_board ${if(p.type==PartType.V||p.type==PartType.AC||p.type==PartType.I||p.type==PartType.EVCVS||p.type==PartType.OPAMP||p.type==PartType.GND)"no" else "yes"}) (dnp no)
                    (uuid ${q(id("symbol:${p.uuid}"))})
                    ${property("Reference",p.ref,x(at),f(22+at.y*5.08/48.0))}
                    ${property("Value",value,x(at),f(38+at.y*5.08/48.0))}
                    ${property("Footprint",p.footprint?:"",x(at),y(at),true)}
                    ${pn.joinToString(" "){"(pin ${q(it)} (uuid ${q(id("pin:${p.uuid}:$it"))}))"}}
                    (instances (project ${q(c.title.replace(Regex("[^a-zA-Z0-9_-]"),"_").take(54))} (path ${q("/$root")} (reference ${q(p.ref)}) (unit 1)))))"""
          }}
          (sheet_instances (path "/" (page "1")))
        )
"""
        require(sch.count{it=='('} > 0 && sch.contains("(kicad_sch"))
        val warnings=c.parts.mapNotNull{p->when{
            p.type==PartType.V||p.type==PartType.AC||p.type==PartType.I||p.type==PartType.EVCVS||p.type==PartType.OPAMP||p.type==PartType.GND->"${p.ref}: simulation/power object; not a physical PCB footprint"
            p.footprint.isNullOrBlank()->"${p.ref}: physical footprint not assigned"
            else->"${p.ref}: assigned footprint is not verified in a KiCad library"
        }}
        return Export(sch,warnings+"KiCad 9 target: native KiCad opening and ERC not performed in this environment")
    }
    /** Original embedded symbol definitions also shipped as a local, editable native KiCad library. */
    fun symbolLibrary():String = """(kicad_symbol_lib
        (version 20231120)
        (generator "blaze_circuitlab")
        ${PartType.values().joinToString("\n") { libraryPart(it).replace("BlazeCircuitLab:", "") }}
    )"""
    fun balanced(text:String):Boolean {
        var depth=0;var quoted=false;var escape=false
        for(ch in text)if(quoted){if(escape)escape=false else if(ch=='\\')escape=true else if(ch=='\"')quoted=false}
        else if(ch=='\"')quoted=true else if(ch=='(')depth++ else if(ch==')'){depth--;if(depth<0)return false}
        return !quoted && !escape && depth==0
    }
}
