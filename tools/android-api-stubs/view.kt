package android.view
import android.content.Context
import android.graphics.Canvas
class WindowInsets {val systemWindowInsetTop=0;val systemWindowInsetBottom=0}
open class View(val context:Context){
    var width=900;var height=600;var minHeight=0;var visibility=VISIBLE
    companion object {const val VISIBLE=0; const val GONE=8}
    open fun onDraw(canvas:Canvas){}
    open fun onTouchEvent(event:MotionEvent):Boolean=true
    fun setBackgroundColor(color:Int){}
    fun setPadding(a:Int,b:Int,c:Int,d:Int){}
    fun setOnApplyWindowInsetsListener(l:(View,WindowInsets)->WindowInsets){}
    fun setOnClickListener(f:(View)->Unit){}
    fun invalidate(){}
}
open class ViewGroup(context:Context):View(context){
    open class LayoutParams(val width:Int,val height:Int){ companion object {const val MATCH_PARENT=-1} }
    fun addView(v:View){}
    fun addView(v:View,p:LayoutParams){}
}
class MotionEvent {
    val pointerCount=1;val x=0f;val y=0f;val actionMasked=0
    companion object {const val ACTION_DOWN=0;const val ACTION_MOVE=1;const val ACTION_UP=2;const val ACTION_CANCEL=3}
}
class ScaleGestureDetector(ctx:Context,listener:SimpleOnScaleGestureListener){
    val scaleFactor=1f;fun onTouchEvent(event:MotionEvent){}
    open class SimpleOnScaleGestureListener {open fun onScale(detector:ScaleGestureDetector):Boolean=true}
}
object Gravity
