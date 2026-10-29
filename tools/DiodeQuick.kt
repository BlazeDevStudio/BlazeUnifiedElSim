import com.blaze.circuitlab.core.*
fun main(){ for (c in listOf(DemoCircuits.diode(),DemoCircuits.diodeReverse())){ val r=MnaSolver.step(c);println("${c.title} OK=${r.ok} ${r.message} iter ${r.iterations} V=${r.voltageAcross(c.parts[2])} I=${r.currentIn(c.parts[2])} R=${r.currentIn(c.parts[1])}") }}
