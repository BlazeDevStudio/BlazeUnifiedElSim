package com.blaze.circuitlab.core

import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Stable physical pad identity. Do not persist topology-assigned integer net indices. */
data class PadKey(val partUuid: String, val number: String)

/** One manually selected, single-layer copper path anchored to two schematic-derived pads.
 *  Bends are in mm; endpoint positions are always resolved from current footprint placements.
 *  This is an approximate clearance model, NOT fabrication-grade DRC or KiCad geometry.
 */
data class BoardRoute(
    val uuid: String = UUID.randomUUID().toString(),
    val start: PadKey,
    val end: PadKey,
    val bends: List<MmPoint> = emptyList(),
    val widthMm: Double = 0.4,
    val layer: String = "F.Cu"
)

object BoardRouting {
    const val APPROX_PAD_RADIUS_MM = 0.35
    const val MIN_CLEARANCE_MM = 0.25
    private const val EPS = 1e-7

    data class Segment(val a: MmPoint, val b: MmPoint, val widthMm: Double, val net: Int)
    data class Routed(val route: BoardRoute, val net: Int, val points: List<MmPoint>)

    private fun same(a:Double,b:Double)=abs(a-b)<EPS
    private fun validNumber(n:Double)=n.isFinite() && abs(n)<1e6
    private fun within(x:Double,lo:Double,hi:Double)=x>=lo-EPS && x<=hi+EPS
    private fun pointDistance(p:MmPoint,a:MmPoint,b:MmPoint):Double {
        val dx=b.x-a.x;val dy=b.y-a.y
        val lengthSq=dx*dx+dy*dy
        if(lengthSq<EPS*EPS)return hypot(p.x-a.x,p.y-a.y)
        val t=(((p.x-a.x)*dx+(p.y-a.y)*dy)/lengthSq).coerceIn(0.0,1.0)
        return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
    }
    private fun distance(a:Segment,b:Segment):Double {
        val av=same(a.a.x,a.b.x);val bv=same(b.a.x,b.b.x)
        if(av!=bv){
            val v=if(av)a else b;val h=if(av)b else a
            if(within(v.a.x,min(h.a.x,h.b.x),max(h.a.x,h.b.x)) &&
                within(h.a.y,min(v.a.y,v.b.y),max(v.a.y,v.b.y)))return 0.0
        }
        return min(min(pointDistance(a.a,b.a,b.b),pointDistance(a.b,b.a,b.b)),
            min(pointDistance(b.a,a.a,a.b),pointDistance(b.b,a.a,a.b)))
    }

    fun resolve(c:Circuit,pads:List<BoardPlanner.Pad>):List<Routed> {
        require(c.board.routes.size<=500) { "PCB route limit (500)" }
        require(c.board.routes.map{it.uuid}.toSet().size==c.board.routes.size){"Duplicate PCB route UUID"}
        val byKey=pads.associateBy{PadKey(it.componentUuid,it.pad)}
        val all=mutableListOf<Routed>()
        for(r in c.board.routes){
            require(r.layer=="F.Cu") { "Unsupported routing layer: ${r.layer}" }
            require(r.widthMm.isFinite() && r.widthMm in 0.2..1.5) { "Unsupported track width" }
            require(r.start!=r.end){"Route must join different pads"}
            require(r.bends.size<=16){"Too many track bends"}
            val a=byKey[r.start] ?: error("Route references missing start pad")
            val z=byKey[r.end] ?: error("Route references missing end pad")
            require(a.net==z.net) { "Route crosses electrical nets: ${a.ref}.${a.pad} to ${z.ref}.${z.pad}" }
            val points=listOf(a.at)+r.bends+z.at
            for(p in points){
                require(validNumber(p.x)&&validNumber(p.y) &&
                    within(p.x,r.widthMm/2,c.board.widthMm-r.widthMm/2) &&
                    within(p.y,r.widthMm/2,c.board.heightMm-r.widthMm/2)) { "Copper outside board outline" }
            }
            for(i in 0 until points.size-1){
                val p=points[i];val q=points[i+1]
                require((same(p.x,q.x) xor same(p.y,q.y))) { "Route segments must be nonzero orthogonal paths" }
            }
            all.add(Routed(r,a.net,points))
        }
        // Only pad-to-pad overlap and track-to-opposite-net proximity are modeled.
        // Full footprint courtyard, copper zones, masks, drills and fab tolerances are not modeled.
        for(i in pads.indices)for(j in 0 until i)if(pads[i].net!=pads[j].net){
            val dist=hypot(pads[i].at.x-pads[j].at.x,pads[i].at.y-pads[j].at.y)
            require(dist+EPS>=2*APPROX_PAD_RADIUS_MM+MIN_CLEARANCE_MM){"Approximate pad clearance violation: ${pads[i].ref}.${pads[i].pad} / ${pads[j].ref}.${pads[j].pad}"}
        }
        val segments=all.flatMap{r->r.points.zipWithNext().map{(a,b)->Segment(a,b,r.route.widthMm,r.net)}}
        for(s in segments){
            for(p in pads)if(p.net!=s.net){
                val d=pointDistance(p.at,s.a,s.b)
                require(d+EPS>=s.widthMm/2+APPROX_PAD_RADIUS_MM+MIN_CLEARANCE_MM){"Track crosses or approaches a different-net pad: ${p.ref}.${p.pad}"}
            }
        }
        for(i in segments.indices)for(j in 0 until i){
            val a=segments[i];val b=segments[j]
            if(a.net!=b.net)require(distance(a,b)+EPS>=
                a.widthMm/2+b.widthMm/2+MIN_CLEARANCE_MM){"Different-net copper traces violate approximate clearance"}
        }
        return all
    }

    /** Manual routing: select two actual physical pads belonging to the same net.
     *  Try straight, horizontal-first then vertical-first paths, rejecting all short risks.
     */
    fun add(c:Circuit,start:PadKey,end:PadKey,widthMm:Double=0.4):BoardRoute {
        BoardPlanner.validate(c)
        val byKey=BoardPlanner.pads(c).associateBy{PadKey(it.componentUuid,it.pad)}
        val a=byKey[start]?:error("Start pad not found")
        val z=byKey[end]?:error("End pad not found")
        require(a.net==z.net) { "Different electrical nets cannot be connected" }
        require(start!=end){"Select two different pads"}
        require(a.at!=z.at){"Pads have coincident centers"}
        val candidates=if(abs(a.at.x-z.at.x)<EPS||abs(a.at.y-z.at.y)<EPS)listOf(emptyList())
        else listOf(listOf(MmPoint(z.at.x,a.at.y)),listOf(MmPoint(a.at.x,z.at.y)))
        var lastError="No clearance-safe orthogonal route"
        for(bends in candidates){
            val route=BoardRoute(start=start,end=end,bends=bends,widthMm=widthMm)
            c.board.routes.add(route)
            val verified=runCatching{BoardPlanner.validate(c)}
            if(verified.isSuccess)return route
            c.board.routes.removeAt(c.board.routes.lastIndex)
            lastError=verified.exceptionOrNull()?.message?:lastError
        }
        throw IllegalArgumentException("Route rejected: $lastError")
    }

    /** Count incomplete pad connections using only actual copper routes, not ratsnest lines.
     *  Segment intersection within the same net is intentionally not credited as electrical
     *  connectivity until routed path/branch-junction editing is explicitly implemented.
     */
    fun residualAirwires(pads:List<BoardPlanner.Pad>,routed:List<Routed>):List<BoardPlanner.Airwire> {
        val ids=pads.map{PadKey(it.componentUuid,it.pad)}
        val parent=ids.associateWith{it}.toMutableMap()
        fun find(key:PadKey):PadKey {
            val p=parent.getValue(key)
            if(p==key)return p
            val r=find(p);parent[key]=r;return r
        }
        fun join(a:PadKey,b:PadKey){val x=find(a);val y=find(b);if(x!=y)parent[y]=x}
        routed.forEach{join(it.route.start,it.route.end)}
        val remain=mutableListOf<BoardPlanner.Airwire>()
        for((net,group) in pads.groupBy{it.net}){
            if(group.size<2)continue
            val covered=mutableSetOf(find(PadKey(group.first().componentUuid,group.first().pad)))
            while(true){
                val candidates=group.filter{find(PadKey(it.componentUuid,it.pad)) in covered}
                    .flatMap{a->group.filter{find(PadKey(it.componentUuid,it.pad)) !in covered}
                        .map{b->BoardPlanner.Airwire(net,a,b,hypot(a.at.x-b.at.x,a.at.y-b.at.y))}}
                if(candidates.isEmpty())break
                val best=candidates.minWith(compareBy<BoardPlanner.Airwire>{it.distanceMm}
                    .thenBy{it.a.ref}.thenBy{it.a.pad}.thenBy{it.b.ref}.thenBy{it.b.pad})
                remain.add(best)
                covered.add(find(PadKey(best.b.componentUuid,best.b.pad)))
            }
        }
        return remain
    }
}
