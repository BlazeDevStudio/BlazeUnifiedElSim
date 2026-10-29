package com.blaze.circuitlab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.blaze.circuitlab.core.*
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Native PCB placement and approximate manual single-layer copper routing preview. */
class NativeBoardView(context:Context):View(context){
    var project=Circuit();set(value){field=value;selectedPartUuid=null;routeStart=null;invalidate()}
    var selectedPartUuid:String?=null
    var routeMode:Boolean=false
        set(value){field=value;routeStart=null;invalidate()}
    private var routeStart:PadKey?=null
    var onBoardChanged:((Circuit)->Unit)?=null
    var onSelectionChanged:(()->Unit)?=null
    var onInvalidEdit:((String)->Unit)?=null
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=2f;textSize=13f}
    private var dragBefore:Circuit?=null
    private var dragUid:String?=null
    private var moved=false
    private var scale=1f
    private fun metrics():Triple<Float,Float,Float>{
        val b=project.board
        val s= (minOf((width-48f)/b.widthMm.toFloat(),(height-72f)/b.heightMm.toFloat()).coerceAtLeast(0.1f))*scale
        return Triple(s,(width-b.widthMm.toFloat()*s)/2f,(height-b.heightMm.toFloat()*s)/2f)
    }
    private fun px(point:MmPoint):Pair<Float,Float>{val (s,ox,oy)=metrics();return (ox+point.x.toFloat()*s) to (oy+point.y.toFloat()*s)}
    private fun mm(x:Float,y:Float):MmPoint {val(s,ox,oy)=metrics();return MmPoint(((x-ox)/s).toDouble(),((y-oy)/s).toDouble())}
    private fun segment(canvas:Canvas,a:MmPoint,b:MmPoint,color:Int,w:Float){
        val(x1,y1)=px(a);val(x2,y2)=px(b);ink.color=color;ink.strokeWidth=w;canvas.drawLine(x1,y1,x2,y2,ink)
    }
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(8,17,29))
        val b=project.board
        val tl=px(MmPoint(0.0,0.0));val br=px(MmPoint(b.widthMm,b.heightMm))
        ink.color=Color.rgb(15,41,43);canvas.drawRect(tl.first,tl.second,br.first,br.second,ink)
        ink.color=Color.rgb(75,221,196);ink.style=Paint.Style.STROKE;ink.strokeWidth=3f
        canvas.drawRect(tl.first,tl.second,br.first,br.second,ink);ink.style=Paint.Style.FILL
        val preview=runCatching{BoardPlanner.preview(project)}.getOrElse{
            ink.color=Color.rgb(252,169,103);canvas.drawText("BOARD MODEL ERROR: ${it.message}",10f,22f,ink);return
        }
        val copper=runCatching{BoardRouting.resolve(project,preview.pads)}.getOrDefault(emptyList())
        for(route in copper){
            val (s,_,_)=metrics()
            for((a,z) in route.points.zipWithNext())
                segment(canvas,a,z,Color.rgb(84,205,124),(route.route.widthMm.toFloat()*s).coerceAtLeast(3f))
        }
        for(w in preview.airwires)segment(canvas,w.a.at,w.b.at,Color.rgb(229,167,83),1.5f)
        for(pl in b.placements){
            val part=project.parts.find{it.uuid==pl.partUuid}?:continue
            val (cx,cy)=px(MmPoint(pl.xMm,pl.yMm))
            ink.color=if(pl.partUuid==selectedPartUuid)Color.rgb(32,115,103) else Color.rgb(36,69,84)
            canvas.drawRoundRect(cx-22f,cy-16f,cx+22f,cy+16f,5f,5f,ink)
            ink.color=Color.WHITE;ink.textSize=13f;canvas.drawText(part.ref,cx-13f,cy-22f,ink)
            ink.color=Color.rgb(93,241,189)
            for(pad in preview.pads.filter{it.componentUuid==pl.partUuid}){
                val(px,py)=px(pad.at)
                ink.color=if(routeStart==PadKey(pad.componentUuid,pad.pad))Color.rgb(255,234,110) else Color.rgb(93,241,189)
                canvas.drawCircle(px,py,if(routeStart==PadKey(pad.componentUuid,pad.pad))7f else 4.5f,ink)
            }
        }
        ink.textSize=12f;ink.color=Color.rgb(189,211,225)
        canvas.drawText("PCB placement • ${b.widthMm.toInt()} × ${b.heightMm.toInt()} mm",12f,22f,ink)
        canvas.drawText(if(routeMode)"ROUTE: tap two same-net pads • yellow = first pad" else
            "Green = MANUAL copper • orange = UNROUTED • DRC approximate",12f,height-14f,ink)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(routeMode){
            if(e.actionMasked==MotionEvent.ACTION_DOWN){
                val preview=runCatching{BoardPlanner.preview(project)}.getOrElse{
                    onInvalidEdit?.invoke(it.message?:"Invalid PCB layout");return true
                }
                val hit=preview.pads.minByOrNull{p->val(x,y)=px(p.at);hypot((e.x-x).toDouble(),(e.y-y).toDouble())}
                    ?.takeIf{p->val(x,y)=px(p.at);hypot((e.x-x).toDouble(),(e.y-y).toDouble())<18.0}
                if(hit!=null){
                    val key=PadKey(hit.componentUuid,hit.pad)
                    val start=routeStart
                    if(start==null)routeStart=key
                    else if(start==key)routeStart=null
                    else{
                        val before=project.detached()
                        val result=runCatching{BoardRouting.add(project,start,key)}
                        if(result.isSuccess){routeStart=null;onBoardChanged?.invoke(before)}
                        else onInvalidEdit?.invoke(result.exceptionOrNull()?.message?:"Route rejected")
                    }
                }else routeStart=null
                invalidate()
            }
            return true
        }
        val position=mm(e.x,e.y)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                dragUid=project.board.placements.minByOrNull{hypot(it.xMm-position.x,it.yMm-position.y)}
                    ?.takeIf{hypot(it.xMm-position.x,it.yMm-position.y)<8.0}?.partUuid
                selectedPartUuid=dragUid
                dragBefore=if(dragUid!=null)project.detached() else null
                moved=false;onSelectionChanged?.invoke();invalidate();return true
            }
            MotionEvent.ACTION_MOVE->{
                val pl=project.board.placements.find{it.partUuid==dragUid}
                if(pl!=null){
                    val x=(position.x*2).roundToInt()/2.0;val y=(position.y*2).roundToInt()/2.0
                    if(pl.xMm!=x || pl.yMm!=y){pl.xMm=x;pl.yMm=y;moved=true;invalidate()}
                }
                return true
            }
            MotionEvent.ACTION_UP->{
                val before=dragBefore
                if(moved&&before!=null){
                    val ok=runCatching{BoardPlanner.validate(project)}
                    if(ok.isFailure){project.board.placements.clear();project.board.placements.addAll(before.board.placements.map{it.copy()});onInvalidEdit?.invoke(ok.exceptionOrNull()?.message?:"Invalid footprint position")}
                    else onBoardChanged?.invoke(before)
                }
                dragUid=null;dragBefore=null;moved=false;invalidate();return true
            }
            MotionEvent.ACTION_CANCEL->{dragBefore?.let{project.board.placements.clear();project.board.placements.addAll(it.board.placements.map{p->p.copy()})};dragUid=null;dragBefore=null;moved=false;invalidate();return true}
        }
        return true
    }
}
