package com.blaze.circuitlab.core

import java.util.Locale
import java.util.UUID

/** Embedded, original Blaze catalogs. Names of KiCad library parts are compatibility identifiers,
 * not copies of third-party symbol files. Only types explicitly backed by MnaSolver are placeable.
 */
data class ElectronicPreset(
    val id: String,
    val title: String,
    val group: String,
    val simulationType: PartType,
    val value: Double,
    val unit: String,
    val kicadSymbol: String,
    val description: String,
    val suggestedFootprints: List<String> = emptyList(),
    val approximateModel: Boolean = false,
    val frequency: Double = 2.0
) {
    fun searchable() = "$id $title $group $unit $kicadSymbol $description".lowercase(Locale.ROOT)
}

object ElectronicLibrary {
    /** Presets represent values and physical applications, not new solver component models. */
    private val originalPresets: List<ElectronicPreset> = buildList {
        val resistors = listOf(10.0,22.0,47.0,100.0,220.0,330.0,470.0,680.0,1000.0,
            2200.0,4700.0,10000.0,22000.0,47000.0,100000.0,220000.0,470000.0,1000000.0)
        resistors.forEach { ohms ->
            add(ElectronicPreset("R-${ohms.toLong()}","Resistor ${ohms.toLong()} Ω","Passive / Resistors",
                PartType.R,ohms,"Ω","Device:R","Ideal resistor; tolerance and power rating not modeled",
                listOf("BlazeBasic:R_0805","BlazeBasic:R_Axial_7.62")))
        }
        listOf(1e-9,2.2e-9,4.7e-9,10e-9,22e-9,47e-9,100e-9,220e-9,470e-9,
            1e-6,2.2e-6,4.7e-6,10e-6,22e-6,47e-6,100e-6,220e-6,470e-6,1e-3,2.2e-3).forEachIndexed { i, farads ->
            add(ElectronicPreset("C-${i+1}","Capacitor ${formatCap(farads)}","Passive / Capacitors",
                PartType.C,farads,"F","Device:C","Ideal capacitor, backward Euler transient; ESR and polarity not modeled",
                listOf("BlazeBasic:C_0805","BlazeBasic:C_Disc_5.00")))
        }
        listOf(1e-14,1e-12,1e-10,1e-9).forEachIndexed{index,isA->
            add(ElectronicPreset("D-SHOCKLEY-${index+1}","Diode Shockley Is=${isA} A", "Semiconductor / Diodes",
                PartType.D,isA,"Is (A)","BlazeCircuitLab:D","Idealized Shockley junction: fixed Is, n=1.75, 25 C by default; no breakdown, capacitance or package parasitics",
                listOf("BlazeBasic:D_DO35_7.62"),true))
        }
        listOf(50.0,100.0,200.0,400.0).forEach { beta ->
            add(ElectronicPreset("QNPN-BETA-${beta.toInt()}","NPN transistor β=${beta.toInt()}","Semiconductor / Transistors",
                PartType.QNPN,beta,"β", "BlazeCircuitLab:QNPN",
                "Educational three-terminal Ebers-Moll NPN model: fixed Is=1e-14 A and reverse beta 2; no Early effect, breakdown, capacitance or thermal tracking",
                listOf("BlazeBasic:Q_TO92_EBC_2.54"),true))
        }
        listOf(1.0,2.0,3.0,4.0).forEach{vth->
            add(ElectronicPreset("MNMOS-VTH-${vth.toInt()}","Enhancement NMOS threshold ${vth.toInt()} V","Semiconductor / MOSFETs",
                PartType.MNMOS,vth,"Vth","BlazeCircuitLab:MNMOS",
                "Idealized symmetric N-MOS square-law channel: K=0.002 A/V²; no body diode, intrinsic capacitance, leakage or device-specific limits",
                listOf("BlazeBasic:M_TO92_GDS_2.54"),true))
        }
        listOf(10.0,100.0,1000.0,100000.0).forEach { gain ->
            add(ElectronicPreset("EVCVS-${gain.toInt()}","Ideal voltage-controlled amplifier ×${gain.toInt()}","Analog / Controlled Sources",
                PartType.EVCVS,gain,"V/V","BlazeCircuitLab:EVCVS",
                "Four-terminal SPICE E-equivalent: infinite input impedance, unpowered output, no clipping, finite gain; not a physical op-amp",emptyList(),true))
        }
        listOf(5.0,10.0,20.0,100.0).forEach { gain ->
            add(ElectronicPreset("OPAMP-${gain.toInt()}","Powered rail-clamped amplifier ×${gain.toInt()}","Analog / Amplifiers",
                PartType.OPAMP,gain,"V/V","BlazeCircuitLab:OPAMP",
                "Five-terminal educational rail-clamped amplifier: IN+, IN-, OUT, V+, V-. Infinite input impedance, gain and output headroom only; no bandwidth, slew or short-circuit protection.",emptyList(),true))
        }
        listOf("Red" to 1.9,"Amber" to 2.1,"Green" to 2.2,"Blue" to 3.1,"White" to 3.2,"Infrared" to 1.3).forEach { (color,vf) ->
            add(ElectronicPreset("LED-${color.uppercase(Locale.ROOT)}","${color} LED (typical Vf)","Semiconductor / LEDs",
                PartType.LED,vf,"V","Device:LED","Approximate piecewise-linear LED; color does not imply a tested device model",
                listOf("BlazeBasic:LED_D3","BlazeBasic:LED_0805"),true))
        }
        listOf(1.5,3.3,5.0,9.0,12.0,24.0).forEach { volts ->
            add(ElectronicPreset("DC-${volts}","DC source ${volts} V","Sources / DC",PartType.V,volts,"V",
                "Simulation_SPICE:VDC","Ideal voltage supply; simulation-only, no physical footprint"))
        }
        // Model-backed inductors (backward-Euler transient), not merely library placeholders.
        listOf(1e-6,4.7e-6,10e-6,22e-6,47e-6,100e-6,220e-6,470e-6,1e-3,2.2e-3,4.7e-3,10e-3,22e-3,47e-3,100e-3).forEachIndexed { index, henries ->
            add(ElectronicPreset("L-${index+1}","Inductor ${formatInductor(henries)}","Passive / Inductors",
                PartType.L,henries,"H","BlazeCircuitLab:L","Ideal inductor: backward-Euler transient current, no saturation, winding resistance, or coupling",
                listOf("BlazeBasic:L_Axial_7.62")))
        }
        listOf(0.0001,0.001,0.005,0.01,0.02,0.05,0.1).forEach { amps ->
            add(ElectronicPreset("I-${(amps*1000000).toInt()}","Current source ${(amps*1000).toSimple()} mA","Sources / DC current",
                PartType.I,amps,"A","BlazeCircuitLab:IDC","Ideal independent source: positive current left to right; simulation-only"))
        }
        listOf(1.0,5.0,12.0).forEach { amp ->
            add(ElectronicPreset("AC-${amp}","Sine source ±${amp} V (2 Hz)","Sources / AC",PartType.AC,amp,"V",
                "Simulation_SPICE:VSIN","Ideal sinusoidal source, simulated at 2 Hz"))
        }
        add(ElectronicPreset("SW-SPST","SPST switch, closed","Switches",PartType.SW,1.0,"state",
            "Switch:SW_SPST","Finite on/off resistance, ideal timing",listOf("BlazeBasic:SW_SPST_2Pad"),true))
        add(ElectronicPreset("GND","Ground reference","Power / Ground",PartType.GND,0.0,"V",
            "power:GND","Electrical reference; not a physical footprint"))
    }
    @Volatile private var bundledPresets: List<ElectronicPreset>? = null
    /** Android uses the validated package assets, not the compile-time fallback. The latter
     * permits isolated JVM solver verification before the Android runtime is initialized. */
    val presets: List<ElectronicPreset> get() = bundledPresets ?: originalPresets
    internal fun install(items: List<ElectronicPreset>) { bundledPresets = items.toList() }
    private fun formatInductor(v:Double):String = if(v>=1e-3)"${(v*1e3).toSimple()} mH" else "${(v*1e6).toSimple()} µH"
    private fun formatCap(v:Double):String = when {
        v>=1e-3 -> "${(v*1e3).toSimple()} mF"
        v>=1e-6 -> "${(v*1e6).toSimple()} µF"
        else -> "${(v*1e9).toSimple()} nF"
    }
    private fun Double.toSimple():String=String.format(Locale.US,"%.3f",this).trimEnd('0').trimEnd('.')
    fun search(query:String,group:String?=null):List<ElectronicPreset> {
        val q=query.trim().lowercase(Locale.ROOT)
        return presets.filter{(group==null||it.group==group)&&(q.isEmpty()||q.split(Regex("\\s+")).all{word->word in it.searchable()})}
    }
    fun byId(id:String)=presets.singleOrNull{it.id==id}
    fun instantiate(id:String,x:Int,y:Int,existing:Collection<Part>):Part {
        val entry=byId(id)?:throw IllegalArgumentException("Unknown electronics library preset: $id")
        val prefix=when(entry.simulationType){PartType.LED,PartType.D->"D";PartType.EVCVS->"E";PartType.OPAMP->"A";PartType.QNPN,PartType.MNMOS->if(entry.simulationType==PartType.MNMOS)"M" else "Q";PartType.SW->"S";PartType.GND->"G";else->entry.simulationType.name}
        var n=1;while(existing.any{it.ref=="$prefix$n"})n++
        return Part(ref="$prefix$n", type=entry.simulationType,x=x,y=y,value=entry.value,
            frequency=entry.frequency, catalogId=entry.id)
    }
    fun validateIdentity(part:Part) {
        if(part.catalogId!=null){val e=byId(part.catalogId!!)?:error("Missing bundled electronics library entry ${part.catalogId}")
            require(part.type==e.simulationType){"Catalog identity/type mismatch for ${part.ref}"}}
    }
}

/** Pin references are board-specific, never a claim of firmware emulation. */
data class McuPin(val name:String,val functions:Set<String>,val kind:String="GPIO",val caution:String="",
    val voltageV:Double?=null, val header:String="") {
    fun searchable():String="$name ${functions.joinToString(" ")} $kind $caution $header".lowercase(Locale.ROOT)
}
data class McuBoard(val id:String,val title:String,val logicVoltage:Double,val description:String,
    val pins:List<McuPin>,val source:String) {
    fun pin(name:String)=pins.singleOrNull{it.name.equals(name,true)}
}
data class McuPinBinding(val pin:String,val partUuid:String,val terminal:Int)
data class McuPlan(var boardId:String,val bindings:MutableList<McuPinBinding> = mutableListOf()) {
    fun detached():McuPlan=McuPlan(boardId,bindings.map{it.copy()}.toMutableList())
}
object McuLibrary {
    private fun gp(name:String,vararg funs:String,caution:String="",v:Double?=null,header:String="")=
        McuPin(name,funs.toSet(),"GPIO",caution,v,header)
    private fun power(name:String,v:Double?=null)=McuPin(name,setOf("POWER"),"POWER",voltageV=v)
    private fun gnd()=McuPin("GND",setOf("GROUND"),"GROUND")
    private val originalBoards:List<McuBoard> = listOf(
        McuBoard("arduino-uno-r3","Arduino UNO R3 / ATmega328P",5.0,
            "5 V I/O; 14 digital signals and 6 analog inputs. D0/D1 shared with serial.",
            (0..13).map{n->gp("D$n",*(listOf("DIGITAL")+
                (if(n in listOf(3,5,6,9,10,11))listOf("PWM") else emptyList())+
                when(n){0->listOf("UART_RX");1->listOf("UART_TX");10->listOf("SPI_SS");11->listOf("SPI_MOSI");12->listOf("SPI_MISO");13->listOf("SPI_SCK");else->emptyList()}).toTypedArray(),
                caution=if(n<=1)"Shared with UART / USB programming" else "") }+
            (0..5).map{gp("A$it","ADC",*(when(it){4->arrayOf("I2C_SDA");5->arrayOf("I2C_SCL");else->emptyArray()}))}+
            listOf(power("5V",5.0),power("3V3",3.3),power("VIN"),gnd(),McuPin("RESET",setOf("RESET"),"CONTROL")),
            "https://docs.arduino.cc/hardware/uno-rev3"),
        McuBoard("arduino-nano-classic","Arduino Nano classic / ATmega328P",5.0,
            "5 V I/O; Nano classic A6 and A7 are analog input only.",
            (0..13).map{n->gp("D$n",*(listOf("DIGITAL")+
                (if(n in listOf(3,5,6,9,10,11))listOf("PWM") else emptyList())+
                when(n){0->listOf("UART_RX");1->listOf("UART_TX");10->listOf("SPI_SS");11->listOf("SPI_MOSI");12->listOf("SPI_MISO");13->listOf("SPI_SCK");else->emptyList()}).toTypedArray())}+
            (0..7).map { n -> if(n>=6)McuPin("A$n",setOf("ADC"),"INPUT_ONLY")
                else gp("A$n","ADC",*(when(n){4->arrayOf("I2C_SDA");5->arrayOf("I2C_SCL");else->emptyArray()}))}+
            listOf(power("5V",5.0),power("3V3",3.3),power("VIN"),gnd(),McuPin("RESET",setOf("RESET"),"CONTROL")),
            "https://docs.arduino.cc/hardware/nano"),
        McuBoard("esp8266-nodemcu-1-0","ESP8266 NodeMCU 1.0 / ESP-12E",3.3,
            "3.3 V GPIO. D labels are NOT GPIO numbers; D3, D4 and D8 are boot strapping pins, and D0 lacks normal PWM/interrupt support.",
            listOf(16,5,4,0,2,14,12,13,15).mapIndexed { i,gpio ->
                val functions=buildSet {
                    add("GPIO$gpio");add("DIGITAL")
                    if(i in listOf(1,2))add(if(i==1)"I2C_SCL" else "I2C_SDA")
                    if(i in listOf(5,6,7))add("SPI")
                    if(i!=0)add("PWM")
                }
                McuPin("D$i",functions,"GPIO",
                    when(i){0->"GPIO16: no standard PWM/interrupt";3,4,8->"GPIO$gpio boot strap; external load can prevent startup";else->""},3.3)
            }+listOf(McuPin("A0",setOf("ADC0"),"INPUT_ONLY","ESP8266 ADC silicon limit 0-1 V; module input scaling varies",1.0),
                gp("RX","GPIO3","UART_RX",caution="Shared with USB serial",v=3.3),
                gp("TX","GPIO1","UART_TX",caution="Shared with USB serial",v=3.3),
                power("3V3",3.3),power("VIN"),gnd(),McuPin("RST",setOf("RESET"),"CONTROL")),
            "https://nodemcu.readthedocs.io/en/dev/modules/gpio/"),
        McuBoard("esp32-devkitc-v4-wroom32e","ESP32-DevKitC V4 (ESP32-WROOM-32E)",3.3,
            "3.3 V GPIO; GPIO34–39 input only, GPIO6–11 reserved for flash and not listed as usable GPIO; ADC2 may conflict with Wi-Fi.",
            listOf(0,1,2,3,4,5,12,13,14,15,16,17,18,19,21,22,23,25,26,27,32,33,34,35,36,39).map{n->
                val caution=buildList{if(n in listOf(0,2,5,12,15))add("Strapping/boot pin")
                    if(n in listOf(34,35,36,39))add("Input only")
                    if(n in listOf(0,2,4,12,13,14,15,25,26,27))add("ADC2 shared with Wi-Fi")}.joinToString("; ")
                val roles=buildList{add("GPIO");if(n in listOf(34,35,36,39,32,33))add("ADC1")
                    if(n in listOf(0,2,4,12,13,14,15,25,26,27))add("ADC2")
                    if(n==21)add("I2C_SDA");if(n==22)add("I2C_SCL")
                    if(n==23)add("SPI_MOSI");if(n==19)add("SPI_MISO");if(n==18)add("SPI_SCK")
                    if(n==1)add("UART_TX");if(n==3)add("UART_RX")
                    if(n==25)add("DAC1");if(n==26)add("DAC2");if(n in listOf(18,19,21,22,23,25,26,27,32,33))add("PWM")}
                McuPin("GPIO$n",roles.toSet(),if(n in listOf(34,35,36,39))"INPUT_ONLY" else "GPIO",caution,3.3)
            }+listOf(power("3V3",3.3),power("5V",5.0),gnd(),McuPin("EN",setOf("RESET","ENABLE"),"CONTROL")),
            "https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html")
    )
    @Volatile private var bundledBoards: List<McuBoard>? = null
    val boards: List<McuBoard> get() = bundledBoards ?: originalBoards
    internal fun install(items: List<McuBoard>) { bundledBoards = items.toList() }
    fun board(id:String)=boards.singleOrNull{it.id==id}
    fun searchBoards(q:String)=boards.filter{"$q".isBlank()||("${it.title} ${it.description} ${it.id}").contains(q,true)}
    fun searchPins(boardId:String,q:String)=board(boardId)?.pins?.filter{q.isBlank()||it.searchable().contains(q.lowercase(Locale.ROOT))}?: emptyList()
    fun validate(c:Circuit) {
        val plan=c.mcuPlan?:return
        val board=board(plan.boardId)?:error("Unknown MCU board ${plan.boardId}")
        require(plan.bindings.size<=128){"Too many MCU plan bindings"}
        require(plan.bindings.map{it.pin}.distinct().size==plan.bindings.size){"MCU pin assigned twice"}
        for(b in plan.bindings){
            val pin=board.pin(b.pin)?:error("Unknown MCU pin ${b.pin}")
            require(pin.kind=="GPIO"||pin.kind=="INPUT_ONLY"){"Pin ${b.pin} is not a GPIO planning pin"}
            val part=c.parts.singleOrNull{it.uuid==b.partUuid}?:error("MCU pin binding refers to missing component")
            require(b.terminal in part.pins().indices){"MCU pin binding terminal invalid"}
        }
    }
    data class PlannedNet(val pin:String,val net:Int,val connectedTo:String,val voltageHint:String)
    fun plannedNets(c:Circuit):List<PlannedNet> {
        validate(c);val plan=c.mcuPlan?:return emptyList();val graph=Topology.build(c)
        val board=board(plan.boardId)!!
        return plan.bindings.map{b->val part=c.parts.single{it.uuid==b.partUuid}
            val net=graph.net(part,b.terminal)
            val hint=if(board.logicVoltage<=3.3&&c.parts.any{p->p.type==PartType.V&&kotlin.math.abs(p.value)>board.logicVoltage+.5})
                "Inspect source and net voltage: 3.3V GPIO is not 5V tolerant" else "No verified voltage safety analysis"
            PlannedNet(b.pin,net,"${part.ref}:${b.terminal}",hint)
        }
    }
    fun bind(c:Circuit,pin:String,partUuid:String,terminal:Int) {
        val plan=c.mcuPlan?:error("Select an MCU board first")
        val old=plan.bindings.toList();plan.bindings.removeAll{it.pin==pin};plan.bindings.add(McuPinBinding(pin,partUuid,terminal))
        try { validate(c) } catch(e:Exception){plan.bindings.clear();plan.bindings.addAll(old);throw e}
    }
    fun csv(c:Circuit):String {
        val board=c.mcuPlan?.let{board(it.boardId)}?:error("No MCU board selected")
        val head="board,pin,functions,net,connected_to,warning\r\n"
        return head+plannedNets(c).joinToString(""){line->
            val p=board.pin(line.pin)!!
            listOf(board.id,p.name,p.functions.joinToString("|"),line.net.toString(),line.connectedTo,
                (p.caution+"; "+line.voltageHint).trim(';',' ')).joinToString(","){s->"\""+s.replace("\"","\"\"")+"\""}+"\r\n"
        }
    }
}
