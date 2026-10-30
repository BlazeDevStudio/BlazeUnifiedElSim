package android.util
import java.io.*
class AtomicFile(f:File){fun readFully():ByteArray=ByteArray(0);fun startWrite():FileOutputStream=FileOutputStream("/tmp/x");fun finishWrite(f:FileOutputStream){};fun failWrite(f:FileOutputStream){} }
