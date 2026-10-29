package com.blaze.circuitlab

import com.blaze.circuitlab.core.*
import org.junit.Assert.*
import org.junit.Test

/** Android Gradle JVM unit tests. No device or physical board is represented by these tests. */
class CoreUnitTest {
    @Test fun ledCurrentRetainsAlphaNumerics() {
        val c=DemoCircuits.led()
        val result=MnaSolver.step(c,dt=0.015,time=0.015)
        assertTrue(result.message,result.ok)
        assertEquals(0.008771929825300092,result.currentIn(c.parts.single{it.ref=="R1"})!!,1e-9)
    }
    @Test fun currentSourceMaintainsItsRequestedCurrent() {
        val c=DemoCircuits.currentSource()
        val result=MnaSolver.step(c,dt=0.001)
        assertTrue(result.message,result.ok)
        assertEquals(0.01,result.currentIn(c.parts.single{it.ref=="R1"})!!,1e-9)
    }
    @Test fun inductorFirstStepMatchesBackwardEuler() {
        val c=DemoCircuits.rl()
        val result=MnaSolver.step(c,dt=0.001)
        assertTrue(result.message,result.ok)
        assertEquals(5.0/110.0,result.currentIn(c.parts.single{it.ref=="L1"})!!,1e-9)
    }
    @Test fun crossingRequiresExplicitJunction() {
        val c=Circuit(wires=mutableListOf(
            Wire(a=Point(-48,0),b=Point(48,0)), Wire(a=Point(0,-48),b=Point(0,48))))
        assertEquals(2,Topology.build(c).wireNet.values.toSet().size)
        c.junctions.add(Junction(at=Point(0,0)))
        assertEquals(1,Topology.build(c).wireNet.values.toSet().size)
    }
    @Test fun validManualRouteRemovesAnAirwire() {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        assertEquals(1,BoardPlanner.preview(c).airwires.size)
        BoardRouting.add(c,PadKey(r.uuid,"2"),PadKey(d.uuid,"2"))
        assertEquals(0,BoardPlanner.preview(c).airwires.size)
    }
    @Test fun differentNetRoutingIsRejected() {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        try {
            BoardRouting.add(c,PadKey(r.uuid,"1"),PadKey(d.uuid,"1"))
            fail("Different-net copper route must fail")
        } catch(_:IllegalArgumentException) { /* expected */ }
        assertTrue(c.board.routes.isEmpty())
    }
    @Test fun deletingRoutingInSnapshotPreservesOriginal() {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"};val d=c.parts.single{it.ref=="D1"}
        BoardPlanner.assign(c,r.uuid,"Resistor_SMD:R_0805_2012Metric")
        BoardPlanner.assign(c,d.uuid,"LED_THT:LED_D3.0mm")
        BoardRouting.add(c,PadKey(r.uuid,"2"),PadKey(d.uuid,"2"))
        val copy=c.detached();copy.board.routes.clear()
        assertEquals(1,c.board.routes.size)
    }
    @Test fun shockleyDiodeForwardCurrentIsPhysicallySolved() {
        val c=DemoCircuits.diode();val result=MnaSolver.step(c)
        assertTrue(result.message,result.ok)
        val d=c.parts.single{it.type==PartType.D}
        val v=result.voltageAcross(d)!!
        assertTrue(v>0.6 && v<1.2)
        assertEquals((5.0-v)/1000.0,result.currentIn(d)!!,1e-8)
    }
    @Test fun reverseBiasedDiodeHasNoFalseForwardCurrent() {
        val c=DemoCircuits.diodeReverse();val result=MnaSolver.step(c)
        assertTrue(result.message,result.ok)
        val d=c.parts.single{it.type==PartType.D}
        assertTrue(result.voltageAcross(d)!! < -4.9)
        assertTrue(kotlin.math.abs(result.currentIn(d)!!)<1e-10)
    }
    @Test fun physicallyMappedDiodePreservesCathodeAndAnode() {
        val c=DemoCircuits.diode();val d=c.parts.single{it.type==PartType.D}
        BoardPlanner.assign(c,d.uuid,"BlazeBasic:D_DO35_7.62")
        val pads=BoardPlanner.preview(c).pads.filter{it.ref=="D1"}.associateBy{it.pad}
        val nets=Topology.build(c)
        assertEquals(nets.net(d,1),pads.getValue("1").net)
        assertEquals(nets.net(d,0),pads.getValue("2").net)
    }
    @Test fun diodeEmitsExplicitSpiceModel() {
        val text=Spice.export(DemoCircuits.diode()).netlist
        assertTrue(text.contains(".model BCL_MODEL_D1 D(Is=0.000000000001 N=1.75)"))
        assertTrue(text.contains("D_D1 "))
    }
    @Test fun diodeInvalidParametersRejectBeforeSolve() {
        val c=DemoCircuits.diode();val d=c.parts.single{it.type==PartType.D}
        d.diodeIdeality=0.0
        assertFalse(MnaSolver.step(c).ok)
    }
    @Test fun realEmbeddedLibraries() {
        assertTrue(ElectronicLibrary.presets.size>=50)
        assertTrue(FootprintCatalog.available.count{it.libraryId.startsWith("BlazeBasic:")}>=14)
        assertEquals(4,McuLibrary.boards.size)
    }
    @Test fun planningPinSurvivesDetachedProjectAndIsNotSimulated() {
        val c=DemoCircuits.led();val r=c.parts.single{it.ref=="R1"}
        c.mcuPlan=McuPlan("arduino-uno-r3")
        McuLibrary.bind(c,"D13",r.uuid,1)
        assertEquals(1,McuLibrary.plannedNets(c).size)
        assertTrue(MnaSolver.step(c).ok)
    }
    @Test fun nmosTriodeDrainVoltageMatchesAnalyticSolution() {
        val c=DemoCircuits.nmosSwitch();val m=c.parts.single{it.type==PartType.MNMOS}
        val r=MnaSolver.step(c)
        assertTrue(r.message,r.ok)
        val expected=(5.0-kotlin.math.sqrt(5.0))/2.0
        assertEquals(expected,r.voltageAcross(m)!!,1e-5)
        assertEquals((5.0-expected)/1000.0,r.currentIn(m)!!,1e-7)
    }
    @Test fun nmosOffStateAndFloatingGateAreDistinct() {
        val c=DemoCircuits.nmosSwitch();val m=c.parts.single{it.type==PartType.MNMOS}
        c.parts.single{it.ref=="V2"}.value=1.0
        val off=MnaSolver.step(c)
        assertTrue(off.message,off.ok)
        assertEquals(0.0,off.currentIn(m)!!,1e-10)
        c.wires.removeAll{it.a==Point(432,276)&&it.b==Point(480,276)}
        val floating=MnaSolver.step(c)
        assertFalse(floating.ok)
        assertTrue(floating.message.contains("Floating"))
    }
    @Test fun nmosPadToNetMappingsUseSameSchematicTopology() {
        val c=DemoCircuits.nmosSwitch();val m=c.parts.single{it.type==PartType.MNMOS}
        BoardPlanner.assign(c,m.uuid,"BlazeBasic:M_TO92_GDS_2.54")
        val graph=Topology.build(c)
        val pads=BoardPlanner.preview(c).pads.filter{it.ref=="M1"}.associateBy{it.pad}
        for(i in 0..2)assertEquals(graph.net(m,i),pads.getValue("${i+1}").net)
    }
    @Test fun nmosEngineeringExportsContainExplicitModelMetadata() {
        val c=DemoCircuits.nmosSwitch()
        assertTrue(KiCad.export(c).schematic.contains("BlazeCircuitLab:MNMOS"))
        val sp=Spice.export(c)
        assertTrue(sp.netlist.contains("M_M1 "))
        assertTrue(sp.netlist.contains("Vto=2 Kp=0.002 Lambda=0"))
        assertTrue(sp.warnings.any{it.contains("body junctions")})
    }

    @Test fun poweredAmpClipsToSupplyRailRatherThanUnboundedGain() {
        val c=DemoCircuits.poweredAmplifier();val a=c.parts.single{it.type==PartType.OPAMP}
        val r=MnaSolver.step(c)
        assertTrue(r.message,r.ok)
        assertEquals(4.8,r.voltageAcross(a)!!,1e-5)
        c.parts.single{it.ref=="V3"}.value=-0.5
        val negative=MnaSolver.step(c)
        assertTrue(negative.message,negative.ok)
        assertEquals(-4.8,negative.voltageAcross(a)!!,1e-5)
    }
    @Test fun poweredAmpNeverGetsFabricatedPcbFootprintOrSpiceEquivalent() {
        val c=DemoCircuits.poweredAmplifier();val amp=c.parts.single{it.type==PartType.OPAMP}
        assertTrue(FootprintCatalog.forPart(amp).isEmpty())
        assertTrue(KiCad.export(c).schematic.contains("BlazeCircuitLab:OPAMP"))
        try {Spice.export(c);fail("Unverified powered op-amp model must reject SPICE export")}
        catch(_:IllegalArgumentException) {/* expected */}
    }
}
