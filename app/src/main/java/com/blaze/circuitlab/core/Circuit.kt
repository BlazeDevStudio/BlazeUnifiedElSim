package com.blaze.circuitlab.core

import java.util.UUID
import kotlin.math.roundToInt

/** Native authoritative electrical project data. Coordinates are original alpha SVG grid units. */
data class Point(val x: Int, val y: Int) {
    fun snapped(): Point = Point((x / 12.0).roundToInt() * 12, (y / 12.0).roundToInt() * 12)
}

enum class PartType { V, AC, R, C, LED, SW, GND, I, L, D, QNPN, MNMOS, EVCVS, OPAMP }

data class Part(
    val uuid: String = UUID.randomUUID().toString(),
    var ref: String,
    val type: PartType,
    var x: Int,
    var y: Int,
    var value: Double = defaults(type),
    var frequency: Double = 2.0,
    var closed: Boolean = true,
    var rotation: Int = 0,
    var footprint: String? = null,
    var catalogId: String? = null,
    var diodeIdeality: Double = 1.75,
    var temperatureC: Double = 25.0,
    var transistorIsA: Double = 1e-14,
    var transistorReverseBeta: Double = 2.0,
    var mosfetK: Double = 0.002, // A/V^2 for simple symmetric level-1-like channel; no body diode
    var opampHeadroomV: Double = 0.2 // Fixed symmetric rail headroom (V), not a device specification
) {
    companion object {
        fun defaults(type: PartType): Double = when (type) {
            PartType.V, PartType.AC -> 5.0
            PartType.I -> 0.010
            PartType.L -> 0.010
            PartType.R -> 330.0
            PartType.C -> 0.001
            PartType.D -> 1e-12 // Shockley diode reverse saturation current (A), not a rated device.
            PartType.QNPN -> 100.0 // Forward beta; Ebers-Moll fixed-Is, educational model.
            PartType.MNMOS -> 2.0 // Threshold voltage Vth, ideal channel model.
            PartType.EVCVS -> 10.0 // Ideal finite-gain VCVS; voltage gain, no power rails.
            PartType.OPAMP -> 20.0 // Controlled amplifier gain; requires connected power pins.
            PartType.LED -> 2.0
            PartType.SW -> 1.0
            PartType.GND -> 0.0
        }
    }
    /** Left/right terminal order is also numerical-solver current-reference order. */
    fun pins(): List<Point> {
        val local = when(type) {
            PartType.GND -> listOf(Point(0,-24))
            PartType.QNPN,PartType.MNMOS -> listOf(Point(-48,0),Point(48,-36),Point(48,36))
            PartType.EVCVS -> listOf(Point(-48,-36),Point(-48,36),Point(48,-36),Point(48,36)) // IN+, IN-, OUT+, OUT-
            PartType.OPAMP -> listOf(Point(-48,-36),Point(-48,36),Point(48,0),Point(0,-48),Point(0,48)) // IN+, IN-, OUT, V+, V-
            else -> listOf(Point(-48,0),Point(48,0))
        }
        return local.map { p -> when (((rotation % 360) + 360) % 360) {
            0 -> Point(x + p.x, y + p.y)
            90 -> Point(x - p.y, y + p.x)
            180 -> Point(x - p.x, y - p.y)
            270 -> Point(x + p.y, y - p.x)
            else -> throw IllegalArgumentException("Rotation must be 0, 90, 180 or 270")
        } }
    }
    fun clonePart(): Part = copy()
}

data class Wire(
    val uuid: String = UUID.randomUUID().toString(),
    var a: Point,
    var b: Point
)

data class Junction(val uuid: String = UUID.randomUUID().toString(), val at: Point)

data class Circuit(
    val version: Int = 9,
    var title: String = "Untitled Circuit",
    val parts: MutableList<Part> = mutableListOf(),
    val wires: MutableList<Wire> = mutableListOf(),
    val junctions: MutableList<Junction> = mutableListOf(),
    val board: BoardLayout = BoardLayout(),
    var mcuPlan: McuPlan? = null
) {
    fun detached(): Circuit = Circuit(version, title, parts.map { it.clonePart() }.toMutableList(),
        wires.map { it.copy() }.toMutableList(), junctions.map { it.copy() }.toMutableList(), board.detached(), mcuPlan?.detached())

    fun validate() {
        require(version == 9) { "Unknown circuit schema version" }
        require(parts.size <= 200 && wires.size <= 700 && junctions.size <= 700) { "Circuit resource limit" }
        require(title.length in 1..100) { "Invalid project title" }
        require((parts.map { it.uuid } + wires.map { it.uuid } + junctions.map { it.uuid }).distinct().size == parts.size + wires.size + junctions.size) { "Duplicate UUID" }
        require(parts.map { it.ref }.distinct().size == parts.size) { "Duplicate reference designator" }
        for (p in parts) {
            require(p.x in -100000..100000 && p.y in -100000..100000) { "Invalid component position" }
            require(p.value.isFinite() && p.frequency.isFinite() && p.diodeIdeality.isFinite() && p.temperatureC.isFinite() && p.transistorIsA.isFinite() && p.transistorReverseBeta.isFinite() && p.mosfetK.isFinite() && p.opampHeadroomV.isFinite()) { "Nonfinite component/model parameter" }
            require(p.ref.length in 1..32) { "Invalid reference designator" }
            require((p.footprint?.length?:0)<=160){"Footprint name exceeds 160 characters"}
            require(p.rotation in setOf(0,90,180,270)) { "Unsupported rotation" }
            ElectronicLibrary.validateIdentity(p)
            when(p.type) {
                PartType.R -> require(p.value in 1e-3..1e10) { "Resistor ${p.ref} is outside modeled range" }
                PartType.L -> require(p.value in 1e-12..1e4) { "Inductor ${p.ref} is outside modeled range" }
                PartType.I -> require(kotlin.math.abs(p.value) <= 1e5) { "Current source ${p.ref} is outside modeled range" }
                PartType.C -> require(p.value in 1e-15..1e3) { "Capacitor ${p.ref} is outside modeled range" }
                PartType.D -> {
                    require(p.value in 1e-18..1e-3) { "Diode Is outside 1e-18 to 1e-3 A" }
                    require(p.diodeIdeality in 1.0..4.0) { "Diode ideality must be 1 to 4" }
                    require(p.temperatureC in -40.0..125.0) { "Diode modeled temperature must be -40 to 125 C" }
                }
                PartType.QNPN -> {
                    require(p.value in 1.0..1000.0) { "NPN forward beta must be 1..1000" }
                    require(p.transistorIsA in 1e-18..1e-9) { "NPN Is must be 1e-18..1e-9 A" }
                    require(p.transistorReverseBeta in 0.1..100.0) { "NPN reverse beta must be 0.1..100" }
                    require(p.temperatureC in -40.0..125.0) { "NPN temperature must be -40..125 C" }
                }
                PartType.EVCVS -> require(p.value in 1e-6..1e7) { "VCVS voltage gain must be 1e-6..1e7" }
                PartType.OPAMP -> {
                    require(p.value in 1.0..1e4) { "Powered amplifier gain must be 1..10000" }
                    require(p.opampHeadroomV in 0.0..3.0) { "Powered amplifier rail headroom must be 0..3 V" }
                }
                PartType.MNMOS -> {
                    require(p.value in 0.1..10.0) { "NMOS Vth must be 0.1..10 V" }
                    require(p.mosfetK in 1e-9..10.0) { "NMOS K must be 1e-9..10 A/V²" }
                }
                PartType.LED -> require(p.value in .01..100.0) { "LED forward voltage is outside modeled range" }
                PartType.AC -> require(p.frequency in .001..1e7 && kotlin.math.abs(p.value)<=1e6) { "AC source is outside modeled range" }
                PartType.V -> require(kotlin.math.abs(p.value)<=1e6) { "Voltage source is outside modeled range" }
                else -> Unit
            }
        }
        for (w in wires) {
            require(w.a != w.b && (w.a.x == w.b.x || w.a.y == w.b.y)) { "Wires must be nonzero orthogonal segments" }
            require(listOf(w.a.x,w.a.y,w.b.x,w.b.y).all{it in -100000..100000}){"Wire outside supported canvas"}
        }
        require(junctions.map { it.at }.distinct().size == junctions.size) { "Duplicate junction coordinate" }
        require(junctions.all{it.at.x in -100000..100000 && it.at.y in -100000..100000}){"Junction outside supported canvas"}
        McuLibrary.validate(this)
    }
}

object DemoCircuits {
    fun led(): Circuit = Circuit(title="LED Current Limiter", parts=mutableListOf(
        part("V1",PartType.V,216,180,5.0),part("R1",PartType.R,384,180,330.0),
        part("D1",PartType.LED,552,180,2.0),part("G1",PartType.GND,240,384,0.0)
    ), wires=mutableListOf(w(264,180,336,180),w(432,180,504,180),w(600,180,672,180),
        w(672,180,672,360),w(672,360,168,360),w(168,360,168,180)),
        junctions=mutableListOf(Junction(at=Point(240,360))))
    fun rc(): Circuit {
        val c=led();c.title="RC Charge and Discharge";
        c.parts[0]=part("AC1",PartType.AC,216,180,5.0).also { it.frequency=.5 }
        c.parts[1]=part("R1",PartType.R,384,180,1000.0)
        c.parts[2]=part("C1",PartType.C,552,180,.001)
        return c
    }
    /** Supply -> R -> L -> return, with original alpha-style explicit ground junction. */
    fun rl(): Circuit = led().also { c ->
        c.title="RL Current Rise"
        c.parts[1].value=10.0
        val old=c.parts[2]
        c.parts[2]=Part(uuid=old.uuid,ref="L1",type=PartType.L,x=old.x,y=old.y,value=0.1)
    }
    /** 10 mA ideal current source and two resistors connected in series. */
    fun currentSource(): Circuit = led().also { c ->
        c.title="Current Source Series Loop"
        val v=c.parts[0]
        c.parts[0]=Part(uuid=v.uuid,ref="I1",type=PartType.I,x=v.x,y=v.y,value=0.01)
        val old=c.parts[2]
        c.parts[2]=Part(uuid=old.uuid,ref="R2",type=PartType.R,x=old.x,y=old.y,value=470.0)
    }
    /** 5 V source, 1 kOhm resistor and Shockley diode; physical diode requires real model calibration. */
    fun diode(): Circuit = led().also { c ->
        c.title="Diode Forward Bias — Shockley Model"
        c.parts[1].value=1000.0
        val old=c.parts[2]
        c.parts[2]=Part(uuid=old.uuid,ref="D1",type=PartType.D,x=old.x,y=old.y,value=1e-12)
    }
    /** Reversed diode in a resistor-limited loop; verifies leakage without fictional LED response. */
    fun diodeReverse(): Circuit = diode().also { c ->
        c.title="Diode Reverse Leakage"
        c.parts[2].rotation=180
        // Changing orientation also changes pin positions and terminal polarity; connections stay at the same coordinates.
    }
    fun switched(): Circuit = Circuit(title="Interactive LED Switch", parts=mutableListOf(
        part("V1",PartType.V,168,180,9.0),part("S1",PartType.SW,312,180,1.0),
        part("R1",PartType.R,456,180,470.0),part("D1",PartType.LED,600,180,2.0),
        part("G1",PartType.GND,240,384,0.0)
    ), wires=mutableListOf(w(216,180,264,180),w(360,180,408,180),w(504,180,552,180),w(648,180,720,180),
        w(720,180,720,360),w(720,360,120,360),w(120,360,120,180)),
        junctions=mutableListOf(Junction(at=Point(240,360))))
    /** 5 V rail and 0.8 V base supply, each through a resistor, shared grounded emitter. */
    fun npnSwitch(): Circuit = Circuit(title="NPN Common Emitter Bias", parts=mutableListOf(
        part("V1",PartType.V,216,180,5.0),
        part("V2",PartType.V,216,360,0.8),
        part("RC1",PartType.R,384,180,1000.0),
        part("RB1",PartType.R,384,300,10000.0),
        part("Q1",PartType.QNPN,552,300,100.0),
        part("G1",PartType.GND,240,480,0.0)
    ), wires=mutableListOf(
        w(264,180,336,180),w(432,180,600,180),w(600,180,600,264),
        w(264,360,336,360),w(336,360,336,300),w(432,300,504,300),
        w(600,336,600,456),w(168,180,168,360),w(168,360,168,456),
        w(168,456,600,456)
    ),junctions=mutableListOf(Junction(at=Point(240,456))))
    /** 5 V drain rail, 4 V independent gate bias, 1 kOhm load and grounded source.
     * Idealized symmetric NMOS channel: Vth=2 V, K=0.002 A/V^2, no body diode.
     */
    fun nmosSwitch(): Circuit = Circuit(title="NMOS Resistive Drain Load", parts=mutableListOf(
        part("V1",PartType.V,120,240,5.0),
        part("V2",PartType.V,264,420,4.0),
        part("RD1",PartType.R,312,240,1000.0),
        part("M1",PartType.MNMOS,528,276,2.0),
        part("G1",PartType.GND,144,480,0.0)
    ), wires=mutableListOf(
        w(168,240,264,240),w(360,240,576,240),
        w(576,312,660,312),w(660,312,660,456),w(72,456,660,456),w(72,240,72,456),
        w(312,420,432,420),w(432,276,432,420),w(432,276,480,276),
        w(216,420,180,420),w(180,420,180,456)
    ),junctions=mutableListOf(Junction(at=Point(144,456)),Junction(at=Point(180,456))))
    /** 1 V input -> 10x floating-input VCVS -> 1 kOhm load, common GND.
     * This is an unpowered behavioral amplifier, not a clipped/railed op-amp.
     */
    fun controlledAmplifier(): Circuit = Circuit(title="Behavioral Controlled Amplifier ×10",parts=mutableListOf(
        part("V1",PartType.V,192,156,1.0),part("E1",PartType.EVCVS,480,228,10.0),
        part("R1",PartType.R,720,192,1000.0),part("G1",PartType.GND,144,384,0.0)
    ),wires=mutableListOf(
        w(144,156,144,360),w(240,156,360,156),w(360,156,360,192),w(360,192,432,192),
        w(432,264,432,336),w(432,336,144,336),
        w(528,192,672,192),w(768,192,816,192),w(816,192,816,360),w(816,360,600,360),
        w(528,264,600,264),w(600,264,600,360),w(600,360,144,360)
    ), junctions=mutableListOf(Junction(at=Point(144,336)),Junction(at=Point(600,360))))
    /** 5 V/-5 V supplies, independent 0.5 V input, negative input at 0 V,
     * rail-limited gain=20 output and a 1 kOhm load. Supply + is source RIGHT pin.
     */
    fun poweredAmplifier(): Circuit = Circuit(title="Powered Op-Amp Rail Limiter", parts=mutableListOf(
        part("V1",PartType.V,144,180,5.0),part("V2",PartType.V,144,336,5.0),
        part("V3",PartType.V,192,240,0.5),part("A1",PartType.OPAMP,480,240,20.0),
        part("R1",PartType.R,672,240,1000.0),part("G1",PartType.GND,240,456,0.0)
    ), wires=mutableListOf(
        w(192,180,480,180),w(480,180,480,192),
        w(96,180,48,180),w(48,180,48,432),
        w(96,336,96,312),w(96,312,480,312),w(480,312,480,288),
        w(192,336,192,432),
        w(144,240,144,408),w(144,408,144,432),
        w(240,240,336,240),w(336,240,336,204),w(336,204,432,204),
        w(432,276,384,276),w(384,276,384,408),w(384,408,384,432),
        w(528,240,624,240),w(720,240,720,432),
        w(48,432,720,432)
    ), junctions=mutableListOf(
        Junction(at=Point(144,432)),Junction(at=Point(192,432)),
        Junction(at=Point(240,432)),Junction(at=Point(384,432))))
    private fun part(r:String,t:PartType,x:Int,y:Int,v:Double) = Part(uuid=UUID.nameUUIDFromBytes("alpha:$r:$x:$y".toByteArray()).toString(),ref=r,type=t,x=x,y=y,value=v)
    private fun w(ax:Int,ay:Int,bx:Int,by:Int)=Wire(a=Point(ax,ay),b=Point(bx,by))
}
