package com.blaze.circuitlab.core

/** One graph is consumed by renderer, MNA solver, probes and native KiCad serializer. */
object Topology {
    data class NetGraph(
        val pinNet: Map<Pair<String,Int>,Int>,
        val wireNet: Map<String,Int>,
        val groundNet: Int?,
        val nets: List<Set<String>>,
        val warnings: List<String>
    ) {
        fun net(part: Part,pin: Int): Int = pinNet[part.uuid to pin] ?: error("Missing pin")
    }
    private class DSU {
        val parent=mutableMapOf<String,String>()
        fun add(id:String) { parent.putIfAbsent(id,id) }
        fun root(id:String):String { val p=parent[id]?:error("Missing $id"); if(p==id)return id;val r=root(p);parent[id]=r;return r }
        fun join(a:String,b:String) {val x=root(a);val y=root(b);if(x!=y)parent[if(x<y)y else x]=if(x<y)x else y}
    }
    fun onSegment(p:Point,w:Wire):Boolean =
        if(w.a.x==w.b.x) p.x==w.a.x && p.y in minOf(w.a.y,w.b.y)..maxOf(w.a.y,w.b.y)
        else p.y==w.a.y && p.x in minOf(w.a.x,w.b.x)..maxOf(w.a.x,w.b.x)
    private fun interior(p:Point,w:Wire)=onSegment(p,w)&&p!=w.a&&p!=w.b
    private fun crossing(a:Wire,b:Wire):Point? {
        if((a.a.x==a.b.x)==(b.a.x==b.b.x))return null
        val v=if(a.a.x==a.b.x)a else b
        val h=if(v===a)b else a
        val p=Point(v.a.x,h.a.y)
        return if(onSegment(p,v)&&onSegment(p,h))p else null
    }
    private fun overlap(a:Wire,b:Wire):Boolean {
        if(a.a.x==a.b.x && b.a.x==b.b.x && a.a.x==b.a.x)
            return minOf(maxOf(a.a.y,a.b.y),maxOf(b.a.y,b.b.y))>maxOf(minOf(a.a.y,a.b.y),minOf(b.a.y,b.b.y))
        if(a.a.y==a.b.y && b.a.y==b.b.y && a.a.y==b.a.y)
            return minOf(maxOf(a.a.x,a.b.x),maxOf(b.a.x,b.b.x))>maxOf(minOf(a.a.x,a.b.x),minOf(b.a.x,b.b.x))
        return false
    }
    fun build(circuit:Circuit):NetGraph {
        circuit.validate()
        val dsu=DSU()
        val pins=circuit.parts.flatMap { p -> p.pins().mapIndexed { i,at -> Triple("p:${p.uuid}:$i",p,at) } }
        val pIndex=pins.mapIndexed { _,tr -> val parts=tr.first.split(':');(parts[1] to parts[2].toInt()) to tr.first }.toMap()
        pins.forEach{dsu.add(it.first)}
        circuit.wires.forEach{dsu.add("w:${it.uuid}")}
        val junctions=circuit.junctions.map{it.at}.toSet()
        for(i in pins.indices)for(j in 0 until i)if(pins[i].third==pins[j].third)dsu.join(pins[i].first,pins[j].first)
        for(w in circuit.wires){
            val wid="w:${w.uuid}"
            for((id,part,p) in pins) if(onSegment(p,w)){
                if(interior(p,w) && p !in junctions) throw IllegalArgumentException("${part.ref} pin touches wire interior without explicit junction at $p")
                dsu.join(wid,id)
            }
        }
        for(i in circuit.wires.indices) for(j in 0 until i){
            val a=circuit.wires[i];val b=circuit.wires[j]
            if(overlap(a,b))throw IllegalArgumentException("Overlapping wire segments: canonicalize before solving")
            val intersection=crossing(a,b)
            if(intersection!=null){
                val inA=interior(intersection,a);val inB=interior(intersection,b)
                if(intersection in junctions || (!inA&&!inB)) dsu.join("w:${a.uuid}","w:${b.uuid}")
                else if(inA != inB)throw IllegalArgumentException("Unmarked T-junction at $intersection")
            } else if(listOf(a.a,a.b).any{it==b.a||it==b.b})dsu.join("w:${a.uuid}","w:${b.uuid}")
        }
        for(j in circuit.junctions){
            val touch=circuit.wires.filter{onSegment(j.at,it)}.map{"w:${it.uuid}"} + pins.filter{it.third==j.at}.map{it.first}
            require(touch.isNotEmpty()){ "Orphan junction at ${j.at}" }
            touch.drop(1).forEach{dsu.join(touch[0],it)}
        }
        val grounds=circuit.parts.filter{it.type==PartType.GND}.map{"p:${it.uuid}:0"}
        grounds.drop(1).forEach{dsu.join(grounds[0],it)}
        val groups=dsu.parent.keys.groupBy{dsu.root(it)}.values.map{it.toSet()}.sortedBy{it.minOrNull()}
        val netByMember=groups.withIndex().flatMap{(i,m)->m.map{it to i}}.toMap()
        val pinNet=pIndex.mapValues{(_,id)->netByMember.getValue(id)}
        val wireNet=circuit.wires.associate{it.uuid to netByMember.getValue("w:${it.uuid}")}
        return NetGraph(pinNet,wireNet,grounds.firstOrNull()?.let{netByMember.getValue(it)},groups,emptyList())
    }
}
