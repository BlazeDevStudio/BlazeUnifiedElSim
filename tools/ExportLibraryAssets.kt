import com.blaze.circuitlab.core.KiCad
import java.io.File

fun main(){
 val path=File("app/src/main/assets/kicad/BlazeCircuitLab.kicad_sym")
 path.writeText(KiCad.symbolLibrary()+"\n")
 check(KiCad.balanced(path.readText()))
 println("Generated ${path.name} (${path.length()} bytes)")
}
