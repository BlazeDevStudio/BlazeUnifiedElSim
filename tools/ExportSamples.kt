import com.blaze.circuitlab.core.*
import java.io.File

fun main(){
    val dir=File("samples").also{it.mkdirs()}
    val circuits=listOf("LED_Current_Limiter" to DemoCircuits.led(),"RC_Charging" to DemoCircuits.rc(),"LED_Switch" to DemoCircuits.switched(),
        "RL_Current_Rise" to DemoCircuits.rl(), "DC_Current_Source" to DemoCircuits.currentSource(),
        "Shockley_Diode" to DemoCircuits.diode(),"NPN_Bias" to DemoCircuits.npnSwitch(),"NMOS_Resistive_Load" to DemoCircuits.nmosSwitch(),"Controlled_Amplifier" to DemoCircuits.controlledAmplifier(),
        "Powered_Opamp_Rail_Limiter" to DemoCircuits.poweredAmplifier())
    for((name,c) in circuits){
        val sch=KiCad.export(c)
        check(KiCad.balanced(sch.schematic))
        File(dir,"$name.kicad_sch").writeText(sch.schematic)
    }
    for((name,circuit) in listOf("RL_Current_Rise" to DemoCircuits.rl(),"DC_Current_Source" to DemoCircuits.currentSource(),
        "Shockley_Diode" to DemoCircuits.diode(),"NPN_Bias" to DemoCircuits.npnSwitch(),"NMOS_Resistive_Load" to DemoCircuits.nmosSwitch(),"Controlled_Amplifier" to DemoCircuits.controlledAmplifier())) {
        File(dir,"$name.cir").writeText(Spice.export(circuit).netlist)
    }
    val c=DemoCircuits.led()
    BoardPlanner.assign(c,c.parts.single{it.ref=="R1"}.uuid,"BlazeBasic:R_0805")
    BoardPlanner.assign(c,c.parts.single{it.ref=="D1"}.uuid,"BlazeBasic:LED_D3")
    val src=File("app/src/main/assets/kicad")
    val lib=mutableMapOf<String,ByteArray>()
    for(file in src.listFiles()!!.filter{it.isFile})lib[file.name]=file.readBytes()
    for(file in File(src,"BlazeBasic.pretty").listFiles()!!)lib["BlazeBasic.pretty/${file.name}"]=file.readBytes()
    File(dir,"LED_With_Libraries.zip").writeBytes(KiCadPackage.pack(c,lib))
    val npn=DemoCircuits.npnSwitch()
    BoardPlanner.assign(npn,npn.parts.single{it.ref=="Q1"}.uuid,"BlazeBasic:Q_TO92_EBC_2.54")
    File(dir,"NPN_With_Libraries.zip").writeBytes(KiCadPackage.pack(npn,lib))
    val nmos=DemoCircuits.nmosSwitch()
    BoardPlanner.assign(nmos,nmos.parts.single{it.ref=="M1"}.uuid,"BlazeBasic:M_TO92_GDS_2.54")
    File(dir,"NMOS_With_Libraries.zip").writeBytes(KiCadPackage.pack(nmos,lib))
    File(dir,"Powered_Amp_With_Libraries.zip").writeBytes(KiCadPackage.pack(DemoCircuits.poweredAmplifier(),lib))
    c.mcuPlan=McuPlan("arduino-uno-r3")
    McuLibrary.bind(c,"D13",c.parts.single{it.ref=="R1"}.uuid,1)
    File(dir,"Arduino_Uno_Pin_Planning.csv").writeText(McuLibrary.csv(c))
    println("Exported samples: ${dir.listFiles()!!.map{it.name}}")
}
