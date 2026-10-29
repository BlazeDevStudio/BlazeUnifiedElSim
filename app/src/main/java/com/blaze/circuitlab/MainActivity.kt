package com.blaze.circuitlab

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType
import android.widget.Toast
import com.blaze.circuitlab.core.*
import java.io.File
import android.util.AtomicFile
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Native Android, offline. No WebView, network permission, account, or cloud service. */
class MainActivity:Activity() {
    private lateinit var editor:NativeCircuitView
    private lateinit var boardView:NativeBoardView
    private var boardMode=false
    private lateinit var readout:TextView
    private lateinit var inspector:TextView
    private lateinit var playButton:Button
    private var running=false
    private var destroyed=false
    private var time=0.0
    private var revision=0
    private var state=MnaSolver.State()
    private var lastResult:MnaSolver.Result?=null
    private val worker=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private val handler=Handler(Looper.getMainLooper())
    private val undo=ArrayDeque<Circuit>()
    private var exportPayload:String?=null
    private var exportBinary:ByteArray?=null
    private val jsonFile by lazy{File(filesDir,"active-circuit.json")}
    private val atomic by lazy{AtomicFile(jsonFile)}
    private val timer=object:Runnable{
        override fun run(){
            if(destroyed)return
            if(running&&!busy.getAndSet(true)){
                val project=editor.project.detached()
                val savedState=state;val epoch=revision;val nextTime=time+.015
                worker.execute{
                    val result=MnaSolver.step(project,savedState,.015,nextTime)
                    handler.post {
                        if(!destroyed && epoch==revision){
                            lastResult=result;editor.measurement=result
                            if(result.ok){state=result.state;time=nextTime
                                val sel=editor.selectedPart()?:editor.project.parts.firstOrNull{it.type!=PartType.GND}
                                editor.addTrace(sel?.let{result.voltageAcross(it)}?:0.0)
                            }else running=false
                            editor.running=running
                            updateDisplay()
                        }
                        busy.set(false)
                    }
                }
            }
            handler.postDelayed(this,55)
        }
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(8,17,29)
        window.navigationBarColor=Color.rgb(8,17,29)
        val libraries = runCatching { OfflineCatalogs.install(assets) }
        if(libraries.isFailure) {
            val message = libraries.exceptionOrNull()?.message ?: "Unknown catalog failure"
            setContentView(label("Offline library validation failed: $message\n\nCircuit editing is disabled to protect project mappings. Reinstall a verified build.",16f))
            return
        }
        buildScreen()
        val loaded=runCatching{if(jsonFile.exists())ProjectJson.decode(atomic.readFully().toString(Charsets.UTF_8)) else DemoCircuits.led()}
            .getOrElse { DemoCircuits.led().also{show("Autosave could not load; original file retained")} }
        editor.project=loaded;boardView.project=loaded;editor.selected=loaded.parts.firstOrNull{it.type==PartType.LED}?.uuid
        simulateOnce()
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun btn(label:String,onClick:()->Unit):Button=Button(this).apply{
        text=label;textSize=12f;isAllCaps=false;minHeight=dp(45)
        setOnClickListener{onClick()}
    }
    private fun label(text:String,size:Float=13f):TextView=TextView(this).apply{
        this.text=text;setTextColor(Color.rgb(215,235,248));textSize=size
        setPadding(dp(10),dp(7),dp(10),dp(7))
    }
    private fun makeStrip():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    private fun scrollStrip(vararg views:View):View=HorizontalScrollView(this).apply{
        isHorizontalScrollBarEnabled=false
        addView(makeStrip().apply{views.forEach{addView(it)}})
    }
    private fun buildScreen(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(8,17,29))}
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(0,insets.systemWindowInsetTop,0,insets.systemWindowInsetBottom)
            insets
        }
        root.addView(label("ϟ  BLAZE CIRCUITLAB   •   NATIVE ANDROID  •   v0.3.0 • Offline Catalog v2",16f))
        playButton=btn("▶ Run"){running=!running;updateDisplay()}
        root.addView(scrollStrip(playButton,btn("Libraries"){browsePartsLibrary()},
            btn("Step"){running=false;simulateOnce();updateDisplay()},
            btn("Reset"){running=false;state=MnaSolver.State();time=0.0;editor.trace.clear();simulateOnce()},
            btn("Circuit"){editor.showScope=false},btn("Lab: Scope"){editor.showScope=true},
            btn("Fit"){editor.fit()},btn("Undo"){undoEdit()},btn("New"){newCircuit()}))
        val palette=mutableListOf<View>()
        val names=listOf("Select" to NativeCircuitView.Tool.SELECT,"Pan" to NativeCircuitView.Tool.PAN,
            "Wire" to NativeCircuitView.Tool.WIRE,"Junction" to NativeCircuitView.Tool.JUNCTION,
            "DC" to NativeCircuitView.Tool.V,"AC" to NativeCircuitView.Tool.AC,"R" to NativeCircuitView.Tool.R,
            "C" to NativeCircuitView.Tool.C,"L" to NativeCircuitView.Tool.L,"Current" to NativeCircuitView.Tool.I,"LED" to NativeCircuitView.Tool.LED,"Diode" to NativeCircuitView.Tool.D,"NPN" to NativeCircuitView.Tool.QNPN,"NMOS" to NativeCircuitView.Tool.MNMOS,"Amplifier (E)" to NativeCircuitView.Tool.EVCVS,"Powered Amp" to NativeCircuitView.Tool.OPAMP,
            "Switch" to NativeCircuitView.Tool.SW,"GND" to NativeCircuitView.Tool.GND)
        names.forEach{(name,mode)->palette.add(btn(name){editor.libraryPresetId=null;editor.tool=mode;show("Tool: $name")})}
        palette.add(btn("Parts Library"){browsePartsLibrary()})
        palette.add(btn("MCU Library"){browseMcuLibrary()})
        palette.add(btn("Footprints"){browseFootprintLibrary()})
        root.addView(scrollStrip(*palette.toTypedArray()))
        root.addView(scrollStrip(btn("Schematic"){setMode(false)}, btn("PCB Placement"){setMode(true);boardView.routeMode=false},
            btn("Assign footprint"){assignFootprint()},btn("Rotate PCB part"){rotateBoardSelection()},
            btn("Board size"){changeBoardSize()},
            btn("Route pads"){setMode(true);boardView.routeMode=true;show("Tap two pads from the same electrical net")},
            btn("Move PCB"){setMode(true);boardView.routeMode=false},
            btn("Remove last route"){removeLastRoute()}))
        editor=NativeCircuitView(this)
        root.addView(editor,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        boardView=NativeBoardView(this)
        boardView.visibility=View.GONE
        root.addView(boardView,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        readout=label("Solver ready",12f)
        root.addView(readout)
        inspector=label("Select a component for measurements",12f)
        root.addView(inspector)
        root.addView(scrollStrip(
            btn("Edit value"){changeValue()},btn("Diode parameters"){changeDiodeModel()},btn("NPN model"){changeNpnModel()},btn("NMOS model"){changeNmosModel()},btn("Op-Amp headroom"){changeOpampModel()},btn("Toggle switch"){toggleSwitch()},
            btn("Rotate 90°"){rotateSelection()},btn("Delete"){deleteSelection()},
            btn("LED demo"){install(DemoCircuits.led())},btn("RC demo"){install(DemoCircuits.rc())},
            btn("Switch demo"){install(DemoCircuits.switched())},
            btn("RL demo"){install(DemoCircuits.rl())},btn("Current demo"){install(DemoCircuits.currentSource())},
            btn("Diode demo"){install(DemoCircuits.diode())},btn("NPN demo"){install(DemoCircuits.npnSwitch())},btn("NMOS demo"){install(DemoCircuits.nmosSwitch())},btn("Amplifier demo"){install(DemoCircuits.controlledAmplifier())},btn("Powered Amp demo"){install(DemoCircuits.poweredAmplifier())}))
        root.addView(scrollStrip(
            btn("Save JSON"){exportJson()},btn("Import JSON"){importJson()},
            btn("Export KiCad"){exportKiCad()},btn("SPICE .cir"){exportSpice()},btn("KiCad ZIP + Library"){exportKiCadPackage()},
            btn("MCU Pin CSV"){exportMcuPinCsv()},btn("BOM CSV"){exportBom()},btn("Project info"){showProjectInfo()}))
        editor.onCircuitChanged={edited()}
        editor.onSelectionChanged={updateDisplay()}
        editor.onUndoSnapshot={c->snapshot(c)}
        editor.onInvalidEdit={msg->show("Connection rejected: $msg")}
        boardView.onBoardChanged={before->snapshot(before);edited()}
        boardView.onSelectionChanged={updateDisplay()}
        boardView.onInvalidEdit={msg->show("PCB edit rejected: $msg")}

    }
    private fun show(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun snapshot(c:Circuit=editor.project.detached()){undo.addLast(c);if(undo.size>50)undo.removeFirst()}
    private fun setMode(pcb:Boolean){
        boardMode=pcb;editor.visibility=if(pcb)View.GONE else View.VISIBLE
        boardView.visibility=if(pcb)View.VISIBLE else View.GONE
        if(pcb){running=false;boardView.project=editor.project}
        updateDisplay()
    }
    private fun assignFootprint(){
        val selected=if(boardMode)boardView.selectedPartUuid else editor.selected
        val part=editor.project.parts.find{it.uuid==selected}
        if(part==null){
            val eligible=editor.project.parts.filter{FootprintCatalog.forPart(it).isNotEmpty()}
            if(eligible.isEmpty())return show("No supported physical components in schematic")
            val items=eligible.map{"${it.ref} (${it.type})"}.toTypedArray()
            AlertDialog.Builder(this).setTitle("Select schematic component")
                .setItems(items){_,index->pickFootprint(eligible[index])}.show()
        }else pickFootprint(part)
    }
    private fun pickFootprint(p:Part){
        val choices=FootprintCatalog.forPart(p)
        if(choices.isEmpty())return show("${p.ref} is not supported as a physical PCB part; sources require connector conversion")
        AlertDialog.Builder(this).setTitle("${p.ref}: assign verified pin relationship")
            .setItems(choices.map{it.title}.toTypedArray()){_,index->
                applyFootprint(p,choices[index])
            }.show()
    }
    /** Searchable native, offline catalog. Entries resolve to real solver-supported types. */
    private fun browsePartsLibrary(){
        val search=EditText(this).apply{hint="Search resistor, 470, capacitor, LED, source...";setSingleLine(true)}
        AlertDialog.Builder(this).setTitle("Offline Electronics · ${ElectronicLibrary.presets.size} loaded")
            .setMessage("Presets use fourteen model-backed component types, including idealized diodes and NPN BJTs. No MCU firmware executes; manufacturing footprints require datasheet checks.")
            .setView(search).setPositiveButton("Find"){_,_->
                val matches=ElectronicLibrary.search(search.text.toString()).take(150)
                if(matches.isEmpty())show("No matching supported parts") else
                    AlertDialog.Builder(this).setTitle("Library · ${matches.size} results")
                        .setItems(matches.map{"${it.title}  · ${it.group}"}.toTypedArray()){_,index->
                            val preset=matches[index]
                            editor.libraryPresetId=preset.id
                            editor.tool=NativeCircuitView.Tool.valueOf(preset.simulationType.name)
                            setMode(false)
                            show("${preset.title}: tap canvas to place · ${if(preset.approximateModel)"approximate model" else "solver-backed"}")
                        }.setNegativeButton("Cancel",null).show()
            }.setNegativeButton("Cancel",null).show()
    }
    private fun browseFootprintLibrary(){
        val search=EditText(this).apply{hint="Search 0805, LED, resistor, disc...";setSingleLine(true)}
        AlertDialog.Builder(this).setTitle("Bundled footprint library · ${FootprintCatalog.available.count{it.libraryId.startsWith("BlazeBasic:")}} native files")
            .setMessage("BlazeBasic files are packaged locally and exported in the KiCad ZIP. Dimensions are reference geometry—check the actual datasheet before manufacture.")
            .setView(search).setPositiveButton("Find"){_,_->
                val matches=FootprintCatalog.available.filter{
                    it.libraryId.contains(search.text.toString(),true)||it.title.contains(search.text.toString(),true)}.take(100)
                if(matches.isEmpty())show("No matching footprint") else
                    AlertDialog.Builder(this).setTitle("Footprints (${matches.size})")
                        .setItems(matches.map{"${it.libraryId} · ${it.title}"}.toTypedArray()){_,index->
                            val tmpl=matches[index]
                            val selected=(if(boardMode)boardView.selectedPartUuid else editor.selected)
                            val part=editor.project.parts.find{it.uuid==selected}
                            if(part==null)show("${tmpl.title} · ${tmpl.padOffsets.size} physical pads. Select a compatible schematic part to assign.")
                            else if(part.type !in tmpl.supported)show("${part.ref}: footprint ${tmpl.libraryId} incompatible with ${part.type}")
                            else applyFootprint(part,tmpl)
                        }.setNegativeButton("Close",null).show()
            }.setNegativeButton("Cancel",null).show()
    }
    private fun applyFootprint(p:Part,tmpl:FootprintTemplate){
        val before=editor.project.detached()
        val result=runCatching{BoardPlanner.assign(editor.project,p.uuid,tmpl.libraryId)}
        if(result.isFailure){editor.project=before;boardView.project=before;show(result.exceptionOrNull()?.message?:"Footprint rejected")}
        else {snapshot(before);boardView.project=editor.project;boardView.selectedPartUuid=p.uuid;edited();setMode(true)}
    }
    private fun browseMcuLibrary(){
        val boards=McuLibrary.boards
        AlertDialog.Builder(this).setTitle("Arduino / ESP MCU Pin Library")
            .setMessage("Board pinouts and electrical planning only. Compiled firmware, peripheral buses and CPU instructions are NOT executed yet.")
            .setItems(boards.map{"${it.title} · ${it.pins.size} named pins"}.toTypedArray()){_,index->selectMcuBoard(boards[index])}
            .setNegativeButton("Close",null).show()
    }
    private fun selectMcuBoard(board:McuBoard){
        val active=editor.project.mcuPlan
        if(active?.boardId==board.id){browseMcuPins(board);return}
        AlertDialog.Builder(this).setTitle(board.title)
            .setMessage("${board.description}\n\nSelect as this project's MCU pin-planning board? Existing MCU bindings will be replaced. This does NOT add an electrical simulation model.")
            .setPositiveButton("Use board"){_,_->
                val before=editor.project.detached()
                editor.project.mcuPlan=McuPlan(board.id)
                snapshot(before);edited();browseMcuPins(board)
            }.setNegativeButton("View pins only"){_,_->browseMcuPins(board)}.show()
    }
    private fun browseMcuPins(board:McuBoard){
        val search=EditText(this).apply{hint="GPIO, PWM, UART, I2C, ADC, D13...";setSingleLine(true)}
        AlertDialog.Builder(this).setTitle("${board.title} · ${board.logicVoltage} V GPIO")
            .setMessage("Search pins. Select a pin to inspect or bind it to the selected schematic component terminal as a DESIGN NOTE, not a simulated wire.")
            .setView(search).setPositiveButton("Find pins"){_,_->
                val matches=McuLibrary.searchPins(board.id,search.text.toString())
                if(matches.isEmpty())show("No matching board pin") else
                    AlertDialog.Builder(this).setTitle("${board.title} · pins")
                        .setItems(matches.map{"${it.name} · ${it.functions.joinToString("/")}${if(it.caution.isNotBlank())" ⚠" else ""}"}.toTypedArray())
                        {_,index->mcuPinDetails(board,matches[index])}.setNegativeButton("Close",null).show()
            }.setNegativeButton("Cancel",null).show()
    }
    private fun mcuPinDetails(board:McuBoard,pin:McuPin){
        val p=editor.selectedPart()
        val active=editor.project.mcuPlan?.boardId==board.id
        val b=AlertDialog.Builder(this).setTitle("${board.title}: ${pin.name}")
            .setMessage("Functions: ${pin.functions.joinToString(", ")}\nRole: ${pin.kind}\nLogic voltage: ${board.logicVoltage} V\n${pin.caution}\n\nReference: ${board.source}\n\nMCU planner bindings are NOT solver connections.")
            .setNegativeButton("Close",null)
        if(active && p!=null && pin.kind in setOf("GPIO","INPUT_ONLY")){
            b.setPositiveButton("Bind selected ${p.ref}"){_,_->
                AlertDialog.Builder(this).setTitle("${pin.name} → ${p.ref} terminal")
                    .setItems(p.pins().indices.map{"Terminal $it"}.toTypedArray()){_,index->
                        val before=editor.project.detached()
                        val attempt=runCatching{McuLibrary.bind(editor.project,pin.name,p.uuid,index)}
                        if(attempt.isFailure)show(attempt.exceptionOrNull()?.message?:"Pin assignment rejected")
                        else{snapshot(before);edited();show("Saved MCU pin planning annotation; no GPIO emulation")}
                    }.show()
            }
        }
        b.show()
    }
    private fun exportBom(){
        val data=runCatching{Bom.export(editor.project)}.getOrElse{return show("BOM: ${it.message}")}
        exportBinary=data.toByteArray(Charsets.UTF_8)
        chooseSave("text/csv","${safeName()}-bom.csv",107)
    }
    private fun exportMcuPinCsv(){
        val data=runCatching{McuLibrary.csv(editor.project)}.getOrElse{return show("MCU pin export: ${it.message}")}
        exportBinary=data.toByteArray(Charsets.UTF_8)
        chooseSave("text/csv","${safeName()}-mcu-pin-plan.csv",104)
    }
    private fun exportKiCadPackage(){
        val bytes=runCatching{
            val resource="kicad/BlazeBasic.pretty"
            val files=mutableMapOf<String,ByteArray>()
            files["fp-lib-table"]=assets.open("kicad/fp-lib-table").use{it.readBytes()}
            files["sym-lib-table"]=assets.open("kicad/sym-lib-table").use{it.readBytes()}
            files["BlazeCircuitLab.kicad_sym"]=assets.open("kicad/BlazeCircuitLab.kicad_sym").use{it.readBytes()}
            files["README.txt"]=assets.open("kicad/README.txt").use{it.readBytes()}
            for(name in assets.list(resource)?:emptyArray())if(name.endsWith(".kicad_mod")){
                files["BlazeBasic.pretty/$name"]=assets.open("$resource/$name").use{it.readBytes()}
            }
            KiCadPackage.pack(editor.project,files)
        }.getOrElse{return show("KiCad ZIP export rejected: ${it.message}")}
        exportBinary=bytes
        chooseSave("application/zip","${safeName()}-kicad-library.zip",105)
    }
    private fun removeLastRoute(){
        if(!boardMode)return show("Switch to PCB workspace first")
        val routes=editor.project.board.routes
        if(routes.isEmpty())return show("No copper routes to remove")
        val before=editor.project.detached()
        routes.removeAt(routes.lastIndex)
        snapshot(before);edited()
    }
    private fun rotateBoardSelection(){
        if(!boardMode)return show("Switch to PCB Placement first")
        val pl=editor.project.board.placements.find{it.partUuid==boardView.selectedPartUuid}?:return show("Tap a PCB footprint first")
        val before=editor.project.detached();pl.rotation=(pl.rotation+90)%360
        val result=runCatching{BoardPlanner.validate(editor.project)}
        if(result.isFailure){editor.project=before;boardView.project=before;show(result.exceptionOrNull()?.message?:"Rotation rejected")}
        else{snapshot(before);edited()}
    }
    private fun changeBoardSize(){
        val b=editor.project.board
        val input=EditText(this).apply{setText("${b.widthMm.toInt()} x ${b.heightMm.toInt()}")}
        AlertDialog.Builder(this).setTitle("Board width x height (mm), 10–500")
            .setView(input).setPositiveButton("Apply"){_,_->
                val dims=input.text.toString().lowercase().split("x").map{it.trim().toDoubleOrNull()}
                if(dims.size!=2||dims.any{it==null})show("Use 70 x 50")
                else{val before=editor.project.detached();b.widthMm=dims[0]!!;b.heightMm=dims[1]!!
                    val result=runCatching{BoardPlanner.validate(editor.project)}
                    if(result.isFailure){editor.project=before;boardView.project=before;show(result.exceptionOrNull()?.message?:"Invalid board size")}
                    else{snapshot(before);edited()}
                }
            }.setNegativeButton("Cancel",null).show()
    }
    private fun undoEdit(){if(undo.isEmpty()){show("Nothing to undo");return};editor.project=undo.removeLast();boardView.project=editor.project;edited()}
    private fun install(c:Circuit){snapshot();editor.project=c;boardView.project=c;edited()}
    private fun newCircuit(){install(Circuit())}
    private fun edited(){
        val accepted=runCatching { editor.project.validate();Topology.build(editor.project);BoardPlanner.validate(editor.project) }
        if(accepted.isFailure){
            if(undo.isNotEmpty()){
                editor.project=undo.removeLast();boardView.project=editor.project
            }
            show("Edit rejected: ${accepted.exceptionOrNull()?.message?:"Invalid project"}")
            updateDisplay();return
        }
        revision++;state=MnaSolver.State();lastResult=null;editor.trace.clear();time=0.0
        saveAutosave();simulateOnce();boardView.invalidate();updateDisplay()
    }
    private fun saveAutosave(){
        var output:java.io.FileOutputStream?=null
        try {
            val bytes=ProjectJson.encode(editor.project).toByteArray(Charsets.UTF_8)
            output=atomic.startWrite()
            output.write(bytes)
            atomic.finishWrite(output)
            output=null
        } catch(e:Exception) {
            if(output!=null)atomic.failWrite(output)
            show("Autosave failed: ${e.message}; export a backup")
        }
    }
    private fun simulateOnce(){
        val result=MnaSolver.step(editor.project.detached(),state,.015,time)
        lastResult=result;editor.measurement=result
        if(result.ok)state=result.state
        updateDisplay()
    }
    private fun updateDisplay(){
        if(!::editor.isInitialized)return
        playButton.text=if(running)"Ⅱ Pause" else "▶ Run"
        val r=lastResult
        readout.text=if(r==null)"Waiting for simulator" else if(!r.ok)"Solver: ${r.message}"
            else "MNA · ${r.iterations} iterations · t=${String.format(Locale.US,"%.2f",time)} s · ${editor.project.parts.size} components"
        val p=editor.selectedPart()
        if(boardMode){
            val pr=runCatching{BoardPlanner.preview(editor.project)}.getOrNull()
            inspector.text="PCB: ${editor.project.board.placements.size} footprints · ${editor.project.board.routes.size} manual F.Cu routes · ${pr?.airwires?.size?:0} remaining airwires · approximate clearance checks only"
            editor.invalidate();boardView.invalidate();return
        }
        inspector.text=if(p==null)"No component selected · Offline catalog v2 loaded · ${ElectronicLibrary.presets.size} electronics · ${FootprintCatalog.available.size} footprints · ${McuLibrary.boards.size} MCU pinouts (planning only)"
            else {
                val v=r?.voltageAcross(p);val i=r?.currentIn(p)
                "${p.ref} (${p.type}) · Value ${p.value} · V=${v?.let{String.format(Locale.US,"%.3f",it)}?:"—"} V · I=${i?.let{String.format(Locale.US,"%.6f",it)}?:"—"} A"
            }
        editor.invalidate()
    }
    private fun changeValue(){
        val p=editor.selectedPart()?:return show("Select a component first")
        if(p.type==PartType.GND||p.type==PartType.SW)return show("This component has no editable numeric value")
        val input=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(p.value.toString());setSelection(text.length)}
        AlertDialog.Builder(this).setTitle("${p.ref} · ${p.type} value")
            .setView(input).setPositiveButton("Apply"){_,_->
                val v=input.text.toString().toDoubleOrNull()
                if(v==null||!v.isFinite()||(p.type in setOf(PartType.R,PartType.C,PartType.L,PartType.LED,PartType.D,PartType.QNPN,PartType.MNMOS,PartType.EVCVS,PartType.OPAMP)&&v<=0.0))show("Invalid electrical parameter")
                else {
                    val old=p.value
                    p.value=v
                    val valid=runCatching{editor.project.validate()}
                    if(valid.isFailure){p.value=old;show(valid.exceptionOrNull()?.message?:"Invalid value")}
                    else {p.value=old;snapshot();p.value=v;edited()}
                }
            }.setNegativeButton("Cancel",null).show()
    }
    private fun changeDiodeModel(){
        val p=editor.selectedPart()?:return show("Select a diode first")
        if(p.type!=PartType.D)return show("Select a Shockley diode (D) to edit the junction model")
        val field=EditText(this).apply{inputType=InputType.TYPE_CLASS_TEXT
            setText("${p.diodeIdeality},${p.temperatureC}")}
        AlertDialog.Builder(this).setTitle("${p.ref} model: ideality n, temperature °C")
            .setMessage("Enter two numbers separated by comma. Is (A) is edited with Edit value. Temperature changes thermal voltage; Is remains fixed, not manufacturer-calibrated.")
            .setView(field).setPositiveButton("Apply"){_,_->
                val vals=field.text.toString().split(',').map{it.trim().toDoubleOrNull()}
                if(vals.size!=2 || vals.any{it==null})return@setPositiveButton show("Enter n and °C separated by comma")
                val oldN=p.diodeIdeality;val oldT=p.temperatureC
                p.diodeIdeality=vals[0]!!;p.temperatureC=vals[1]!!
                val status=runCatching{editor.project.validate()}
                p.diodeIdeality=oldN;p.temperatureC=oldT
                if(status.isFailure)show(status.exceptionOrNull()?.message?:"Invalid diode model")
                else {snapshot();p.diodeIdeality=vals[0]!!;p.temperatureC=vals[1]!!;edited()}
            }.setNegativeButton("Cancel",null).show()
    }
    private fun changeNpnModel(){
        val part=editor.selectedPart()?:return show("Select an NPN transistor first")
        if(part.type!=PartType.QNPN)return show("Select an NPN transistor (Q) first")
        val field=EditText(this).apply{inputType=InputType.TYPE_CLASS_TEXT
            setText("${part.transistorIsA},${part.transistorReverseBeta},${part.temperatureC}")}
        AlertDialog.Builder(this).setTitle("${part.ref} NPN model: Is(A), reverse beta, temperature(C)")
            .setMessage("Forward beta is edited with Edit value. Educational Ebers-Moll model only; no Early effect, charge storage, self-heating or verified physical part.")
            .setView(field).setPositiveButton("Apply"){_,_->
                val entries=field.text.toString().split(',').map{it.trim().toDoubleOrNull()}
                if(entries.size!=3||entries.any{it==null})return@setPositiveButton show("Enter Is, reverse beta, temperature separated by commas")
                val before=editor.project.detached()
                part.transistorIsA=entries[0]!!;part.transistorReverseBeta=entries[1]!!;part.temperatureC=entries[2]!!
                val status=runCatching{editor.project.validate()}
                if(status.isFailure){editor.project=before;boardView.project=before;show(status.exceptionOrNull()?.message?:"Invalid NPN model")}
                else{snapshot(before);edited()}
            }.setNegativeButton("Cancel",null).show()
    }
    private fun changeNmosModel(){
        val part=editor.selectedPart()?:return show("Select an NMOS transistor first")
        if(part.type!=PartType.MNMOS)return show("Select a MOSFET (M) first")
        val field=EditText(this).apply{inputType=InputType.TYPE_CLASS_TEXT;setText(part.mosfetK.toString())}
        AlertDialog.Builder(this).setTitle("${part.ref} NMOS channel K (A/V²)")
            .setMessage("Threshold voltage is set with Edit value. This is an idealized symmetric square-law channel, NOT a real device: no body diode, intrinsic capacitance, thermal limits or leakage.")
            .setView(field).setPositiveButton("Apply"){_,_->
                val v=field.text.toString().trim().toDoubleOrNull()
                if(v==null||!v.isFinite())return@setPositiveButton show("Enter a finite K in A/V²")
                val before=editor.project.detached()
                part.mosfetK=v
                val status=runCatching{editor.project.validate()}
                if(status.isFailure){editor.project=before;boardView.project=before;show(status.exceptionOrNull()?.message?:"Invalid NMOS K")}
                else{snapshot(before);edited()}
            }.setNegativeButton("Cancel",null).show()
    }
    private fun changeOpampModel(){
        val part=editor.selectedPart()?:return show("Select a powered op-amp first")
        if(part.type!=PartType.OPAMP)return show("Select an OPAMP (A) first")
        val field=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(part.opampHeadroomV.toString())}
        AlertDialog.Builder(this).setTitle("${part.ref} rail headroom (V)")
            .setMessage("Gain is edited with Edit value. This is a basic rail-clamped source with infinite input impedance; no slew, bandwidth, output current limit or supply current model.")
            .setView(field).setPositiveButton("Apply"){_,_->
                val newValue=field.text.toString().trim().toDoubleOrNull()
                if(newValue==null||!newValue.isFinite())return@setPositiveButton show("Invalid rail headroom")
                val before=editor.project.detached()
                part.opampHeadroomV=newValue
                val status=runCatching{editor.project.validate()}
                if(status.isFailure){editor.project=before;boardView.project=before;show(status.exceptionOrNull()?.message?:"Invalid headroom")}
                else{snapshot(before);edited()}
            }.setNegativeButton("Cancel",null).show()
    }
    private fun toggleSwitch(){
        val p=editor.selectedPart()?:return show("Select a switch first")
        if(p.type!=PartType.SW)return show("Selected component is not a switch")
        snapshot();p.closed=!p.closed;edited()
    }
    private fun rotateSelection(){
        val p=editor.selectedPart()?:return show("Select a component first")
        val old=editor.project.detached()
        val previousPins=p.pins();p.rotation=(p.rotation+90)%360
        val newPins=p.pins()
        // Re-anchor original wire endpoints at the same physical pins after rotation.
        for(w in editor.project.wires){for(i in previousPins.indices){if(w.a==previousPins[i])w.a=newPins[i]
            if(w.b==previousPins[i])w.b=newPins[i]}}
        // Stay fail-closed until arbitrary multi-segment re-routing is validated.
        if(editor.project.wires.any{it.a.x!=it.b.x && it.a.y!=it.b.y}){
            editor.project=old;show("Rotation would create diagonal wires; disconnect or reroute first");return
        }
        try{Topology.build(editor.project);snapshot(old);edited()}
        catch(e:Exception){editor.project=old;show("Rotation rejected: ${e.message}")}
    }
    private fun deleteSelection(){
        val p=editor.selectedPart()?:return show("Select a component first")
        snapshot();editor.project.parts.remove(p);editor.project.board.placements.removeAll{it.partUuid==p.uuid}
        editor.project.mcuPlan?.bindings?.removeAll{it.partUuid==p.uuid}
        editor.project.board.routes.removeAll{it.start.partUuid==p.uuid||it.end.partUuid==p.uuid}
        editor.selected=null;edited()
    }
    private fun exportJson(){exportPayload=runCatching{ProjectJson.encode(editor.project)}.getOrElse{return show("Export failed: ${it.message}")}
        chooseSave("application/json","${safeName()}.json",101)}
    private fun exportSpice(){
        val result=runCatching{Spice.export(editor.project)}.getOrElse{return show("SPICE export rejected: ${it.message}")}
        exportPayload=result.netlist
        AlertDialog.Builder(this).setTitle("Experimental SPICE netlist")
            .setMessage(result.warnings.joinToString("\n"))
            .setPositiveButton("Export .cir"){_,_->chooseSave("text/plain","${safeName()}.cir",106)}
            .setNegativeButton("Cancel",null).show()
    }
    private fun exportKiCad(){
        val exported=runCatching{KiCad.export(editor.project)}.getOrElse{return show("KiCad export rejected: ${it.message}")}
        exportPayload=exported.schematic
        AlertDialog.Builder(this).setTitle("Experimental KiCad 9 schematic")
            .setMessage(exported.warnings.joinToString("\n"))
            .setPositiveButton("Export .kicad_sch"){_,_->chooseSave("application/octet-stream","${safeName()}.kicad_sch",102)}
            .setNegativeButton("Cancel",null).show()
    }
    private fun safeName()=editor.project.title.replace(Regex("[^a-zA-Z0-9_-]"),"_").take(54).ifEmpty{"Circuit"}
    private fun chooseSave(type:String,name:String,req:Int){
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);this.type=type;putExtra(Intent.EXTRA_TITLE,name)}
        startActivityForResult(intent,req)
    }
    private fun importJson(){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"}
        startActivityForResult(intent,103)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK)return
        val uri:Uri=data?.data?:return
        try{
            if(requestCode==103){
                val json=contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use{reader->
                    val out=StringBuilder();val chunk=CharArray(4096)
                    while(true){val n=reader.read(chunk);if(n<0)break
                        if(out.length+n>2_000_000)throw IllegalArgumentException("File exceeds import size limit")
                        out.append(chunk,0,n)}
                    out.toString()
                }
                val parsed=ProjectJson.decode(json)
                install(parsed);show("Circuit imported")
            } else if(requestCode==104||requestCode==105||requestCode==107){
                val payload=exportBinary?:return
                contentResolver.openOutputStream(uri,"wt")!!.use{stream->stream.write(payload);stream.flush()}
                show("Exported ${payload.size} bytes through Android file picker")
            } else if(requestCode==101||requestCode==102||requestCode==106){
                val payload=exportPayload?:return
                contentResolver.openOutputStream(uri,"wt")!!.use{stream->stream.write(payload.toByteArray(Charsets.UTF_8));stream.flush()}
                show("Saved through Android file picker")
            }
        }catch(e:Exception){show("File operation failed: ${e.message}")}
        finally{exportPayload=null;exportBinary=null}
    }
    private fun showProjectInfo(){AlertDialog.Builder(this).setTitle("Blaze CircuitLab · Native foundation")
        .setMessage("Implemented: Android Canvas editor, DC/AC voltage sources, dependent VCVS amplifier, DC current source, R, C, ideal L, approximate LED, switch, explicit junctions, MNA, oscilloscope, offline autosave, JSON, experimental KiCad schematic and SPICE linear netlist export.\n\nPCB workspace includes approximate manual pad-to-pad copper routing; no fabrication-grade DRC, vias, Gerber or KiCad PCB validation. Not implemented: real MCU emulation or professional SPICE-grade accuracy.\n\nOffline. Bundled: electronics presets, original BlazeBasic KiCad footprints, Arduino/ESP pin planning and CSV. Firmware emulation not implemented.\n\nOffline. No account. No WebView.")
        .setPositiveButton("OK",null).show()}
    override fun onResume(){super.onResume();handler.removeCallbacks(timer);if(::editor.isInitialized)handler.post(timer)}
    override fun onPause(){running=false;handler.removeCallbacks(timer);if(::editor.isInitialized)saveAutosave();super.onPause()}
    override fun onDestroy(){destroyed=true;handler.removeCallbacks(timer);worker.shutdownNow();super.onDestroy()}
}
