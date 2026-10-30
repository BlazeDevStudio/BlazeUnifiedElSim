package com.blaze.circuitlab.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.ln

/** Bounded educational MNA: backward-Euler reactive elements, Shockley diodes, legacy piecewise LEDs. Not ngspice. */
object MnaSolver {
    /** Is in amperes, n dimensionless, T in Celsius. Fixed-Is temperature model, no breakdown/series R/capacitance. */
    object DiodeModel {
        private const val BOLTZMANN_OVER_Q = 8.617333262145e-5 // V/K
        fun thermalVoltage(celsius:Double,ideality:Double)=(celsius+273.15)*BOLTZMANN_OVER_Q*ideality
        fun current(voltage:Double,isA:Double,ideality:Double,celsius:Double):Double {
            val vT=thermalVoltage(celsius,ideality)
            return isA * (exp((voltage/vT).coerceIn(-80.0,40.0))-1.0)
        }
        fun conductance(voltage:Double,isA:Double,ideality:Double,celsius:Double):Double {
            val vT=thermalVoltage(celsius,ideality)
            return isA / vT * exp((voltage/vT).coerceIn(-80.0,40.0))
        }
    }
    /** Educational forward/reverse Ebers-Moll NPN. B, C, E terminal currents sum to zero. */
    object NpnModel {
        data class Currents(val base:Double,val collector:Double,val emitter:Double,
            val baseDeriv:DoubleArray,val collectorDeriv:DoubleArray)
        fun evaluate(vb:Double,vc:Double,ve:Double,beta:Double,isA:Double,reverseBeta:Double,celsius:Double):Currents {
            val vt=DiodeModel.thermalVoltage(celsius,1.0)
            val vf=vb-ve;val vr=vb-vc
            val iF=isA*(exp((vf/vt).coerceIn(-80.0,40.0))-1.0)
            val iR=isA*(exp((vr/vt).coerceIn(-80.0,40.0))-1.0)
            val gF=isA/vt*exp((vf/vt).coerceIn(-80.0,40.0))
            val gR=isA/vt*exp((vr/vt).coerceIn(-80.0,40.0))
            val alphaF=beta/(beta+1);val alphaR=reverseBeta/(reverseBeta+1)
            val ib=(1-alphaF)*iF+(1-alphaR)*iR
            val ic=alphaF*iF-iR
            // derivatives are ordered by B,C,E node voltage
            val db=doubleArrayOf((1-alphaF)*gF+(1-alphaR)*gR,-(1-alphaR)*gR,-(1-alphaF)*gF)
            val dc=doubleArrayOf(alphaF*gF-gR,gR,-alphaF*gF)
            return Currents(ib,ic,-ib-ic,db,dc)
        }
    }
    /** Educational enhancement NMOS with source/drain-symmetric square-law channel.
     * G, D, S derivative order; Id is positive D -> S. No body diode, parasitics,
     * subthreshold leakage, channel length modulation, or gate current.
     * K in A/V²; Id=K*((Vgs-Vth)*Vds - Vds²/2) in triode, K/2*Vov² in saturation.
     */
    object NmosModel {
        data class Drain(val current:Double,val derivatives:DoubleArray)
        fun evaluate(vg:Double,vd:Double,vs:Double,vth:Double,k:Double):Drain {
            val forward=vd>=vs
            val s=if(forward)vs else vd
            val d=if(forward)vd else vs
            val ov=vg-s-vth
            val vds=d-s
            if(ov<=0.0 || vds<=0.0)return Drain(0.0,doubleArrayOf(0.0,0.0,0.0))
            val saturated=vds>=ov
            val id=if(saturated)0.5*k*ov*ov else k*(ov*vds-0.5*vds*vds)
            val dGate=if(saturated)k*ov else k*vds
            val dHigh=if(saturated)0.0 else k*(ov-vds)
            val dLow=-dGate-dHigh
            return if(forward)Drain(id,doubleArrayOf(dGate,dHigh,dLow))
                else Drain(-id,doubleArrayOf(-dGate,-dLow,-dHigh))
        }
    }
    data class State(val capacitorVoltage: Map<String,Double> = emptyMap(), val nodeVoltage: Map<Int,Double> = emptyMap(),
        val inductorCurrent: Map<String,Double> = emptyMap())
    data class Result(
        val ok: Boolean, val message: String, val topology: Topology.NetGraph?,
        val voltage: Map<Int,Double> = emptyMap(), val current: Map<String,Double> = emptyMap(),
        val state: State = State(), val iterations: Int = 0, val time: Double = 0.0
    ) {
        fun voltageAcross(p:Part):Double? = topology?.let{ t ->
            if(p.type==PartType.GND) 0.0 else if(p.type==PartType.EVCVS) (voltage[t.net(p,2)]?:return null)-(voltage[t.net(p,3)]?:return null)
            else if(p.type==PartType.OPAMP) voltage[t.net(p,2)]
            else if((p.type==PartType.QNPN||p.type==PartType.MNMOS)) (voltage[t.net(p,1)]?:return null)-(voltage[t.net(p,2)]?:return null)
            else (voltage[t.net(p,0)]?:return null)-(voltage[t.net(p,1)]?:return null)
        }
        fun currentIn(p:Part) = current[p.uuid]
    }
    private fun solveLinear(a:Array<DoubleArray>,b:DoubleArray):DoubleArray {
        val n=b.size
        val m=Array(n){i->DoubleArray(n+1){j->if(j==n)b[i] else a[i][j]}}
        for(k in 0 until n){
            var pivot=k;for(i in k+1 until n)if(abs(m[i][k])>abs(m[pivot][k]))pivot=i
            if(abs(m[pivot][k])<1e-13)throw IllegalArgumentException("Singular circuit matrix or incompatible ideal voltage sources")
            if(pivot!=k){val tmp=m[k];m[k]=m[pivot];m[pivot]=tmp}
            val div=m[k][k];for(j in k..n)m[k][j]/=div
            for(i in 0 until n)if(i!=k){val factor=m[i][k];if(factor!=0.0)for(j in k..n)m[i][j]-=factor*m[k][j]}
        }
        return DoubleArray(n){m[it][n]}
    }
    private fun verifyGroundReachability(circuit:Circuit,t:Topology.NetGraph) {
        val g=t.groundNet?:throw IllegalArgumentException("Add a GND reference before simulating")
        val neighbors=mutableMapOf<Int,MutableSet<Int>>()
        for(p in circuit.parts)if(p.type!=PartType.GND){
            val a=t.net(p,0);val b=t.net(p,1)
            if(p.type!=PartType.MNMOS && p.type!=PartType.EVCVS && p.type!=PartType.OPAMP){
                neighbors.getOrPut(a){mutableSetOf()}.add(b)
                neighbors.getOrPut(b){mutableSetOf()}.add(a)
            }
            if(p.type==PartType.OPAMP) {
                // OUT is a solver voltage source to the ground reference. Inputs and rails
                // must independently reach GND through external circuitry.
                val o=t.net(p,2)
                neighbors.getOrPut(o){mutableSetOf()}.add(g)
                neighbors.getOrPut(g){mutableSetOf()}.add(o)
            } else if(p.type==PartType.EVCVS) {
                // The controlling input pins are infinite impedance. Only the output
                // voltage constraint ties the two output terminals into the DC network.
                val o=t.net(p,2);val n=t.net(p,3)
                neighbors.getOrPut(o){mutableSetOf()}.add(n)
                neighbors.getOrPut(n){mutableSetOf()}.add(o)
            } else if(p.type==PartType.QNPN){
                val e=t.net(p,2)
                neighbors.getOrPut(a){mutableSetOf()}.add(e)
                neighbors.getOrPut(b){mutableSetOf()}.add(e)
                neighbors.getOrPut(e){mutableSetOf()}.addAll(listOf(a,b))
            } else if(p.type==PartType.MNMOS){
                // Unlike a BJT base, an insulated MOS gate has NO DC conductive path
                // through the device. Treat an unwired gate as floating, not grounded.
                val s=t.net(p,2)
                neighbors.getOrPut(b){mutableSetOf()}.add(s)
                neighbors.getOrPut(s){mutableSetOf()}.add(b)
            }
        }
        val seen=mutableSetOf(g);val queue=ArrayDeque<Int>();queue.add(g)
        while(queue.isNotEmpty())for(v in neighbors[queue.removeFirst()].orEmpty())if(seen.add(v))queue.add(v)
        val used=circuit.parts.flatMap{p->p.pins().indices.map{i->t.net(p,i)}}
        if(used.any{it !in seen})throw IllegalArgumentException("Floating circuit island: connect the network to GND")
    }
    fun step(circuit:Circuit,state:State=State(),dt:Double=.015,time:Double=0.0):Result {
        var topology:Topology.NetGraph?=null
        try {
            require(dt.isFinite() && dt>0 && dt<=1.0) { "Invalid simulation timestep" }
            topology=Topology.build(circuit)
            val t=topology
            verifyGroundReachability(circuit,t)
            val ground=t.groundNet!!
            val roots=circuit.parts.flatMap{p->p.pins().indices.map{i->t.net(p,i)}}.distinct().filter{it!=ground}.sorted()
            val idx=roots.withIndex().associate{it.value to it.index}
            val sources=circuit.parts.filter{it.type==PartType.V||it.type==PartType.AC||it.type==PartType.EVCVS||it.type==PartType.OPAMP}
            val n=roots.size+sources.size
            require(n in 1..120){"Unsupported circuit size or missing active network"}
            fun node(net:Int):Int=if(net==ground)-1 else idx[net]?:throw IllegalStateException("Net missing from MNA")
            fun sourceVoltage(p:Part):Double=if(p.type==PartType.AC)p.value*sin(2*PI*p.frequency*time) else p.value
            var previous=state.nodeVoltage
            var solution=DoubleArray(n)
            var volts=emptyMap<Int,Double>()
            var iterations=0
            var converged=false
            val nonlinearParts=circuit.parts.filter{it.type==PartType.D||it.type==PartType.QNPN||it.type==PartType.MNMOS||it.type==PartType.OPAMP}
            for(iter in 0 until if(nonlinearParts.isEmpty())24 else 100) {
                iterations=iter+1
                val A=Array(n){DoubleArray(n)};val b=DoubleArray(n)
                fun stampG(a:Int,z:Int,g:Double){
                    val x=node(a);val y=node(z)
                    if(x>=0)A[x][x]+=g
                    if(y>=0)A[y][y]+=g
                    if(x>=0&&y>=0){A[x][y]-=g;A[y][x]-=g}
                }
                fun stampI(a:Int,z:Int,i:Double){val x=node(a);val y=node(z);if(x>=0)b[x]-=i;if(y>=0)b[y]+=i}
                for(i in roots.indices)A[i][i]+=1e-11 // Numerical regularization; floating networks rejected above.
                // Linearize terminal currents about the current Newton iterate.
                // A rows are KCL at source node, with a sink at the other terminal.
                fun nonlinearBranch(from:Int,to:Int,current:Double,nets:IntArray,derivatives:DoubleArray) {
                    val constant=current-nets.indices.sumOf { j -> derivatives[j]*(previous[nets[j]]?:0.0) }
                    fun put(rowNet:Int,sign:Double){
                        val row=node(rowNet);if(row<0)return
                        nets.indices.forEach { j -> val col=node(nets[j]);if(col>=0)A[row][col]+=sign*derivatives[j] }
                        b[row]-=sign*constant
                    }
                    put(from,1.0);put(to,-1.0)
                }
                for(p in circuit.parts) {
                    if(p.type==PartType.GND)continue
                    val a=t.net(p,0);val z=t.net(p,1)
                    when(p.type){
                        PartType.MNMOS -> {
                            val s=t.net(p,2)
                            val v=NmosModel.evaluate(previous[a]?:0.0,previous[z]?:0.0,previous[s]?:0.0,p.value,p.mosfetK)
                            nonlinearBranch(z,s,v.current,intArrayOf(a,z,s),v.derivatives)
                        }
                        PartType.QNPN -> {
                            val e=t.net(p,2)
                            val ports=intArrayOf(a,z,e)
                            val currents=NpnModel.evaluate(previous[a]?:0.0,previous[z]?:0.0,previous[e]?:0.0,
                                p.value,p.transistorIsA,p.transistorReverseBeta,p.temperatureC)
                            nonlinearBranch(a,e,currents.base,ports,currents.baseDeriv)
                            nonlinearBranch(z,e,currents.collector,ports,currents.collectorDeriv)
                        }
                        PartType.R -> stampG(a,z,1/p.value)
                        PartType.SW -> stampG(a,z,if(p.closed)20.0 else 1e-10)
                        PartType.I -> stampI(a,z,p.value) // Positive I flows pin 0 (left) to pin 1 (right).
                        PartType.L -> {
                            // Backward Euler Norton equivalent: i_n = i_(n-1) + (dt/L)*v_n.
                            // Unlike a capacitor, the inductor stores branch CURRENT.
                            val g=dt/p.value
                            stampG(a,z,g)
                            stampI(a,z,state.inductorCurrent[p.uuid]?:0.0)
                        }
                        PartType.C -> {
                            val g=p.value/dt
                            stampG(a,z,g);stampI(a,z,-g*(state.capacitorVoltage[p.uuid]?:0.0))
                        }
                        PartType.D -> {
                            val v=(previous[a]?:0.0)-(previous[z]?:0.0)
                            val gd=DiodeModel.conductance(v,p.value,p.diodeIdeality,p.temperatureC)
                            val id=DiodeModel.current(v,p.value,p.diodeIdeality,p.temperatureC)
                            stampG(a,z,gd+1e-12) // tiny off-state leakage regularizer; counted in branch current
                            stampI(a,z,id-gd*v-1e-12*v)
                        }
                        PartType.LED -> {
                            val v=(previous[a]?:0.0)-(previous[z]?:0.0)
                            val on=v>=p.value
                            val g=if(on)1.0/12.0 else 1e-8
                            stampG(a,z,g)
                            if(on)stampI(a,z,-g*p.value)
                        }
                        else -> Unit
                    }
                }
                for((j,p) in sources.withIndex()){
                    val c=roots.size+j
                    if(p.type==PartType.OPAMP) {
                        // Five-pin behavioral model: Vout = clamp(A*(V+in - V-in), V-rail+H, V+rail-H).
                        // Newton linearization includes the controlling input AND rail derivatives.
                        // Rail headroom is a simplified approximation; no bandwidth, slew or load limit.
                        val ninp=t.net(p,0);val ninm=t.net(p,1)
                        val no=t.net(p,2);val nplus=t.net(p,3);val nminus=t.net(p,4)
                        val vinp=previous[ninp]?:0.0;val vinm=previous[ninm]?:0.0
                        val vplus=previous[nplus]?:0.0;val vminus=previous[nminus]?:0.0
                        val low=vminus+p.opampHeadroomV;val high=vplus-p.opampHeadroomV
                        val raw=p.value*(vinp-vinm)
                        val controlled=if(high<=low) 0.0 else raw.coerceIn(low,high)
                        val d=when {
                            high<=low->doubleArrayOf(0.0,0.0,0.0,0.0)
                            raw<=low->doubleArrayOf(0.0,0.0,0.0,1.0)
                            raw>=high->doubleArrayOf(0.0,0.0,1.0,0.0)
                            else->doubleArrayOf(p.value,-p.value,0.0,0.0)
                        }
                        val ports=intArrayOf(ninp,ninm,nplus,nminus)
                        val values=doubleArrayOf(vinp,vinm,vplus,vminus)
                        val out=node(no)
                        require(out>=0) { "Powered amplifier OUT must not be shorted directly to GND" }
                        A[out][c]++;A[c][out]++
                        for(k in ports.indices) {
                            val ix=node(ports[k]);if(ix>=0)A[c][ix]-=d[k]
                        }
                        b[c]=controlled-ports.indices.sumOf{k->d[k]*values[k]}
                    } else if(p.type==PartType.EVCVS) {
                        // Constraint: V(out+) - V(out-) = gain * (V(in+) - V(in-)).
                        // One MNA source-current unknown ensures the output current is calculated.
                        val o=node(t.net(p,2));val nout=node(t.net(p,3))
                        if(o>=0){A[o][c]++;A[c][o]++}
                        if(nout>=0){A[nout][c]--;A[c][nout]--}
                        val ip=node(t.net(p,0));val im=node(t.net(p,1))
                        if(ip>=0)A[c][ip]-=p.value
                        if(im>=0)A[c][im]+=p.value
                    } else {
                        val a=node(t.net(p,0));val z=node(t.net(p,1))
                        if(a>=0){A[a][c]++;A[c][a]++};if(z>=0){A[z][c]--;A[c][z]--}
                        b[c]=-sourceVoltage(p) // Original alpha positive terminal is on the RIGHT.
                    }
                }
                solution=solveLinear(A,b)
                volts=buildMap{put(ground,0.0);roots.forEachIndexed{i,r->put(r,solution[i])}}
                // Damped Newton: bound diode and transistor junction steps to 0.18 V to avoid runaway exponential steps.
                // Pure linear/legacy circuits preserve their prior unmodified solve path and results.
                val alpha = if(nonlinearParts.isEmpty()) 1.0 else {
                    val jump=nonlinearParts.maxOf { d ->
                        val a=t.net(d,0);val z=t.net(d,1)
                        val dif=abs(((volts[a]?:0.0)-(volts[z]?:0.0)) - ((previous[a]?:0.0)-(previous[z]?:0.0)))
                        if(d.type==PartType.OPAMP) {
                            val out=t.net(d,2)
                            max(dif, abs((volts[out]?:0.0)-(previous[out]?:0.0)))
                        } else if(d.type!=PartType.QNPN && d.type!=PartType.MNMOS)dif else {
                            val e=t.net(d,2)
                            max(dif,abs(((volts[a]?:0.0)-(volts[e]?:0.0)) - ((previous[a]?:0.0)-(previous[e]?:0.0))))
                        }
                    }
                    if(jump<=0.18)1.0 else 0.18/jump
                }
                val relaxed=if(alpha==1.0)volts else buildMap<Int,Double> {
                    put(ground,0.0)
                    roots.forEach{r->put(r,(previous[r]?:0.0)+alpha*((volts[r]?:0.0)-(previous[r]?:0.0)))}
                }
                val delta=roots.maxOfOrNull{abs((previous[it]?:0.0)-(relaxed[it]?:0.0))}?:0.0
                previous=relaxed
                if(delta<1e-7){converged=true;break}
            }
            if(!converged)throw IllegalArgumentException("Nonlinear/controlled-source iteration did not converge")
            for(p in circuit.parts.filter{it.type==PartType.OPAMP}) {
                val railPlus=volts[t.net(p,3)]?:0.0
                val railMinus=volts[t.net(p,4)]?:0.0
                require(railPlus-railMinus > 2*p.opampHeadroomV+1e-6) {
                    "${p.ref}: invalid supply rails (V+ must exceed V- by more than twice headroom)"
                }
            }
            val currents=mutableMapOf<String,Double>();val caps=state.capacitorVoltage.toMutableMap()
            val inductors=state.inductorCurrent.toMutableMap()
            for(p in circuit.parts){
                if(p.type==PartType.GND)continue
                val v=(volts[t.net(p,0)]?:0.0)-(volts[t.net(p,1)]?:0.0)
                val i=when(p.type){
                    PartType.R->v/p.value
                    PartType.I->p.value
                    PartType.L->{val i=(state.inductorCurrent[p.uuid]?:0.0)+dt/p.value*v;inductors[p.uuid]=i;i}
                    PartType.SW->v/(if(p.closed)0.05 else 1e10)
                    PartType.C->{val i=p.value/dt*(v-(state.capacitorVoltage[p.uuid]?:0.0));caps[p.uuid]=v;i}
                    PartType.D->DiodeModel.current(v,p.value,p.diodeIdeality,p.temperatureC)+1e-12*v
                    PartType.MNMOS->{
                        val gate=volts[t.net(p,0)]?:0.0;val drain=volts[t.net(p,1)]?:0.0;val source=volts[t.net(p,2)]?:0.0
                        NmosModel.evaluate(gate,drain,source,p.value,p.mosfetK).current
                    }
                    PartType.QNPN->{
                        val b=volts[t.net(p,0)]?:0.0;val c=volts[t.net(p,1)]?:0.0;val e=volts[t.net(p,2)]?:0.0
                        // For a three-terminal part, the scalar inspector current is collector current.
                        NpnModel.evaluate(b,c,e,p.value,p.transistorIsA,p.transistorReverseBeta,p.temperatureC).collector
                    }
                    PartType.LED->if(v>=p.value)(v-p.value)/12.0 else v*1e-8
                    PartType.V,PartType.AC,PartType.EVCVS,PartType.OPAMP->solution[roots.size+sources.indexOf(p)]
                    PartType.GND->0.0
                }
                if(!i.isFinite())throw IllegalArgumentException("Nonfinite branch current")
                currents[p.uuid]=i
            }
            return Result(true,"OK",t,volts,currents,State(caps,volts,inductors),iterations,time)
        } catch(e:Exception) {return Result(false,e.message?:"Numerical solver failure",topology,time=time)}
    }
}
