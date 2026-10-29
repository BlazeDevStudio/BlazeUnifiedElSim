package android.widget
import android.content.Context
import android.view.View
import android.view.ViewGroup
open class TextView(c:Context):View(c){var text:CharSequence="";var textSize=0f;fun setTextColor(n:Int){} }
class Button(c:Context):TextView(c){var isAllCaps=false}
class EditText(c:Context):TextView(c){var inputType=0;fun setText(t:String){text=t};fun setSelection(p:Int){};var hint:CharSequence="";fun setSingleLine(value:Boolean){} }
class LinearLayout(c:Context):ViewGroup(c){var orientation=0;class LayoutParams(w:Int,h:Int,weight:Float):ViewGroup.LayoutParams(w,h); companion object {const val HORIZONTAL=0;const val VERTICAL=1}}
class HorizontalScrollView(c:Context):ViewGroup(c){var isHorizontalScrollBarEnabled=true}
class ScrollView(c:Context):ViewGroup(c)
class Toast {fun show(){};companion object {const val LENGTH_LONG=1;fun makeText(c:Context,s:String,d:Int)=Toast()}}
