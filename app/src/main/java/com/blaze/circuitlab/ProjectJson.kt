package com.blaze.circuitlab

import com.blaze.circuitlab.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Android Storage Access Framework import/export and private autosave JSON schema. */
object ProjectJson {
    private fun point(p:Point):JSONObject=JSONObject().put("x",p.x).put("y",p.y)
    private fun parsePoint(p:JSONObject)=Point(p.getDouble("x").toInt(),p.getDouble("y").toInt()).snapped()
    private fun mm(p:MmPoint)=JSONObject().put("x",p.x).put("y",p.y)
    private fun parseMm(p:JSONObject)=MmPoint(p.getDouble("x"),p.getDouble("y"))
    private fun pad(k:PadKey)=JSONObject().put("partUuid",k.partUuid).put("number",k.number)
    private fun parsePad(p:JSONObject)=PadKey(p.getString("partUuid"),p.getString("number"))
    fun encode(c:Circuit):String {
        c.validate();BoardPlanner.validate(c)
        val j=JSONObject().put("version",9).put("title",c.title)
        j.put("parts",JSONArray().also{arr->c.parts.forEach{p->arr.put(JSONObject()
            .put("uuid",p.uuid).put("ref",p.ref).put("type",p.type.name).put("x",p.x).put("y",p.y)
            .put("value",p.value).put("frequency",p.frequency).put("closed",p.closed)
            .put("rotation",p.rotation).put("footprint",p.footprint?:"").put("catalogId",p.catalogId?:"")
            .put("diodeIdeality",p.diodeIdeality).put("temperatureC",p.temperatureC)
            .put("transistorIsA",p.transistorIsA).put("transistorReverseBeta",p.transistorReverseBeta).put("mosfetK",p.mosfetK).put("opampHeadroomV",p.opampHeadroomV))}})
        j.put("wires",JSONArray().also{arr->c.wires.forEach{w->arr.put(JSONObject().put("uuid",w.uuid).put("a",point(w.a)).put("b",point(w.b)))}})
        j.put("junctions",JSONArray().also{arr->c.junctions.forEach{e->arr.put(JSONObject().put("uuid",e.uuid).put("at",point(e.at)))}})
        j.put("board", JSONObject().put("widthMm",c.board.widthMm).put("heightMm",c.board.heightMm)
            .put("placements",JSONArray().also{arr->c.board.placements.forEach{p->arr.put(JSONObject()
                .put("partUuid",p.partUuid).put("footprintId",p.footprintId)
                .put("xMm",p.xMm).put("yMm",p.yMm).put("rotation",p.rotation))}})
            .put("routes",JSONArray().also{arr->c.board.routes.forEach{r->arr.put(JSONObject()
                .put("uuid",r.uuid).put("start",pad(r.start)).put("end",pad(r.end))
                .put("widthMm",r.widthMm).put("layer",r.layer)
                .put("bends",JSONArray().also{b->r.bends.forEach{b.put(mm(it))}}))}}))
        c.mcuPlan?.let { plan ->
            j.put("mcuPlan",JSONObject().put("boardId",plan.boardId)
                .put("bindings",JSONArray().also{arr->plan.bindings.forEach{b->arr.put(JSONObject()
                    .put("pin",b.pin).put("partUuid",b.partUuid).put("terminal",b.terminal))}}))
        }
        return j.toString(2)
    }
    private fun JSONArray.each(fn:(JSONObject)->Unit){for(i in 0 until length())fn(getJSONObject(i))}
    private fun stable(source:String)=UUID.nameUUIDFromBytes(source.toByteArray()).toString()
    fun decode(text:String):Circuit {
        require(text.length<2_000_000){"Import exceeds 2MB limit"}
        val json=JSONObject(text)
        val version=json.optInt("version",1)
        require(version in 1..9){"Unsupported version $version"}
        val c=Circuit(title=json.optString("title","Imported Circuit").take(100))
        val parts=if(version==1)json.getJSONArray("components") else json.getJSONArray("parts")
        parts.each { j ->
            val kind=PartType.valueOf(j.getString("type"))
            val ref=j.optString("ref",j.optString("id","?"))
            val uuid=if(version==1)stable("legacy:${ref}:${c.parts.size}") else j.getString("uuid")
            c.parts.add(Part(uuid,ref,kind,j.getInt("x"),j.getInt("y"),j.optDouble("value",Part.defaults(kind)),
                if(version==1)j.optDouble("freq",2.0) else j.optDouble("frequency",2.0),
                j.optBoolean("closed",true),j.optInt("rotation",0),j.optString("footprint","").ifBlank{null},
                j.optString("catalogId","").ifBlank{null},
                j.optDouble("diodeIdeality",1.75),j.optDouble("temperatureC",25.0),
                j.optDouble("transistorIsA",1e-14),j.optDouble("transistorReverseBeta",2.0),j.optDouble("mosfetK",0.002),j.optDouble("opampHeadroomV",0.2)))
        }
        json.getJSONArray("wires").each{j->c.wires.add(Wire(
            if(version==1)stable("legacy-wire:${c.wires.size}") else j.getString("uuid"),
            parsePoint(j.getJSONObject("a")),parsePoint(j.getJSONObject("b"))))}
        if(version>=2)json.optJSONArray("junctions")?.each{j->c.junctions.add(Junction(j.getString("uuid"),parsePoint(j.getJSONObject("at"))))}
        else {
            // Alpha implicitly connected endpoints and ground/pin contacts in wire interiors.
            // Migrate to explicit junctions so old projects retain those connections.
            val allEndpoints=c.wires.flatMap{listOf(it.a,it.b)}
            val pinPts=c.parts.flatMap{it.pins()}
            for(p in (allEndpoints+pinPts).distinct())if(c.wires.any{w-> Topology.onSegment(p,w) && p!=w.a && p!=w.b}){
                c.junctions.add(Junction(stable("legacy-junction:${p.x}:${p.y}"),p))
            }
        }
        if(version>=3){json.optJSONObject("board")?.let{b->
            c.board.widthMm=b.getDouble("widthMm");c.board.heightMm=b.getDouble("heightMm")
            b.optJSONArray("placements")?.each{p->c.board.placements.add(BoardPlacement(
                p.getString("partUuid"),p.getString("footprintId"),p.getDouble("xMm"),
                p.getDouble("yMm"),p.getInt("rotation")))}
            if(version>=4)b.optJSONArray("routes")?.each{r->
                val bendArray=r.getJSONArray("bends")
                val bends=(0 until bendArray.length()).map{parseMm(bendArray.getJSONObject(it))}
                c.board.routes.add(BoardRoute(r.getString("uuid"),parsePad(r.getJSONObject("start")),
                    parsePad(r.getJSONObject("end")),bends,r.getDouble("widthMm"),r.getString("layer")))
            }
        }}
        if(version>=5)json.optJSONObject("mcuPlan")?.let{mp->
            val plan=McuPlan(mp.getString("boardId"))
            mp.optJSONArray("bindings")?.each{b->plan.bindings.add(McuPinBinding(
                b.getString("pin"),b.getString("partUuid"),b.getInt("terminal")))}
            c.mcuPlan=plan
        }
        c.validate();Topology.build(c);BoardPlanner.validate(c)
        return c
    }
}
