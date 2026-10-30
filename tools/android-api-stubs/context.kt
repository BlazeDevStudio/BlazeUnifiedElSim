package android.content
import android.content.res.AssetManager
import java.io.File
import android.net.Uri
class Resources {val displayMetrics=DisplayMetrics()}
class DisplayMetrics {val density=1f}
open class Context {
    val resources=Resources();val filesDir=File("/tmp")
    val contentResolver=ContentResolver();val assets=AssetManager()
}
class ContentResolver {
    fun openInputStream(uri:Uri):java.io.InputStream?=null
    fun openOutputStream(uri:Uri,mode:String):java.io.OutputStream?=null
}
open class Intent(val action:String){
    var type:String="";val data:Uri?=null
    fun addCategory(s:String):Intent=this
    fun putExtra(s:String,a:String):Intent=this
    companion object {const val ACTION_CREATE_DOCUMENT="";const val ACTION_OPEN_DOCUMENT="";const val CATEGORY_OPENABLE="";const val EXTRA_TITLE=""}
}

