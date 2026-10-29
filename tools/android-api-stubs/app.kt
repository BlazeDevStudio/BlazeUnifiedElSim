package android.app
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
class Window {var statusBarColor=0;var navigationBarColor=0}
open class Activity:Context(){
    val window=Window()
    open fun onCreate(b:Bundle?){}
    open fun onResume(){}
    open fun onPause(){}
    open fun onDestroy(){}
    open fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){}
    fun setContentView(view:View){}
    fun startActivityForResult(intent:Intent,req:Int){}
    companion object {const val RESULT_OK=-1}
}
class AlertDialog {
    class Builder(context:Context){
        fun setTitle(s:String):Builder=this
        fun setMessage(s:String):Builder=this
        fun setView(v:View):Builder=this
        fun setItems(items:Array<String>, f:(Any?,Int)->Unit):Builder=this
        fun setPositiveButton(s:String,f:((Any?,Any?)->Unit)?):Builder=this
        fun setNegativeButton(s:String,f:((Any?,Any?)->Unit)?):Builder=this
        fun show():AlertDialog=AlertDialog()
    }
}
