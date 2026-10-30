package com.blaze.circuitlab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.blaze.circuitlab.core.*
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Android Canvas renderer/editor. Absolutely no WebView or HTML runtime. */
class NativeCircuitView(context:Context): View(context) {
    enum class Tool { SELECT, WIRE, JUNCTION, PAN, V, AC, R, C, LED, SW, GND, I, L, D, QNPN, MNMOS, EVCVS, OPAMP }
    var project=Circuit();set(value){field=value;selected=null;pendingWire=null;trace.clear();invalidate()}
    var tool=Tool.SELECT;set(value){field=value;pendingWire=null;invalidate()}
    var selected:String?=null
    /** Selected preset is placed on next canvas tap, not a decorative library button. */
    var libraryPresetId:String?=null
    var measurement:MnaSolver.Result?=null;set(value){field=value;invalidate()}
    var showScope=false;set(value){field=value;invalidate()}
    var running=false
    var onCircuitChanged:(()->Unit)?=null
    var onSelectionChanged:(()->Unit)?=null
    var onUndoSnapshot:((Circuit)->Unit)?=null
    var onInvalidEdit:((String)->Unit)?=null
    val trace=ArrayDeque<Double>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private var zoom=1f
    private var panX=-60f;private var panY=30f
    private var down:Point?=null
    private var previousTouchX=0f;private var previousTouchY=0f
    private var movingPart:String?=null
    private var moved=false
    private var previousCircuit:Circuit?=null
    private var pendingWire:Point?=null
    private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            zoom=(zoom*detector.scaleFactor).coerceIn(.32f,3.5f);invalidate();return true
        }
    })
    fun addTrace(v:Double){if(v.isFinite()){trace.addLast(v);while(trace.size>230)trace.removeFirst()};invalidate()}
    fun fit(){zoom= min(width/900f,height/500f).coerceIn(.36f,2.0f);panX=0f;panY=0f;invalidate()}
    fun restore(c:Circuit){project=c;invalidate()}
    fun refresh(){invalidate()}
    fun selectedPart():Part?=project.parts.find{it.uuid==selected}
    private fun world(x:Float,y:Float):Point=Point(((x-panX)/zoom).roundToInt(),((y-panY)/zoom).roundToInt()).snapped()
    private fun screenDist(a:Point,b:Point):Float = hypot((a.x-b.x).toFloat(),(a.y-b.y).toFloat())*zoom
    private fun terminalAt(p:Point):Point?=project.parts.flatMap{it.pins()}.minByOrNull{screenDist(it,p)}?.takeIf{screenDist(it,p)<22f}
    private fun elementAt(p:Point):Part?=project.parts.lastOrNull{abs(it.x-p.x)<52 && abs(it.y-p.y)<43}
    private fun line(c:Canvas,x1:Float,y1:Float,x2:Float,y2:Float,color:Int,width:Float=2f){
        paint.color=color;paint.strokeWidth=width;paint.style=Paint.Style.STROKE;c.drawLine(x1,y1,x2,y2,paint);paint.style=Paint.Style.FILL
    }
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float=14f,color:Int=Color.WHITE){
        paint.color=color;paint.textSize=size;paint.style=Paint.Style.FILL;c.drawText(s,x,y,paint)
    }
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(8,17,29))
        canvas.save();canvas.translate(panX,panY);canvas.scale(zoom,zoom)
        val dark=Color.rgb(35,52,70);val soft=Color.rgb(135,169,192);val teal=Color.rgb(75,221,196)
        val minX=(((-panX/zoom).toInt()-12)/24)*24
        val maxX=(((width-panX)/zoom).toInt()+24)
        val minY=(((-panY/zoom).toInt()-12)/24)*24
        val maxY=(((height-panY)/zoom).toInt()+24)
        for(x in minX..maxX step 24)line(canvas,x.toFloat(),minY.toFloat(),x.toFloat(),maxY.toFloat(),dark,.6f)
        for(y in minY..maxY step 24)line(canvas,minX.toFloat(),y.toFloat(),maxX.toFloat(),y.toFloat(),dark,.6f)
        for(w in project.wires){
            val net=measurement?.topology?.wireNet?.get(w.uuid)
            val volts=net?.let{measurement?.voltage?.get(it)}
            val c=when{measurement?.ok!=true->soft;volts==null->soft;volts>.1->Color.rgb(240,163,87);volts<-.1->Color.rgb(120,151,252);else->teal}
            line(canvas,w.a.x.toFloat(),w.a.y.toFloat(),w.b.x.toFloat(),w.b.y.toFloat(),c,3f)
        }
        for(j in project.junctions){paint.color=teal;canvas.drawCircle(j.at.x.toFloat(),j.at.y.toFloat(),5f,paint)}
        for(p in project.parts){
            canvas.save();canvas.translate(p.x.toFloat(),p.y.toFloat());canvas.rotate(p.rotation.toFloat())
            val active=p.uuid==selected
            if(active){paint.color=Color.rgb(19,59,68);canvas.drawRoundRect(-55f,-35f,55f,40f,8f,8f,paint)}
            val ink=if(active)teal else Color.rgb(194,223,238)
            when(p.type){
                PartType.L->{line(canvas,-48f,0f,-29f,0f,ink);line(canvas,29f,0f,48f,0f,ink)
                    for(j in 0..3){val center=-21f+j*14f;val path=Path().apply{moveTo(center-7f,0f);lineTo(center-5f,-9f);lineTo(center,-14f);lineTo(center+5f,-9f);lineTo(center+7f,0f)}
                        paint.color=ink;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.4f;canvas.drawPath(path,paint);paint.style=Paint.Style.FILL}}
                PartType.R->{line(canvas,-48f,0f,-28f,0f,ink);line(canvas,28f,0f,48f,0f,ink)
                    val path=Path().apply{moveTo(-28f,0f);for(i in 0..8)lineTo(-28f+i*7f,if(i%2==0)0f else if(i%4==1)-12f else 12f)}
                    paint.color=ink;paint.strokeWidth=2.5f;paint.style=Paint.Style.STROKE;canvas.drawPath(path,paint);paint.style=Paint.Style.FILL}
                PartType.C->{line(canvas,-48f,0f,-7f,0f,ink);line(canvas,7f,0f,48f,0f,ink)
                    line(canvas,-7f,-20f,-7f,20f,ink,3f);line(canvas,7f,-20f,7f,20f,ink,3f)}
                PartType.V,PartType.AC,PartType.I->{line(canvas,-48f,0f,-24f,0f,ink);line(canvas,24f,0f,48f,0f,ink)
                    paint.color=ink;paint.strokeWidth=2.5f;paint.style=Paint.Style.STROKE;canvas.drawCircle(0f,0f,24f,paint);paint.style=Paint.Style.FILL
                    text(canvas,if(p.type==PartType.V)"−  +" else if(p.type==PartType.I)"→" else "∿",-15f,6f,16f,ink)}
                PartType.EVCVS->{
                    paint.color=ink;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.4f
                    canvas.drawRect(-26f,-47f,26f,47f,paint);paint.style=Paint.Style.FILL
                    listOf(-36f,36f).forEach { y ->
                        line(canvas,-48f,y,-26f,y,ink);line(canvas,26f,y,48f,y,ink)
                    }
                    text(canvas,"+",-20f,-27f,17f,ink)
                    text(canvas,"−",-20f,44f,17f,ink)
                    text(canvas,"E",-6f,6f,19f,ink)
                }
                PartType.OPAMP->{
                    val tri=Path().apply { moveTo(-30f,-35f);lineTo(30f,0f);lineTo(-30f,35f);close() }
                    paint.color=ink;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.4f
                    canvas.drawPath(tri,paint);paint.style=Paint.Style.FILL
                    line(canvas,-48f,-36f,-30f,-36f,ink);line(canvas,-48f,36f,-30f,36f,ink)
                    line(canvas,30f,0f,48f,0f,ink);line(canvas,0f,-48f,0f,-20f,ink);line(canvas,0f,20f,0f,48f,ink)
                    text(canvas,"+",-23f,-12f,13f,soft);text(canvas,"−",-23f,17f,13f,soft)
                    text(canvas,"V+",7f,-25f,10f,soft);text(canvas,"V−",7f,31f,10f,soft)
                }
                PartType.MNMOS->{
                    line(canvas,-48f,0f,-25f,0f,ink)
                    line(canvas,-25f,-21f,-25f,21f,ink,3f)
                    line(canvas,-14f,-23f,-14f,23f,ink,3f)
                    line(canvas,-14f,-13f,24f,-36f,ink,3f)
                    line(canvas,-14f,13f,24f,36f,ink,3f)
                    line(canvas,24f,-36f,48f,-36f,ink)
                    line(canvas,24f,36f,48f,36f,ink)
                    text(canvas,"G",-40f,-5f,11f,ink);text(canvas,"D",30f,-44f,11f,ink);text(canvas,"S",30f,51f,11f,ink)
                }
                PartType.QNPN->{
                    line(canvas,-48f,0f,-24f,0f,ink)
                    line(canvas,24f,-36f,48f,-36f,ink)
                    line(canvas,24f,36f,48f,36f,ink)
                    line(canvas,-24f,-22f,-24f,22f,ink,3f)
                    line(canvas,-24f,-12f,24f,-36f,ink,3f)
                    line(canvas,-24f,12f,24f,36f,ink,3f)
                    text(canvas,"B",-38f,-5f,11f,ink);text(canvas,"C",30f,-44f,11f,ink);text(canvas,"E",30f,51f,11f,ink)
                }
                PartType.D->{line(canvas,-48f,0f,-15f,0f,ink);line(canvas,13f,0f,48f,0f,ink)
                    val path=Path().apply{moveTo(-15f,-16f);lineTo(13f,0f);lineTo(-15f,16f);close()}
                    paint.color=ink;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.2f
                    canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
                    line(canvas,13f,-17f,13f,17f,ink,3f)}
                PartType.LED->{line(canvas,-48f,0f,-15f,0f,ink);line(canvas,13f,0f,48f,0f,ink)
                    val path=Path().apply{moveTo(-15f,-16f);lineTo(13f,0f);lineTo(-15f,16f);close()}
                    paint.color=if((measurement?.currentIn(p)?:0.0)>.002)teal else Color.rgb(29,65,81)
                    canvas.drawPath(path,paint);line(canvas,13f,-17f,13f,17f,ink,3f)}
                PartType.SW->{line(canvas,-48f,0f,-22f,0f,ink);line(canvas,22f,0f,48f,0f,ink)
                    line(canvas,-22f,0f,if(p.closed)22f else 18f,if(p.closed)0f else -23f,ink,3f)}
                PartType.GND->{line(canvas,0f,-24f,0f,-9f,ink);line(canvas,-16f,-9f,16f,-9f,ink)
                    line(canvas,-10f,-2f,10f,-2f,ink);line(canvas,-5f,5f,5f,5f,ink)}
            }
            canvas.restore()
            for(pin in p.pins()){paint.color=teal;canvas.drawCircle(pin.x.toFloat(),pin.y.toFloat(),2.8f,paint)}
            text(canvas,p.ref,p.x-16f,p.y+64f,14f,ink)
            if(p.type!=PartType.GND)text(canvas,when(p.type){PartType.R->"${p.value.toInt()} Ω";PartType.C->"${p.value} F";PartType.L->"${p.value} H";PartType.I->"${p.value} A";PartType.SW->if(p.closed)"CLOSED" else "OPEN";PartType.LED->"Vf ${p.value}";PartType.D->"Is ${p.value} A";PartType.QNPN->"β ${p.value.toInt()}";PartType.MNMOS->"Vth ${p.value} V";PartType.EVCVS->"gain ×${p.value}";PartType.OPAMP->"powered ×${p.value}";else->"${p.value} V"},p.x-26f,p.y+82f,11f,soft)
        }
        pendingWire?.let{p->paint.color=teal;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;canvas.drawCircle(p.x.toFloat(),p.y.toFloat(),10f,paint);paint.style=Paint.Style.FILL}
        canvas.restore()
        if(showScope)drawScope(canvas)
    }
    private fun drawScope(c:Canvas){
        val top=height*0.64f;val bottom=height-12f
        paint.color=Color.rgb(16,31,47);c.drawRect(0f,top,width.toFloat(),bottom,paint)
        for(j in 0..4)line(c,0f,top+(bottom-top)*j/4,width.toFloat(),top+(bottom-top)*j/4,Color.rgb(37,59,76),1f)
        text(c,"SCOPE · simulated selected-component voltage",14f,top+17f,12f,Color.rgb(141,199,187))
        if(trace.size<2)return
        val peak=max(1.0,trace.maxOf{abs(it)})
        val data=trace.toList();val mid=top+(bottom-top)*.55f;val half=(bottom-top)*.32f
        for(i in 1 until data.size){val x0=(i-1).toFloat()/229f*width;val x1=i.toFloat()/229f*width
            line(c,x0,mid-(data[i-1]/peak).toFloat()*half,x1,mid-(data[i]/peak).toFloat()*half,Color.rgb(84,233,202),2f)}
    }
    private fun altered() {onCircuitChanged?.invoke();invalidate()}
    override fun onTouchEvent(event:MotionEvent):Boolean {
        scaler.onTouchEvent(event)
        if(event.pointerCount>1){down=null;movingPart=null;return true}
        val p=world(event.x,event.y)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{
                previousTouchX=event.x;previousTouchY=event.y;down=p;moved=false
                movingPart=if(tool==Tool.SELECT)elementAt(p)?.uuid else null
                if(movingPart!=null){selected=movingPart;previousCircuit=project.detached();onSelectionChanged?.invoke()}
                return true
            }
            MotionEvent.ACTION_MOVE->{
                val dx=event.x-previousTouchX;val dy=event.y-previousTouchY
                if(abs(dx)+abs(dy)>2f){
                    if(tool==Tool.PAN){panX+=dx;panY+=dy;moved=true}
                    else if(tool==Tool.SELECT && movingPart!=null){
                        val part=project.parts.find{it.uuid==movingPart}
                        if(part!=null){val start=down?:p;val ddx=p.x-start.x;val ddy=p.y-start.y
                            if(ddx!=0||ddy!=0){
                                val old=part.pins();part.x+=ddx;part.y+=ddy
                                // preserve wires attached at part terminals; orthogonalize at release
                                for(w in project.wires){if(w.a in old)w.a=Point(w.a.x+ddx,w.a.y+ddy);if(w.b in old)w.b=Point(w.b.x+ddx,w.b.y+ddy)}
                                down=p;moved=true
                            }
                        }
                    }
                }
                previousTouchX=event.x;previousTouchY=event.y;invalidate();return true
            }
            MotionEvent.ACTION_UP->{
                if(tool==Tool.SELECT){
                    if(moved&&movingPart!=null){
                        val diagonal=project.wires.filter{it.a.x!=it.b.x && it.a.y!=it.b.y}.toList()
                        for(w in diagonal){project.wires.remove(w);val elbow=Point(w.b.x,w.a.y)
                            project.wires.add(Wire(a=w.a,b=elbow));project.wires.add(Wire(a=elbow,b=w.b))}
                        try{Topology.build(project);previousCircuit?.let{onUndoSnapshot?.invoke(it)};altered()}
                        catch(e:Exception){previousCircuit?.let{project=it};onInvalidEdit?.invoke(e.message?:"Invalid wire move")}
                    }else if(!moved){selected=elementAt(p)?.uuid;onSelectionChanged?.invoke()}
                    movingPart=null;previousCircuit=null
                }else if(tool==Tool.WIRE && !moved){
                    val at=terminalAt(p)?:p
                    if(pendingWire==null)pendingWire=at
                    else{
                        val a=pendingWire!!;pendingWire=null
                        if(a!=at){val before=project.detached()
                            if(a.x==at.x||a.y==at.y)project.wires.add(Wire(a=a,b=at))
                            else{val elbow=Point(at.x,a.y);project.wires.add(Wire(a=a,b=elbow));project.wires.add(Wire(a=elbow,b=at))}
                            try{Topology.build(project);onUndoSnapshot?.invoke(before);altered()}
                            catch(e:Exception){project=before;onInvalidEdit?.invoke(e.message?:"Invalid wiring")}
                        }
                    }
                }else if(tool==Tool.JUNCTION && !moved){
                    if(project.junctions.none{it.at==p}){val before=project.detached();project.junctions.add(Junction(at=p))
                        try{Topology.build(project);onUndoSnapshot?.invoke(before);altered()}catch(e:Exception){project=before;onInvalidEdit?.invoke(e.message?:"Invalid junction")}}
                }else if(tool.name in PartType.values().map{it.name} && !moved){
                    val type=PartType.valueOf(tool.name)
                    val prefix=when(type){PartType.LED->"D";PartType.SW->"S";PartType.GND->"G";PartType.QNPN->"Q";PartType.MNMOS->"M";PartType.EVCVS->"E";PartType.OPAMP->"A";else->type.name}
                    var count=1;while(project.parts.any{it.ref=="$prefix$count"})count++
                    onUndoSnapshot?.invoke(project.detached())
                    val preset=libraryPresetId?.let{ElectronicLibrary.byId(it)}
                    val part=if(preset!=null && preset.simulationType==type)
                        ElectronicLibrary.instantiate(preset.id,p.x,p.y,project.parts)
                        else Part(ref="$prefix$count",type=type,x=p.x,y=p.y)
                    libraryPresetId=null
                    project.parts.add(part);selected=part.uuid;tool=Tool.SELECT;altered();onSelectionChanged?.invoke()
                }
                invalidate();return true
            }
        }
        return true
    }
}
