package android.graphics
object Color {const val WHITE=1; fun rgb(r:Int,g:Int,b:Int)=0}
open class Paint(flags:Int=0){
    enum class Cap {ROUND};enum class Join {ROUND};enum class Style { STROKE,FILL }
    var strokeCap=Cap.ROUND;var strokeJoin=Join.ROUND;var color=0;var strokeWidth=1f;var style=Style.FILL;var textSize=1f
    companion object {const val ANTI_ALIAS_FLAG=1}
}
class Path { fun moveTo(x:Float,y:Float){};fun lineTo(x:Float,y:Float){};fun close(){} }
class Canvas {
    fun drawColor(c:Int){};fun save(){};fun restore(){};fun translate(x:Float,y:Float){}
    fun scale(x:Float,y:Float){};fun rotate(n:Float){}
    fun drawLine(a:Float,b:Float,c:Float,d:Float,p:Paint){}
    fun drawCircle(a:Float,b:Float,c:Float,p:Paint){}
    fun drawRect(a:Float,b:Float,c:Float,d:Float,p:Paint){}
    fun drawRoundRect(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float,p:Paint){}
    fun drawText(s:String,x:Float,y:Float,p:Paint){}
    fun drawPath(path:Path,p:Paint){}
}
