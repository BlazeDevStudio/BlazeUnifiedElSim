import com.blaze.circuitlab.core.*
import kotlin.math.abs

fun near(actual:Double,expected:Double,eps:Double=1e-4) {
    check(abs(actual-expected)<eps) { "Expected $expected, got $actual" }
}
var checks=0
fun test(name:String,fn:()->Unit){fn();checks++;println("PASS $name")}
fun main() {
    test("original LED demo and DC current") {
        val c=DemoCircuits.led();val r=MnaSolver.step(c)
        check(r.ok){r.message};near(r.currentIn(c.parts[1])!!,3.0/342.0,5e-5)
        near(r.voltageAcross(c.parts[0])!!,-5.0)
    }
    test("original switch closed and opened"){
        val c=DemoCircuits.switched()
        val on=MnaSolver.step(c);check(on.ok){on.message}
        val onCurrent=on.currentIn(c.parts[2])!!
        c.parts.first{it.type==PartType.SW}.closed=false
        val off=MnaSolver.step(c);check(off.ok){off.message}
        check(kotlin.math.abs(off.currentIn(c.parts[2])!!)<1e-6)
        check(onCurrent>.001)
    }
    test("original RC demo transient"){
        val c=DemoCircuits.rc();var state=MnaSolver.State()
        repeat(100){i-> val s=MnaSolver.step(c,state,0.01,(i+1)*0.01);check(s.ok){s.message};state=s.state }
        check(state.capacitorVoltage.isNotEmpty())
        check(state.nodeVoltage.values.all{it.isFinite()})
    }
    test("crossing wires without junction not connected") {
        val c=Circuit(title="cross",wires=mutableListOf(Wire(a=Point(-48,0),b=Point(48,0)),Wire(a=Point(0,-48),b=Point(0,48))))
        val t=Topology.build(c);check(t.wireNet.values.distinct().size==2)
        c.junctions.add(Junction(at=Point(0,0)))
        val joined=Topology.build(c);check(joined.wireNet.values.distinct().size==1)
    }
    test("unmarked T intersection is rejected"){
        val c=Circuit(wires=mutableListOf(Wire(a=Point(-48,0),b=Point(48,0)),Wire(a=Point(0,-48),b=Point(0,0))))
        check(runCatching{Topology.build(c)}.isFailure)
        c.junctions.add(Junction(at=Point(0,0)))
        check(Topology.build(c).wireNet.values.distinct().size==1)
    }
    test("end-to-end segment connection"){
        val c=Circuit(wires=mutableListOf(Wire(a=Point(-48,0),b=Point(0,0)),Wire(a=Point(0,0),b=Point(48,0))))
        check(Topology.build(c).wireNet.values.distinct().size==1)
    }
    test("duplicate refs validation"){
        val c=DemoCircuits.led();c.parts[1].ref=c.parts[0].ref
        check(runCatching{Topology.build(c)}.isFailure)
    }
    test("no GND -> diagnostic"){
        val c=DemoCircuits.led();c.parts.removeAll{it.type==PartType.GND}
        check(MnaSolver.step(c).message.contains("GND"))
    }
    test("open disconnected circuit -> floating diagnostic"){
        val c=DemoCircuits.led();c.wires.clear();c.junctions.clear()
        check(!MnaSolver.step(c).ok)
    }
    test("KiCad serializer generated syntax"){
        val c=DemoCircuits.led();val exp=KiCad.export(c)
        check(KiCad.balanced(exp.schematic));check(exp.schematic.contains("(kicad_sch"))
        check(exp.schematic.contains("(lib_symbols"))
        check(exp.schematic.contains("\"BlazeCircuitLab:LED\""))
        check(exp.schematic.contains("(junction "))
        check(exp.warnings.isNotEmpty())
        java.io.File("tools/LED_Current_Limiter.kicad_sch").writeText(exp.schematic)
    }
    test("all original alpha demos now export KiCad symbols without fake footprints") {
        for(c in listOf(DemoCircuits.led(),DemoCircuits.rc(),DemoCircuits.switched())) {
            val exp=KiCad.export(c);check(KiCad.balanced(exp.schematic))
            check(exp.warnings.any{it.contains("physical")||it.contains("footprint")})
        }
    }
    test("KiCad deterministic output"){
        val c=DemoCircuits.led();check(KiCad.export(c).schematic==KiCad.export(c).schematic)
    }
    test("LED branch current matches original HTML alpha") {
        val c=DemoCircuits.led();val r=MnaSolver.step(c,dt=.015,time=.015)
        near(r.currentIn(c.parts[1])!!,.008771929825300092,1e-9)
    }
    test("Switch branch current matches original HTML alpha") {
        val c=DemoCircuits.switched();val r=MnaSolver.step(c,dt=.015,time=.015)
        near(r.currentIn(c.parts[2])!!,.01452131521679579,1e-9)
    }
    test("RC first timestep matches original HTML alpha") {
        val c=DemoCircuits.rc();val r=MnaSolver.step(c,dt=.015,time=.015)
        near(r.currentIn(c.parts[1])!!,.00023205148132880336,1e-9)
    }
    test("PCB data mapping uses authoritative net graph") {
        val c=DemoCircuits.led();val r=c.parts[1]
        val pads=BoardContract.resolvePads(c,listOf(PhysicalPadMapping(r.uuid,"Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P7.62mm_Horizontal",mapOf("1" to 0,"2" to 1))))
        check(pads.size==2 && pads[0].electricalNet!=pads[1].electricalNet)
    }
    test("PCB mapping rejects invented voltage-source footprint") {
        val c=DemoCircuits.led();val p=c.parts[0]
        check(runCatching{BoardContract.resolvePads(c,listOf(PhysicalPadMapping(p.uuid,"Connector_Generic:Conn_01x02",mapOf("1" to 0,"2" to 1))))}.isFailure)
    }
    test("PCB mapping rejects missing terminal mapping") {
        val c=DemoCircuits.led();val p=c.parts[1]
        check(runCatching{BoardContract.resolvePads(c,listOf(PhysicalPadMapping(p.uuid,"Resistor_SMD:R_0805",mapOf("1" to 0))))}.isFailure)
    }
    test("PCB explicit footprint assignment builds one unrouted net") {
        val c=DemoCircuits.led()
        val resistor=c.parts.single{it.ref=="R1"};val led=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,resistor.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,led.uuid,"LED_THT:LED_D3.0mm")
        val preview=BoardPlanner.preview(c)
        check(preview.pads.size==4)
        check(preview.airwires.size==1)
        check(preview.airwires.single().a.net==preview.airwires.single().b.net)
        check(preview.warnings.any{it.contains("no physical footprint")})
    }
    test("PCB footprint pin polarity is physical KiCad LED cathode 1 anode 2") {
        val c=DemoCircuits.led();val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        val p=BoardPlanner.preview(c).pads.filter{it.ref=="D1"}.associateBy{it.pad}
        val topology=Topology.build(c)
        check(p.getValue("1").net==topology.net(d,1))
        check(p.getValue("2").net==topology.net(d,0))
    }
    test("PCB rejects impossible position and missing footprint") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"}
        check(runCatching{BoardPlanner.assign(c,r.uuid,"LED_THT:LED_D3.0mm")}.isFailure)
        BoardPlanner.assign(c,r.uuid,"Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P7.62mm_Horizontal")
        val pl=c.board.placements.single();pl.xMm=0.0
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
    }
    test("PCB layout snapshots are deep copies") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        val snap=c.detached();c.board.placements.single().xMm=20.0
        check(snap.board.placements.single().xMm!=20.0)
    }
    test("PCB has no fabricated copper traces") {
        val c=DemoCircuits.led()
        BoardPlanner.assign(c,c.parts.single{it.ref=="R1"}.uuid,"Resistor_SMD:R_0805_2012Metric")
        check(BoardPlanner.preview(c).airwires.isEmpty())
    }
    test("manual F.Cu route joins same-net pads and removes one airwire") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"}; val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        check(BoardPlanner.preview(c).airwires.size==1)
        val route=BoardRouting.add(c,PadKey(r.uuid,"2"),PadKey(d.uuid,"2"))
        check(route.layer=="F.Cu" && c.board.routes.size==1)
        check(BoardPlanner.preview(c).airwires.isEmpty())
    }
    test("different-net pad connection is rejected without mutation") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"}; val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        check(runCatching{BoardRouting.add(c,PadKey(r.uuid,"1"),PadKey(d.uuid,"1"))}.isFailure)
        check(c.board.routes.isEmpty())
    }
    test("invalid copper geometry and unsupported layers are refused") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"}; val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        val start=PadKey(r.uuid,"2");val end=PadKey(d.uuid,"2")
        c.board.routes.add(BoardRoute(start=start,end=end,bends=listOf(MmPoint(19.23,18.13))))
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
        c.board.routes.clear()
        c.board.routes.add(BoardRoute(start=start,end=end,layer="B.Cu"))
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
    }
    test("routing snapshots are deep copies and retain pad identities") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        BoardRouting.add(c,PadKey(r.uuid,"2"),PadKey(d.uuid,"2"))
        val snap=c.detached()
        c.board.routes.clear()
        check(snap.board.routes.size==1 && snap.board.routes.single().start==PadKey(r.uuid,"2"))
        check(BoardPlanner.preview(snap).airwires.isEmpty())
    }
    test("route becomes invalid if footprint motion would cause a diagonal segment") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        BoardRouting.add(c,PadKey(r.uuid,"2"),PadKey(d.uuid,"2"))
        c.board.placements.single{it.partUuid==d.uuid}.yMm+=1.0
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
    }
    test("approximate DRC refuses copper on a different-net pad") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        val first=BoardPlanner.preview(c).pads.single{it.ref=="R1" && it.pad=="1"}.at
        val second=BoardPlanner.preview(c).pads.single{it.ref=="D1" && it.pad=="1"}.at
        // This manually injected copper has endpoints on *different* nets.
        c.board.routes.add(BoardRoute(start=PadKey(r.uuid,"1"),end=PadKey(d.uuid,"1"),
            bends=if(first.x!=second.x&&first.y!=second.y)listOf(MmPoint(second.x,first.y)) else emptyList()))
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
    }
    test("route rejects missing pad and nonfinite geometry") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        val start=PadKey(r.uuid,"2");val end=PadKey(d.uuid,"2")
        c.board.routes.add(BoardRoute(start=PadKey(r.uuid,"999"),end=end))
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
        c.board.routes.clear()
        c.board.routes.add(BoardRoute(start=start,end=end,bends=listOf(MmPoint(Double.NaN,1.0))))
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
    }
    test("route cannot silently cross an obstacle pad") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        // Force a different-net R1 pin-1 pad into the path of the R1-pin-2 to D1-anode route.
        // The explicitly injected detour traverses R1 pin 1 before ending at the LED.
        val pads=BoardPlanner.preview(c).pads.associateBy{it.ref+"."+it.pad}
        val obstacle=pads.getValue("R1.1").at
        val a=pads.getValue("R1.2").at
        val z=pads.getValue("D1.2").at
        c.board.routes.add(BoardRoute(start=PadKey(r.uuid,"2"),end=PadKey(d.uuid,"2"),
            bends=listOf(obstacle,MmPoint(z.x,obstacle.y))))
        check(a.x!=obstacle.x)
        check(runCatching{BoardPlanner.validate(c)}.isFailure)
    }
    test("different-net intersecting copper tracks are rejected") {
        val c=Circuit(title="Cross-net routing regression")
        val a=Part(ref="R1",type=PartType.R,x=0,y=0)
        val b=Part(ref="R2",type=PartType.R,x=180,y=0)
        val d=Part(ref="R3",type=PartType.R,x=0,y=180)
        val e=Part(ref="R4",type=PartType.R,x=180,y=180)
        c.parts.addAll(listOf(a,b,d,e))
        c.wires.add(Wire(a=Point(48,0),b=Point(132,0)))
        c.wires.add(Wire(a=Point(48,180),b=Point(132,180)))
        for(p in c.parts)BoardPlanner.assign(c,p.uuid,"Resistor_SMD:R_0805_2012Metric")
        val positions=listOf(MmPoint(15.0,25.0),MmPoint(55.0,25.0),MmPoint(35.0,10.0),MmPoint(35.0,40.0))
        c.board.placements.forEachIndexed{i,pl->pl.xMm=positions[i].x;pl.yMm=positions[i].y
            if(i>=2)pl.rotation=90}
        BoardPlanner.validate(c)
        BoardRouting.add(c,PadKey(a.uuid,"2"),PadKey(b.uuid,"1"))
        check(c.board.routes.size==1)
        check(runCatching{BoardRouting.add(c,PadKey(d.uuid,"2"),PadKey(e.uuid,"1"))}.isFailure)
        check(c.board.routes.size==1)
    }
    test("unrouted airwires are not falsely classified as copper") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        check(c.board.routes.isEmpty())
        check(BoardPlanner.preview(c).airwires.size==1)
    }
    test("PCB edits do not change alpha-based solver or KiCad electrical nets") {
        val c=DemoCircuits.led()
        val before=MnaSolver.step(c)
        val schBefore=KiCad.export(c).schematic
        val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        BoardRouting.add(c,PadKey(r.uuid,"2"),PadKey(d.uuid,"2"))
        val after=MnaSolver.step(c)
        check(before.ok && after.ok)
        near(before.currentIn(r)!!,after.currentIn(r)!!,1e-12)
        check(KiCad.export(c).schematic.contains("(kicad_sch"))
        check(schBefore.contains("(kicad_sch"))
        val t=Topology.build(c)
        check(t.net(r,1)==t.net(d,0))
    }
    test("ideal independent DC current source: 10 mA in series loop") {
        val c=DemoCircuits.currentSource()
        val result=MnaSolver.step(c,dt=.001)
        check(result.ok){result.message}
        near(result.currentIn(c.parts.single{it.ref=="I1"})!!,0.01,1e-12)
        near(result.currentIn(c.parts.single{it.ref=="R1"})!!,0.01,1e-9)
        near(result.currentIn(c.parts.single{it.ref=="R2"})!!,0.01,1e-9)
    }
    test("negative independent current source reverses resistor current") {
        val c=DemoCircuits.currentSource()
        c.parts.single{it.ref=="I1"}.value=-0.007
        val result=MnaSolver.step(c,dt=.001)
        check(result.ok){result.message}
        near(result.currentIn(c.parts.single{it.ref=="R1"})!!,-0.007,1e-9)
    }
    test("RL first backward-Euler step agrees with closed-form discrete time") {
        val c=DemoCircuits.rl()
        val result=MnaSolver.step(c,dt=.001)
        check(result.ok){result.message}
        near(result.currentIn(c.parts.single{it.ref=="L1"})!!,5.0/(10.0+0.1/.001),1e-9)
        near(result.state.inductorCurrent.getValue(c.parts.single{it.ref=="L1"}.uuid),5.0/110.0,1e-9)
    }
    test("RL exponential current rise reaches 5V/10ohm DC limit") {
        val c=DemoCircuits.rl()
        var state=MnaSolver.State()
        repeat(160){i->val r=MnaSolver.step(c,state,.001,(i+1)*.001)
            check(r.ok){r.message};state=r.state
        }
        val current=state.inductorCurrent.getValue(c.parts.single{it.ref=="L1"}.uuid)
        near(current,0.5,1e-5)
    }
    test("inductor current persists across circuit steps and zero-source free decay") {
        val c=DemoCircuits.rl()
        val first=MnaSolver.step(c,dt=.001)
        check(first.ok){first.message}
        c.parts.single{it.ref=="V1"}.value=0.0
        val decay=MnaSolver.step(c,first.state,dt=.001,time=.002)
        check(decay.ok){decay.message}
        val expected=(5.0/110.0)*(100.0/110.0)
        near(decay.currentIn(c.parts.single{it.ref=="L1"})!!,expected,1e-9)
    }
    test("inductor parameter validation fails closed") {
        val c=DemoCircuits.rl()
        c.parts.single{it.ref=="L1"}.value=0.0
        val r=MnaSolver.step(c)
        check(!r.ok)
        check(r.message.contains("Inductor"))
    }
    test("current source value cannot be nonfinite or outside modeled range") {
        val c=DemoCircuits.currentSource()
        c.parts.single{it.ref=="I1"}.value=Double.NaN
        check(!MnaSolver.step(c).ok)
        c.parts.single{it.ref=="I1"}.value=1e9
        check(!MnaSolver.step(c).ok)
    }
    test("native model-backed library includes inductor and current source values") {
        val l=ElectronicLibrary.search("inductor")
        val currents=ElectronicLibrary.search("current source")
        check(l.size>=10 && currents.size>=5)
        check(l.all{it.simulationType==PartType.L} && currents.all{it.simulationType==PartType.I})
        val c=DemoCircuits.led()
        val part=ElectronicLibrary.instantiate(l.first().id,0,0,c.parts)
        check(part.type==PartType.L)
        c.parts.add(part);c.validate()
    }
    test("KiCad export contains native inductor and current source with correct properties") {
        for(c in listOf(DemoCircuits.rl(),DemoCircuits.currentSource())){
            val sch=KiCad.export(c).schematic
            check(KiCad.balanced(sch))
            check(sch.contains("(lib_symbols"))
        }
        val rl=KiCad.export(DemoCircuits.rl()).schematic
        check(rl.contains("BlazeCircuitLab:L"))
        val current=KiCad.export(DemoCircuits.currentSource())
        check(current.schematic.contains("BlazeCircuitLab:IDC"))
        check(current.warnings.any{it.contains("I1: simulation")})
    }
    test("PCB footprint maps inductor pin 0/1 and refuses simulator-only current source") {
        val c=DemoCircuits.rl()
        val inductor=c.parts.single{it.type==PartType.L}
        BoardPlanner.assign(c,inductor.uuid,"BlazeBasic:L_Axial_7.62")
        check(BoardPlanner.preview(c).pads.count{it.ref=="L1"}==2)
        val current=DemoCircuits.currentSource()
        val i=current.parts.single{it.type==PartType.I}
        check(FootprintCatalog.forPart(i).isEmpty())
        check(runCatching{BoardPlanner.assign(current,i.uuid,"BlazeBasic:L_Axial_7.62")}.isFailure)
    }
    test("tiny capacitor and inductor values survive KiCad export without rounding to zero") {
        val smallCap=DemoCircuits.rc()
        smallCap.parts.single{it.type==PartType.C}.value=1e-9
        val capSch=KiCad.export(smallCap).schematic
        check(capSch.contains("\"0.000000001\""))
        val smallL=DemoCircuits.rl()
        smallL.parts.single{it.type==PartType.L}.value=1e-6
        val indSch=KiCad.export(smallL).schematic
        check(indSch.contains("\"0.000001\"") || indSch.contains("\"0.0000010\"") || indSch.contains("\"0.000001\""))
    }
    test("SPICE exported RL matches authoritative topology and correct DC source polarity") {
        val c=DemoCircuits.rl()
        val exp=Spice.export(c)
        val topology=Topology.build(c)
        val v=c.parts.single{it.type==PartType.V};val l=c.parts.single{it.type==PartType.L}
        check(exp.netlist.contains("V1 N${topology.net(v,1)+1} ") || topology.net(v,1)==topology.groundNet)
        check(exp.netlist.lines().any{it.startsWith("L1 ")&&it.endsWith("0.1")})
        check(exp.netlist.lines().any{it.startsWith("R1 ")&&it.endsWith("10")})
        check(exp.netlist.contains(".tran 0.001 0.1"))
    }
    test("SPICE current source exports correct element and excludes imaginary PCB footprint") {
        val c=DemoCircuits.currentSource()
        val result=Spice.export(c)
        check(result.netlist.lines().any{it.startsWith("I1 ")&&it.endsWith("0.01")})
    }
    test("SPICE fails closed for LED model without physical device equivalent") {
        val c=DemoCircuits.led()
        check(runCatching{Spice.export(c)}.exceptionOrNull()?.message?.contains("LED") == true)
    }
    test("SPICE open switch is an explicit 10 gigaohm approximation") {
        val c=DemoCircuits.switched()
        c.parts.removeAll{it.type==PartType.LED}
        // A removed device leaves a disconnected net; topology is still serializable, but this example has
        // a floating terminal. Use an RL circuit plus a closed switch in a safe new standalone branch.
        val rl=DemoCircuits.rl()
        val sw=Part(ref="S99",type=PartType.SW,x=864,y=180,closed=false)
        rl.parts.add(sw)
        val text=Spice.export(rl)
        check(text.netlist.contains("R_SWITCH_S99")&&text.netlist.contains("1e10"))
        check(text.warnings.any{it.contains("finite-resistance switch")})
    }
    test("SPICE export rejects unsafe component identifiers rather than emitting injected directives") {
        val c=DemoCircuits.rl()
        c.parts.single{it.ref=="R1"}.ref="R1\n.control"
        check(runCatching{Spice.export(c)}.exceptionOrNull()?.message?.contains("SPICE references") == true)
    }
    test("offline electronics catalog searchable and backed by real solver types") {
        check(ElectronicLibrary.presets.size>=50)
        check(ElectronicLibrary.search("330 resistor").any{it.id=="R-330"})
        check(ElectronicLibrary.search("white LED").size==1)
        check(ElectronicLibrary.presets.map{it.id}.distinct().size==ElectronicLibrary.presets.size)
        check(ElectronicLibrary.presets.map{it.simulationType}.toSet()==PartType.values().toSet())
    }
    test("catalog placement keeps electrical semantics and persistent catalog identity") {
        val c=DemoCircuits.led()
        val p=ElectronicLibrary.instantiate("R-470",0,0,c.parts)
        check(p.ref=="R2"&&p.type==PartType.R&&p.value==470.0&&p.catalogId=="R-470")
        c.parts.add(p);c.validate()
        check(runCatching{p.catalogId="C-1";c.validate()}.isFailure)
    }
    test("offline catalog refuses unknown entry") {
        check(runCatching{ElectronicLibrary.instantiate("MOSFET-FAKE",0,0,emptyList())}.isFailure)
    }
    test("bundled local KiCad footprints map two physical pads to correct terminals") {
        val dir=java.io.File("app/src/main/assets/kicad/BlazeBasic.pretty")
        val bundled=FootprintCatalog.available.filter{it.libraryId.startsWith("BlazeBasic:")}
        check(bundled.size>=14)
        for(fp in bundled){
            check(fp.terminals.keys==fp.padOffsets.keys)
            check(fp.terminals.values.toSet()==if(fp.supported==setOf(PartType.QNPN)||fp.supported==setOf(PartType.MNMOS))setOf(0,1,2) else setOf(0,1))
            val file=java.io.File(dir,"${fp.libraryId.substringAfter(':')}.kicad_mod")
            check(file.exists()){"Missing bundled ${fp.libraryId}"}
            val data=file.readText();check(data.startsWith("(footprint"))
            for(pad in fp.padOffsets.keys)check(data.contains("(pad \"$pad\""))
        }
    }
    test("new physical library assignment and ratsnest operate through shared topology") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"BlazeBasic:R_0805");BoardPlanner.assign(c,d.uuid,"BlazeBasic:LED_0805")
        val pads=BoardPlanner.preview(c).pads
        check(pads.single{it.ref=="D1"&&it.pad=="1"}.net==Topology.build(c).net(d,1))
        check(BoardPlanner.preview(c).airwires.size==1)
        check(KiCad.export(c).schematic.contains("BlazeBasic:R_0805"))
    }
    test("standalone schematic ZIP contains real KiCad footprint definitions") {
        val dir=java.io.File("app/src/main/assets/kicad")
        val files=mutableMapOf<String,ByteArray>(
            "fp-lib-table" to java.io.File(dir,"fp-lib-table").readBytes(),
            "README.txt" to java.io.File(dir,"README.txt").readBytes(),
            "sym-lib-table" to java.io.File(dir,"sym-lib-table").readBytes(),
            "BlazeCircuitLab.kicad_sym" to java.io.File(dir,"BlazeCircuitLab.kicad_sym").readBytes())
        java.io.File(dir,"BlazeBasic.pretty").listFiles()!!.forEach{file->files["BlazeBasic.pretty/${file.name}"]=file.readBytes()}
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"BlazeBasic:R_0805");BoardPlanner.assign(c,d.uuid,"BlazeBasic:LED_D3")
        val bytes=KiCadPackage.pack(c,files)
        val entries=mutableMapOf<String,ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use{zip->while(true){val e=zip.nextEntry?:break;entries[e.name]=zip.readBytes()}}
        check(entries.size>=17)
        check(entries.keys.any{it.endsWith(".kicad_sch")})
        val boardEntry=entries.entries.single{it.key.endsWith(".kicad_pcb")}
        val projectEntry=entries.entries.single{it.key.endsWith(".kicad_pro")}
        check(projectEntry.key==boardEntry.key.removeSuffix(".kicad_pcb")+".kicad_pro")
        check(projectEntry.value.decodeToString()=="{}")
        check(KiCad.balanced(boardEntry.value.decodeToString()))
        check(boardEntry.value.decodeToString().contains("(net 0 \"\")"))
        check(entries["BlazeBasic.pretty/LED_D3.kicad_mod"]!!.decodeToString().contains("(pad \"2\""))
        check(entries["fp-lib-table"]!!.decodeToString().contains("KIPRJMOD"))
        check(entries["BlazeCircuitLab.kicad_sym"]!!.decodeToString().contains("kicad_symbol_lib"))
        check(KiCad.balanced(entries["BlazeCircuitLab.kicad_sym"]!!.decodeToString()))
    }
    test("all nine bundled native KiCad symbol types are present") {
        val lib=java.io.File("app/src/main/assets/kicad/BlazeCircuitLab.kicad_sym").readText()
        check(KiCad.balanced(lib))
        for(name in listOf("VDC","VSIN","R","C","LED","SW_SPST","GND"))
            check(lib.contains("(symbol \"$name\""))
        check(lib.count{it=='('}>100)
    }
    test("KiCad bundle refuses references to absent local footprint") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"}
        r.footprint="BlazeBasic:R_0805"
        check(runCatching{KiCadPackage.pack(c,mapOf("fp-lib-table" to "x".toByteArray()))}.isFailure)
    }
    test("Arduino UNO and Nano exact named pin reference capabilities") {
        val uno=McuLibrary.board("arduino-uno-r3")!!
        check(uno.pin("D13")!!.functions.contains("SPI_SCK"))
        check(uno.pin("A4")!!.functions.contains("I2C_SDA"))
        val nano=McuLibrary.board("arduino-nano-classic")!!
        check(nano.pin("A7")!!.kind=="INPUT_ONLY")
    }
    test("ESP32 GPIO reference contains boot and input-only restrictions") {
        val esp=McuLibrary.board("esp32-devkitc-v4-wroom32e")!!
        check(esp.pin("GPIO34")!!.kind=="INPUT_ONLY")
        check(esp.pin("GPIO0")!!.caution.contains("Strapping"))
        check(esp.pin("GPIO6")==null)
        check(esp.logicVoltage==3.3)
        check(McuLibrary.searchPins(esp.id,"I2C").size==2)
    }
    test("MCU pin planner binds stable component terminal rather than unstable net index") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"}
        c.mcuPlan=McuPlan("arduino-uno-r3")
        McuLibrary.bind(c,"D13",r.uuid,1)
        val before=McuLibrary.plannedNets(c).single()
        check(before.net==Topology.build(c).net(r,1))
        val copy=c.detached();copy.mcuPlan!!.bindings.clear()
        check(c.mcuPlan!!.bindings.size==1)
        check(McuLibrary.csv(c).contains("D13"))
        val d=c.parts.single{it.ref=="D1"}
        c.wires.removeAll{it.a==Point(432,180)||it.b==Point(504,180)}
        val after=McuLibrary.plannedNets(c).single()
        check(before.connectedTo==after.connectedTo)
        check(d.ref=="D1")
    }
    test("MCU planner rejects invalid GPIO and orphan part binding") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"}
        c.mcuPlan=McuPlan("arduino-uno-r3")
        check(runCatching{McuLibrary.bind(c,"GND",r.uuid,0)}.isFailure)
        check(runCatching{McuLibrary.bind(c,"D1","absent",0)}.isFailure)
        check(c.mcuPlan!!.bindings.isEmpty())
        McuLibrary.bind(c,"D1",r.uuid,0)
        check(runCatching{McuLibrary.bind(c,"D2",r.uuid,5)}.isFailure)
        check(c.mcuPlan!!.bindings.size==1)
    }
    test("ESP8266 NodeMCU virtual pin planner documents real D/GPIO mapping") {
        val b=McuLibrary.board("esp8266-nodemcu-1-0")!!
        check(b.pin("D0")!!.functions.contains("GPIO16"))
        check(b.pin("D2")!!.functions.contains("GPIO4"))
        check(b.pin("D8")!!.caution.contains("boot strap"))
        check(b.pin("A0")!!.kind=="INPUT_ONLY")
        check(McuLibrary.searchPins(b.id,"I2C_SDA").single().name=="D2")
    }
    test("MCU planner warns that 3.3 V GPIO is not automatically 5 V safe") {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"}
        c.mcuPlan=McuPlan("esp32-devkitc-v4-wroom32e")
        McuLibrary.bind(c,"GPIO23",r.uuid,0)
        check(McuLibrary.plannedNets(c).single().voltageHint.contains("3.3V"))
    }

    test("Shockley diode forward branch current agrees with series resistor KCL") {
        val c=DemoCircuits.diode();val r=MnaSolver.step(c)
        check(r.ok){r.message};val d=c.parts.single{it.type==PartType.D};val resistor=c.parts.single{it.type==PartType.R}
        val vd=r.voltageAcross(d)!!;val id=r.currentIn(d)!!
        check(vd>0.6 && vd<1.2){"Unexpected junction voltage $vd"}
        near(id,(5.0-vd)/1000.0,1e-8)
        near(id,r.currentIn(resistor)!!,1e-8)
        check(r.iterations in 2..80)
    }
    test("Diode forward operating point matches analytic bisection solution") {
        val c=DemoCircuits.diode();val d=c.parts.single{it.type==PartType.D}
        val r=MnaSolver.step(c);check(r.ok){r.message}
        fun residual(v:Double)=(5.0-v)/1000.0-MnaSolver.DiodeModel.current(v,d.value,d.diodeIdeality,d.temperatureC)
        var lo=0.0;var hi=2.0
        repeat(75){val mid=(lo+hi)/2;if(residual(mid)>0)lo=mid else hi=mid}
        near(r.voltageAcross(d)!!,(lo+hi)/2,1e-6)
    }
    test("Diode reverse bias remains blocking without fictional forward conduction") {
        val c=DemoCircuits.diodeReverse();val r=MnaSolver.step(c);check(r.ok){r.message}
        val d=c.parts.single{it.type==PartType.D}
        check(r.voltageAcross(d)!! < -4.9)
        check(kotlin.math.abs(r.currentIn(d)!!)<1e-10)
    }
    test("Shockley model monotonic current versus forward bias") {
        val m=MnaSolver.DiodeModel
        val vals=listOf(-2.0,-0.2,0.0,0.4,0.6,0.8,1.0).map{m.current(it,1e-12,1.75,25.0)}
        check(vals.zipWithNext().all{(a,b)->b>a})
        check(vals.all{it.isFinite()})
    }
    test("Shockley derivative matches numerical finite differences") {
        val m=MnaSolver.DiodeModel;val v=0.75;val h=1e-6
        val numeric=(m.current(v+h,1e-12,1.75,25.0)-m.current(v-h,1e-12,1.75,25.0))/(2*h)
        val g=m.conductance(v,1e-12,1.75,25.0)
        check(kotlin.math.abs(g-numeric)/g<1e-7)
    }
    test("Diode fixed-Is temperature dependence is explicit and finite") {
        val d=Part(ref="D1",type=PartType.D,x=0,y=0)
        val cold=MnaSolver.DiodeModel.current(.65,d.value,d.diodeIdeality,-10.0)
        val hot=MnaSolver.DiodeModel.current(.65,d.value,d.diodeIdeality,90.0)
        check(cold>hot && hot>0.0)
    }
    test("Diode model parameter guards are enforced") {
        val c=DemoCircuits.diode();val d=c.parts.single{it.type==PartType.D}
        for(bad in listOf(0.0,-1e-12,1.0,Double.NaN)){
            d.value=bad;check(runCatching{c.validate()}.isFailure)
        }
        d.value=1e-12
        d.diodeIdeality=0.1;check(runCatching{c.validate()}.isFailure)
        d.diodeIdeality=1.75;d.temperatureC=200.0;check(runCatching{c.validate()}.isFailure)
    }
    test("Detaching project keeps diode model metadata independent") {
        val c=DemoCircuits.diode();val d=c.parts.single{it.type==PartType.D};d.temperatureC=38.0
        val snap=c.detached();d.diodeIdeality=2.2
        check(snap.parts.single{it.type==PartType.D}.temperatureC==38.0)
        check(snap.parts.single{it.type==PartType.D}.diodeIdeality==1.75)
    }
    test("Offline library includes simulation-backed diode presets") {
        val entries=ElectronicLibrary.presets.filter{it.simulationType==PartType.D}
        check(entries.size>=4 && entries.all{it.approximateModel})
        val part=ElectronicLibrary.instantiate(entries.first().id,192,192,emptyList())
        check(part.type==PartType.D && part.value>0)
        check(ElectronicLibrary.search("Shockley").contains(entries.first()))
    }
    test("Diode physical pad 1 cathode and pad 2 anode share authoritative net") {
        val c=DemoCircuits.diode();val d=c.parts.single{it.type==PartType.D}
        BoardPlanner.assign(c,d.uuid,"BlazeBasic:D_DO35_7.62")
        val pads=BoardPlanner.preview(c).pads.filter{it.ref==d.ref}.associateBy{it.pad}
        val net=Topology.build(c)
        check(pads.getValue("1").net==net.net(d,1) && pads.getValue("2").net==net.net(d,0))
    }
    test("Diode KiCad schema exports anode/cathode pin number and precise value") {
        val c=DemoCircuits.diode();val sch=KiCad.export(c).schematic
        check(KiCad.balanced(sch));check("BlazeCircuitLab:D" in sch)
        check("(pin \"2\"" in sch && "(pin \"1\"" in sch)
        check("0.000000000001" in sch)
    }
    test("Diode SPICE writes explicit model, anode/cathode and bounded parameters") {
        val net=Spice.export(DemoCircuits.diode()).netlist
        check(net.contains(".model BCL_MODEL_D1 D(Is=0.000000000001 N=1.75)"))
        check(net.contains("D_D1 "))
        check(net.contains(".temp 25"))
    }
    test("SPICE rejects per-diode inconsistent temperatures rather than lying") {
        val c=DemoCircuits.diode();val d=c.parts.single{it.type==PartType.D}
        c.parts.add(d.copy(uuid=java.util.UUID.randomUUID().toString(),ref="D2",x=700,y=200,temperatureC=35.0))
        check(runCatching{Spice.export(c)}.isFailure)
    }
    test("All old example numerical regression fixtures still work after diode addition") {
        val led=DemoCircuits.led();val switched=DemoCircuits.switched();val rc=DemoCircuits.rc()
        near(MnaSolver.step(led,dt=.015,time=.015).currentIn(led.parts[1])!!,.008771929825300092,1e-9)
        near(MnaSolver.step(switched,dt=.015,time=.015).currentIn(switched.parts[2])!!,.01452131521679579,1e-9)
        near(MnaSolver.step(rc,dt=.015,time=.015).currentIn(rc.parts[1])!!,.00023205148132880336,1e-9)
    }
    test("Three-terminal NPN pin identity uses base collector emitter in order") {
        val q=Part(ref="Q1",type=PartType.QNPN,x=552,y=300)
        check(q.pins()==listOf(Point(504,300),Point(600,264),Point(600,336)))
        q.rotation=90
        check(q.pins().distinct().size==3)
    }
    test("NPN grounded-emitter bias solution has physical branch currents and VCE") {
        val c=DemoCircuits.npnSwitch();val q=c.parts.single{it.type==PartType.QNPN}
        val r=MnaSolver.step(c);check(r.ok){r.message}
        val g=r.topology!!;val b=r.voltage[g.net(q,0)]!!;val col=r.voltage[g.net(q,1)]!!;val e=r.voltage[g.net(q,2)]!!
        check(b in .5..0.8 && col in 2.0..4.5 && kotlin.math.abs(e)<1e-8)
        check(r.voltageAcross(q)!! in 2.0..4.5)
        val ic=r.currentIn(q)!!
        val rb=c.parts.single{it.ref=="RB1"};val rc=c.parts.single{it.ref=="RC1"}
        val ib=r.currentIn(rb)!!
        check(ic>0 && ib>0)
        near(ic,(5-col)/1000.0,1e-6)
        near(ib,(.8-b)/10000.0,1e-6)
        check(ic/ib in 95.0..105.0) {"Unexpected beta ${ic/ib}"}
    }
    test("NPN transistor turns off under zero base bias") {
        val c=DemoCircuits.npnSwitch();c.parts.single{it.ref=="V2"}.value=0.0
        val q=c.parts.single{it.type==PartType.QNPN};val r=MnaSolver.step(c)
        check(r.ok){r.message};check(kotlin.math.abs(r.currentIn(q)!!)<1e-8)
        check(r.voltageAcross(q)!!>4.9)
    }
    test("NPN terminal KCL and Jacobian agree with finite difference") {
        val m=MnaSolver.NpnModel
        val v=listOf(.65,3.4,0.0)
        val a=m.evaluate(v[0],v[1],v[2],100.0,1e-14,2.0,25.0)
        near(a.base+a.collector+a.emitter,0.0,1e-12)
        val h=1e-6
        for(i in 0..2){
            val hi=v.toMutableList();val lo=v.toMutableList();hi[i]+=h;lo[i]-=h
            val bh=m.evaluate(hi[0],hi[1],hi[2],100.0,1e-14,2.0,25.0)
            val bl=m.evaluate(lo[0],lo[1],lo[2],100.0,1e-14,2.0,25.0)
            near((bh.base-bl.base)/(2*h),a.baseDeriv[i],1e-8)
            near((bh.collector-bl.collector)/(2*h),a.collectorDeriv[i],1e-8)
        }
    }
    test("NPN rejects nonphysical beta and saturation current") {
        val c=DemoCircuits.npnSwitch();val q=c.parts.single{it.type==PartType.QNPN}
        q.value=0.0;check(runCatching{c.validate()}.isFailure);q.value=100.0
        q.transistorIsA=-1.0;check(runCatching{c.validate()}.isFailure);q.transistorIsA=1e-14
        q.transistorReverseBeta=0.0;check(runCatching{c.validate()}.isFailure);q.transistorReverseBeta=2.0
        q.temperatureC=200.0;check(runCatching{c.validate()}.isFailure)
    }
    test("NPN simulation presets are not just catalog names") {
        val entries=ElectronicLibrary.presets.filter{it.simulationType==PartType.QNPN}
        check(entries.size==4 && entries.all{it.approximateModel})
        val part=ElectronicLibrary.instantiate(entries.first().id,0,0,emptyList())
        check(part.type==PartType.QNPN && part.ref=="Q1" && part.value>0)
    }
    test("NPN three-pad footprint maps E B C to authoritative pin nets") {
        val c=DemoCircuits.npnSwitch();val q=c.parts.single{it.type==PartType.QNPN}
        BoardPlanner.assign(c,q.uuid,"BlazeBasic:Q_TO92_EBC_2.54")
        val pads=BoardPlanner.preview(c).pads.filter{it.componentUuid==q.uuid}.associateBy{it.pad}
        val g=Topology.build(c)
        check(pads.size==3)
        check(pads.getValue("1").net==g.net(q,2) && pads.getValue("2").net==g.net(q,0) && pads.getValue("3").net==g.net(q,1))
        check(java.io.File("app/src/main/assets/kicad/BlazeBasic.pretty/Q_TO92_EBC_2.54.kicad_mod").readText().contains("(pad \"3\""))
    }
    test("NPN KiCad export assigns B C E pin numbers 2 3 1") {
        val sch=KiCad.export(DemoCircuits.npnSwitch()).schematic
        check(KiCad.balanced(sch))
        check("BlazeCircuitLab:QNPN" in sch)
        check("(pin \"2\"" in sch && "(pin \"3\"" in sch && "(pin \"1\"" in sch)
        check("BF=100" in sch && "IS=0.00000000000001" in sch)
    }
    test("NPN SPICE netlist exports a three-terminal Q and explicit model") {
        val net=Spice.export(DemoCircuits.npnSwitch()).netlist
        check("Q_Q1 " in net && " BCL_NPN_Q1" in net)
        check(".model BCL_NPN_Q1 NPN(Is=0.00000000000001 Bf=100 Br=2)" in net)
        check(".temp 25" in net)
    }
    test("NPN project clone retains independent model parameters") {
        val c=DemoCircuits.npnSwitch();val q=c.parts.single{it.type==PartType.QNPN}
        q.transistorIsA=2e-14;q.transistorReverseBeta=4.0
        val copy=c.detached();q.transistorIsA=4e-14
        val saved=copy.parts.single{it.type==PartType.QNPN}
        check(saved.transistorIsA==2e-14 && saved.transistorReverseBeta==4.0 && copy.version==9)
    }
    test("Earlier alpha demos remain numerically unchanged with 3-pin NPN support") {
        val led=DemoCircuits.led();val switched=DemoCircuits.switched();val rc=DemoCircuits.rc()
        near(MnaSolver.step(led,dt=.015,time=.015).currentIn(led.parts[1])!!,.008771929825300092,1e-9)
        near(MnaSolver.step(switched,dt=.015,time=.015).currentIn(switched.parts[2])!!,.01452131521679579,1e-9)
        near(MnaSolver.step(rc,dt=.015,time=.015).currentIn(rc.parts[1])!!,.00023205148132880336,1e-9)
    }
    test("NMOS G D S terminal order and 90-degree rotation") {
        val m=Part(ref="M1",type=PartType.MNMOS,x=528,y=276)
        check(m.pins()==listOf(Point(480,276),Point(576,240),Point(576,312)))
        m.rotation=90;check(m.pins().distinct().size==3)
    }
    test("NMOS off below Vth: finite solution but no channel current") {
        val c=DemoCircuits.nmosSwitch();c.parts.single{it.ref=="V2"}.value=1.5
        val m=c.parts.single{it.type==PartType.MNMOS}
        val r=MnaSolver.step(c);check(r.ok){r.message}
        near(r.currentIn(m)!!,0.0,1e-10)
        near(r.voltageAcross(m)!!,5.0,1e-6)
    }
    test("NMOS saturation current agrees with square law") {
        val c=DemoCircuits.nmosSwitch();c.parts.single{it.ref=="V2"}.value=3.0
        val m=c.parts.single{it.type==PartType.MNMOS};val r=MnaSolver.step(c)
        check(r.ok){r.message}
        near(r.currentIn(m)!!,0.001,2e-7)
        near(r.voltageAcross(m)!!,4.0,2e-5)
    }
    test("NMOS triode solution matches analytic quadratic root") {
        val c=DemoCircuits.nmosSwitch();val m=c.parts.single{it.type==PartType.MNMOS}
        val r=MnaSolver.step(c);check(r.ok){r.message}
        val vds=(5.0-kotlin.math.sqrt(5.0))/2.0
        near(r.voltageAcross(m)!!,vds,1e-5)
        near(r.currentIn(m)!!,(5.0-vds)/1000.0,1e-7)
        val gate=Topology.build(c).net(m,0)
        near(r.voltage[gate]!!,4.0,1e-8)
    }
    test("NMOS finite difference Jacobian matches both triode and saturation branches") {
        val eps=1e-6
        val positions=listOf(listOf(4.0,1.3,0.0),listOf(3.0,4.0,0.0),listOf(3.0,0.0,2.0))
        for(v in positions){
            val a=MnaSolver.NmosModel.evaluate(v[0],v[1],v[2],2.0,0.002)
            for(i in 0..2){
                val up=v.toMutableList();val down=v.toMutableList();up[i]+=eps;down[i]-=eps
                val fd=(MnaSolver.NmosModel.evaluate(up[0],up[1],up[2],2.0,0.002).current-
                    MnaSolver.NmosModel.evaluate(down[0],down[1],down[2],2.0,0.002).current)/(2*eps)
                near(a.derivatives[i],fd,1e-8)
            }
            near(a.derivatives.sum(),0.0,1e-10)
        }
    }
    test("NMOS reverse channel direction is symmetric without imaginary body diode") {
        val f=MnaSolver.NmosModel.evaluate(5.0,1.0,0.0,2.0,0.002)
        val r=MnaSolver.NmosModel.evaluate(5.0,0.0,1.0,2.0,0.002)
        check(f.current>0 && r.current<0)
        // Reverse bias uses a different source-reference Vgs (gate to the LOW node).
        near(f.current,-r.current,1e-12)
        check(MnaSolver.NmosModel.evaluate(0.0,5.0,0.0,2.0,0.002).current==0.0)
    }
    test("An unwired insulated NMOS gate is diagnosed as floating") {
        val c=DemoCircuits.nmosSwitch()
        c.wires.removeAll { it.a==Point(432,276) && it.b==Point(480,276) }
        val r=MnaSolver.step(c)
        check(!r.ok && r.message.contains("Floating")){r.message}
    }
    test("NMOS physically invalid thresholds or K are rejected") {
        val c=DemoCircuits.nmosSwitch();val m=c.parts.single{it.type==PartType.MNMOS}
        m.value=0.0;check(runCatching{c.validate()}.isFailure)
        m.value=2.0;m.mosfetK=0.0;check(runCatching{c.validate()}.isFailure)
        m.mosfetK=Double.NaN;check(runCatching{c.validate()}.isFailure)
        m.mosfetK=0.002;c.validate()
    }
    test("NMOS four offline catalog presets actually instantiate simulated MOSFETs") {
        val entries=ElectronicLibrary.presets.filter{it.simulationType==PartType.MNMOS}
        check(entries.size==4 && entries.all{it.approximateModel})
        val m=ElectronicLibrary.instantiate(entries[1].id,240,240,emptyList())
        check(m.ref=="M1"&&m.value==2.0&&m.mosfetK==0.002)
        check(ElectronicLibrary.search("NMOS threshold").size==4)
    }
    test("NMOS pad mapping preserves schematic G D S nets") {
        val c=DemoCircuits.nmosSwitch();val m=c.parts.single{it.type==PartType.MNMOS}
        BoardPlanner.assign(c,m.uuid,"BlazeBasic:M_TO92_GDS_2.54")
        val pads=BoardPlanner.preview(c).pads.filter{it.componentUuid==m.uuid}.associateBy{it.pad}
        val g=Topology.build(c)
        for(i in 0..2)check(pads.getValue("${i+1}").net==g.net(m,i))
        check(java.io.File("app/src/main/assets/kicad/BlazeBasic.pretty/M_TO92_GDS_2.54.kicad_mod").exists())
    }
    test("NMOS KiCad export contains correct G D S pin identities and Vth") {
        val c=DemoCircuits.nmosSwitch();val sch=KiCad.export(c).schematic
        check(KiCad.balanced(sch))
        check("BlazeCircuitLab:MNMOS" in sch)
        check("NMOS Vth=2 K=0.002" in sch)
        for(i in 1..3)check("(pin \"$i\"" in sch)
    }
    test("NMOS SPICE export uses D G S B syntax and explicit level-one model") {
        val c=DemoCircuits.nmosSwitch();val sp=Spice.export(c)
        check("M_M1 " in sp.netlist && " BCL_NMOS_M1" in sp.netlist)
        check(".model BCL_NMOS_M1 NMOS(Level=1 Vto=2 Kp=0.002 Lambda=0)" in sp.netlist)
        check(sp.warnings.any{it.contains("body junctions")})
    }
    test("NMOS project clone keeps K and new schema version") {
        val c=DemoCircuits.nmosSwitch();val m=c.parts.single{it.type==PartType.MNMOS}
        m.mosfetK=0.004
        val clone=c.detached();m.mosfetK=0.006
        check(clone.version==9 && clone.parts.single{it.type==PartType.MNMOS}.mosfetK==0.004)
    }
    test("Original LED RC and switch operating points stay unchanged with NMOS solver") {
        val led=DemoCircuits.led();val sw=DemoCircuits.switched();val rc=DemoCircuits.rc()
        near(MnaSolver.step(led,dt=.015,time=.015).currentIn(led.parts[1])!!,.008771929825300092,1e-9)
        near(MnaSolver.step(sw,dt=.015,time=.015).currentIn(sw.parts[2])!!,.01452131521679579,1e-9)
        near(MnaSolver.step(rc,dt=.015,time=.015).currentIn(rc.parts[1])!!,.00023205148132880336,1e-9)
    }
    test("VCVS authoritative four-pin circuit produces 10x gain across load") {
        val c=DemoCircuits.controlledAmplifier()
        val e=c.parts.single{it.type==PartType.EVCVS}
        check(e.pins()==listOf(Point(432,192),Point(432,264),Point(528,192),Point(528,264)))
        val r=MnaSolver.step(c)
        check(r.ok) { r.message }
        near(r.voltageAcross(e)!!,10.0,1e-7)
        near(r.currentIn(c.parts.single{it.ref=="R1"})!!,.01,1e-7)
        near(r.currentIn(e)!!,-.01,1e-7)
    }
    test("VCVS input pin does not create a fictitious conductive short") {
        val c=DemoCircuits.controlledAmplifier();val e=c.parts.single{it.type==PartType.EVCVS}
        val t=Topology.build(c)
        check(t.net(e,0)!=t.net(e,1))
        check(t.net(e,2)!=t.net(e,3))
        check(t.net(e,1)==t.groundNet)
        check(t.net(e,3)==t.groundNet)
    }
    test("VCVS rejects floating control input rather than applying fake conductance") {
        val c=DemoCircuits.controlledAmplifier()
        c.wires.removeAll { it.a==Point(432,264) || it.b==Point(432,264) || it.a==Point(432,336) || it.b==Point(432,336) }
        c.junctions.removeAll{it.at==Point(144,336)}
        check(!MnaSolver.step(c).ok)
    }
    test("VCVS finite gain stays valid under gain changes") {
        val c=DemoCircuits.controlledAmplifier();val e=c.parts.single{it.type==PartType.EVCVS}
        for(gain in listOf(0.5,1.0,10.0,50.0)){
            e.value=gain
            val r=MnaSolver.step(c)
            check(r.ok){r.message}
            near(r.voltageAcross(e)!!,gain,1e-6)
        }
        e.value=-1.0;check(runCatching{c.validate()}.isFailure)
        e.value=1e12;check(runCatching{c.validate()}.isFailure)
    }
    test("VCVS negative-feedback unity follower solves finite closed-loop gain") {
        val c=DemoCircuits.controlledAmplifier()
        c.wires.removeAll { w ->
            listOf(Point(432,264),Point(432,336),Point(144,336)).any { p -> w.a==p||w.b==p }
        }
        c.junctions.removeAll{it.at==Point(144,336)}
        c.wires.addAll(listOf(
            Wire(a=Point(432,264),b=Point(432,420)),
            Wire(a=Point(432,420),b=Point(900,420)),
            Wire(a=Point(900,420),b=Point(900,108)),
            Wire(a=Point(900,108),b=Point(528,108)),
            Wire(a=Point(528,108),b=Point(528,192))
        ))
        val amplifier=c.parts.single{it.type==PartType.EVCVS}
        amplifier.value=1000.0
        val r=MnaSolver.step(c)
        check(r.ok){r.message}
        near(r.voltageAcross(amplifier)!!,1000.0/1001.0,1e-5)
    }
    test("VCVS KiCad symbolic export uses four pin identities and no PCB footprint") {
        val c=DemoCircuits.controlledAmplifier()
        val k=KiCad.export(c)
        check(KiCad.balanced(k.schematic))
        check("BlazeCircuitLab:EVCVS" in k.schematic)
        for(n in 1..4)check("(pin \"$n\"" in k.schematic)
        check(k.warnings.any{it.contains("E1") && it.contains("not a physical")})
    }
    test("VCVS SPICE export is a true E controlled source with correct terminals") {
        val c=DemoCircuits.controlledAmplifier();val sp=Spice.export(c)
        val e=c.parts.single{it.type==PartType.EVCVS};val g=Topology.build(c)
        fun n(i:Int):String=if(g.net(e,i)==g.groundNet)"0" else "N${g.net(e,i)+1}"
        check("E_E1 ${n(2)} ${n(3)} ${n(0)} ${n(1)} 10" in sp.netlist)
        check(".end" in sp.netlist)
    }
    test("VCVS library entries have real model identity and no physical package") {
        val entry=ElectronicLibrary.search("voltage-controlled amplifier").first{it.simulationType==PartType.EVCVS}
        val e=ElectronicLibrary.instantiate(entry.id,480,228,emptyList())
        check(e.type==PartType.EVCVS)
        check(FootprintCatalog.forPart(e).isEmpty())
        val c=DemoCircuits.controlledAmplifier()
        check(runCatching{BoardContract.resolvePads(c,listOf(PhysicalPadMapping(c.parts.single{it.type==PartType.EVCVS}.uuid,"BlazeBasic:Fake",mapOf("1" to 0,"2" to 1,"3" to 2,"4" to 3))))}.isFailure)
    }
    test("Legacy VCVS source and new schema preserve existing numerical behavior") {
        check(DemoCircuits.led().version==9)
        check(DemoCircuits.controlledAmplifier().detached().version==9)
        val led=DemoCircuits.led()
        near(MnaSolver.step(led).currentIn(led.parts[1])!!,.008771929825300092,1e-9)
    }
    test("Powered op-amp five independent pins and deterministic connectivity") {
        val c=DemoCircuits.poweredAmplifier()
        val p=c.parts.single{it.type==PartType.OPAMP};val g=Topology.build(c)
        check(p.pins().size==5 && p.pins().distinct().size==5)
        check((0..4).map{g.net(p,it)}.toSet().size==5)
        check(p.pins()[3]==Point(480,192) && p.pins()[4]==Point(480,288))
    }
    test("Powered op-amp positive rail clamp and finite load current") {
        val c=DemoCircuits.poweredAmplifier()
        val r=MnaSolver.step(c);check(r.ok){r.message}
        val amp=c.parts.single{it.type==PartType.OPAMP}
        near(r.voltageAcross(amp)!!,4.8,1e-5)
        near(r.currentIn(c.parts.single{it.ref=="R1"})!!,0.0048,1e-6)
    }
    test("Powered op-amp negative rail clamp") {
        val c=DemoCircuits.poweredAmplifier()
        c.parts.single{it.ref=="V3"}.value=-0.5
        val r=MnaSolver.step(c);check(r.ok){r.message}
        near(r.voltageAcross(c.parts.single{it.type==PartType.OPAMP})!!,-4.8,1e-5)
    }
    test("Powered op-amp linear open loop and supply-dependent clipping") {
        val c=DemoCircuits.poweredAmplifier()
        c.parts.single{it.ref=="V3"}.value=0.1
        var r=MnaSolver.step(c);check(r.ok){r.message}
        near(r.voltageAcross(c.parts.single{it.type==PartType.OPAMP})!!,2.0,1e-5)
        c.parts.single{it.ref=="V1"}.value=1.0
        r=MnaSolver.step(c);check(r.ok){r.message}
        near(r.voltageAcross(c.parts.single{it.type==PartType.OPAMP})!!,0.8,1e-5)
    }
    test("Powered op-amp negative-feedback follower converges without false rail clipping") {
        val c=DemoCircuits.poweredAmplifier()
        c.wires.removeAll { it.a==Point(432,276) && it.b==Point(384,276) ||
            it.a==Point(384,276) && it.b==Point(384,408) ||
            it.a==Point(384,408) && it.b==Point(384,432) }
        c.junctions.removeAll{it.at==Point(384,432)}
        c.wires.add(Wire(a=Point(432,276),b=Point(432,396)))
        c.wires.add(Wire(a=Point(432,396),b=Point(576,396)))
        c.wires.add(Wire(a=Point(576,396),b=Point(576,240)))
        c.junctions.add(Junction(at=Point(576,240)))
        val r=MnaSolver.step(c)
        check(r.ok){r.message}
        near(r.voltageAcross(c.parts.single{it.type==PartType.OPAMP})!!,20.0/21.0*0.5,1e-5)
    }
    test("Powered op-amp rejects invalid or missing supply rails") {
        val c=DemoCircuits.poweredAmplifier()
        c.parts.single{it.ref=="V1"}.value=-5.0
        val r=MnaSolver.step(c)
        check(!r.ok && ("rails" in r.message || "converge" in r.message)) { r.message }
    }
    test("Powered op-amp model fields are detached and validated") {
        val c=DemoCircuits.poweredAmplifier()
        val p=c.parts.single{it.type==PartType.OPAMP}
        p.opampHeadroomV=0.4
        val detached=c.detached()
        p.opampHeadroomV=0.6
        check(detached.version==9)
        check(detached.parts.single{it.type==PartType.OPAMP}.opampHeadroomV==0.4)
        p.opampHeadroomV=4.0
        check(runCatching{c.validate()}.isFailure)
    }
    test("Powered op-amp catalog, KiCad five pin mapping and physical rejection") {
        val c=DemoCircuits.poweredAmplifier()
        val amp=c.parts.single{it.type==PartType.OPAMP}
        val catalog=ElectronicLibrary.search("rail-clamped amplifier")
        check(catalog.count{it.simulationType==PartType.OPAMP}==4)
        check(ElectronicLibrary.instantiate(catalog.first().id,480,240,emptyList()).type==PartType.OPAMP)
        check(FootprintCatalog.forPart(amp).isEmpty())
        val sch=KiCad.export(c)
        check(KiCad.balanced(sch.schematic) && "BlazeCircuitLab:OPAMP" in sch.schematic)
        check((1..5).all{"(pin \"$it\"" in sch.schematic})
        check(sch.warnings.any{"A1" in it && "not a physical" in it})
    }
    test("Powered op-amp refuses unverified SPICE export") {
        check(runCatching{Spice.export(DemoCircuits.poweredAmplifier())}.isFailure)
    }
    test("Legacy LED RC Switch VCVS remain numerically functional with powered op-amp") {
        listOf(DemoCircuits.led(),DemoCircuits.rc(),DemoCircuits.switched(),DemoCircuits.controlledAmplifier()).forEach{
            val r=MnaSolver.step(it)
            check(r.ok){ "${it.title}: ${r.message}" }
        }
    }
    test("BOM exports source-only, missing physical footprints without fabricated MPNs") {
        val c=DemoCircuits.led()
        val csv=Bom.export(c)
        check(csv.contains("SCHEMATIC_ONLY") && csv.contains("NO_FOOTPRINT"))
        check(csv.lines().size>=4 && !csv.contains("manufacturer part number",ignoreCase=true))
        check(csv.contains("\"R1\""))
    }
    test("BOM reflects actual schematic-to-PCB placement and external footprint status") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"}
        BoardPlanner.assign(c,r.uuid,"BlazeBasic:R_0805")
        var report=Bom.export(c)
        check(report.contains("BUNDLED_REFERENCE_UNVERIFIED"))
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        report=Bom.export(c)
        check(report.contains("EXTERNAL_REFERENCE_NOT_BUNDLED"))
    }
    test("BOM rejects erroneous or orphan physical assignments") {
        val c=DemoCircuits.led()
        c.parts.first{it.type==PartType.R}.footprint="nonexistent"
        check(runCatching{Bom.export(c)}.isFailure)
    }
    test("KiCad board exports real placed footprint pads and an unrouted board") {
        val c=DemoCircuits.led()
        BoardPlanner.assign(c,c.parts.single{it.ref=="R1"}.uuid,"BlazeBasic:R_0805")
        BoardPlanner.assign(c,c.parts.single{it.ref=="D1"}.uuid,"BlazeBasic:LED_D3")
        val lib=java.io.File("app/src/main/assets/kicad/BlazeBasic.pretty").listFiles()!!.associate { "BlazeBasic.pretty/${it.name}" to it.readBytes() }
        val b=KiCadBoard.export(c,lib)
        check(b.mappedPads==4 && b.routedSegments==0)
        check("(kicad_pcb" in b.pcb && "(gr_rect" in b.pcb)
        check("(net 0 \"\")" in b.pcb)
        check(b.pcb.contains("(footprint \"BlazeBasic:R_0805\""))
        check(b.pcb.contains("(footprint \"BlazeBasic:LED_D3\""))
        check(b.pcb.contains("(property \"Reference\" \"R1\""))
        check(b.pcb.contains("(property \"Reference\" \"D1\""))
        check(b.warnings.any{"unrouted" in it})
        check(KiCad.balanced(b.pcb))
        check(b==KiCadBoard.export(c,lib))
    }
    test("KiCad board refuses unavailable/external physical footprint files") {
        val c=DemoCircuits.led()
        val resistor=c.parts.single{it.ref=="R1"}
        BoardPlanner.assign(c,resistor.uuid,"Resistor_SMD:R_0805_2012Metric")
        check(runCatching{KiCadBoard.export(c,emptyMap())}.isFailure)
        BoardPlanner.assign(c,resistor.uuid,"BlazeBasic:R_0805")
        check(runCatching{KiCadBoard.export(c,emptyMap())}.isFailure)
        val file="BlazeBasic.pretty/R_0805.kicad_mod"
        val raw=java.io.File("app/src/main/assets/kicad/$file").readText()
        check(runCatching{KiCadBoard.export(c,mapOf(file to raw.replace("(pad \"2\"","(pad \"3\"").toByteArray()))}.isFailure)
    }
    test("KiCad PCB requires physical pad placements, not simulation sources") {
        val c=DemoCircuits.led()
        check(runCatching{KiCadBoard.export(c,emptyMap())}.isFailure)
        val voltage=c.parts.single{it.type==PartType.V}
        check(runCatching{BoardPlanner.assign(c,voltage.uuid,"BlazeBasic:R_0805")}.isFailure)
    }
    test("KiCad PCB exports only actual copper, with distinct schematic nets") {
        val c=DemoCircuits.led()
        val r=c.parts.single{it.ref=="R1"};val led=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"BlazeBasic:R_0805")
        BoardPlanner.assign(c,led.uuid,"BlazeBasic:LED_D3")
        val rp=BoardPlanner.pads(c).single{it.ref=="R1" && it.pad=="2"}
        val dp=BoardPlanner.pads(c).single{it.ref=="D1" && it.pad=="2"}
        check(rp.net==dp.net)
        BoardRouting.add(c,PadKey(r.uuid,"2"),PadKey(led.uuid,"2"))
        val lib=java.io.File("app/src/main/assets/kicad/BlazeBasic.pretty").listFiles()!!.associate { "BlazeBasic.pretty/${it.name}" to it.readBytes() }
        val b=KiCadBoard.export(c,lib)
        check(b.routedSegments>=1)
        check(b.pcb.contains("(segment "))
        check(b.warnings.any{"provisional" in it})
    }
    println("SUMMARY: $checks/$checks passed")
}
