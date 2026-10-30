package com.blaze.circuitlab

import org.junit.Assert.*
import org.junit.Test

/** Source-level regression for the offline KiCad resource boundary. */
class KiCadAssetRepositoryTest {
    private val root = "kicad/BlazeBasic.pretty"
    private val originalFootprint = "R_0805.kicad_mod"

    private fun repo(
        names: List<String>? = listOf(originalFootprint),
        resources: Map<String, ByteArray> = mapOf("$root/$originalFootprint" to byteArrayOf(1))
    ) = KiCadAssetRepository(
        listAssets = { path -> if (path == root) names else null },
        readAsset = { path -> resources[path] ?: error("No offline asset: $path") }
    )

    @Test fun boardExportReadsOnlyFootprintsNotPackageMetadata() {
        val loaded=repo(listOf("README.txt","LED_D3.kicad_mod",originalFootprint),
            mapOf("$root/$originalFootprint" to byteArrayOf(1), "$root/LED_D3.kicad_mod" to byteArrayOf(2)))
            .footprints()
        assertEquals(listOf("BlazeBasic.pretty/LED_D3.kicad_mod", "BlazeBasic.pretty/R_0805.kicad_mod"), loaded.keys.toList())
        assertArrayEquals(byteArrayOf(2),loaded.getValue("BlazeBasic.pretty/LED_D3.kicad_mod"))
    }

    @Test fun packageExportPreservesAllOriginalOfflineResources() {
        val bytes=mapOf(
            "kicad/fp-lib-table" to "table".toByteArray(),
            "kicad/sym-lib-table" to "symbols".toByteArray(),
            "kicad/BlazeCircuitLab.kicad_sym" to "definitions".toByteArray(),
            "kicad/README.txt" to "notes".toByteArray(),
            "$root/$originalFootprint" to byteArrayOf(42)
        )
        val archive=repo(resources=bytes).forPackage()
        assertEquals(5,archive.size)
        assertEquals(listOf("fp-lib-table","sym-lib-table","BlazeCircuitLab.kicad_sym","README.txt",
            "BlazeBasic.pretty/R_0805.kicad_mod"),archive.keys.toList())
        assertArrayEquals(byteArrayOf(42),archive.getValue("BlazeBasic.pretty/R_0805.kicad_mod"))
    }

    @Test fun malformedOrEscapingFileNamesFailClosed() {
        for(name in listOf("../R_0805.kicad_mod", "bad\\\\name.kicad_mod", ".hidden.kicad_mod",
            "a..b.kicad_mod", "subfolder/R_0805.kicad_mod")) {
            assertTrue("Unsafe asset allowed: $name",
                runCatching { repo(names=listOf(name)).footprints() }.isFailure)
        }
    }

    @Test fun duplicateOrMissingFootprintsFailClosed() {
        assertTrue(runCatching { repo(names=null).footprints() }.isFailure)
        assertTrue(runCatching { repo(names=emptyList()).footprints() }.isFailure)
        assertTrue(runCatching { repo(names=listOf(originalFootprint,originalFootprint)).footprints() }.isFailure)
    }

    @Test fun emptyOrOversizedOrMissingAssetFailsClosed() {
        assertTrue(runCatching { repo(resources=emptyMap()).footprints() }.isFailure)
        assertTrue(runCatching {
            repo(resources=mapOf("$root/$originalFootprint" to byteArrayOf())).footprints()
        }.isFailure)
        assertTrue(runCatching {
            repo(resources=mapOf("$root/$originalFootprint" to ByteArray(100_001))).footprints()
        }.isFailure)
    }

    @Test fun packageCannotSilentlyOmitRequiredLibraryTable() {
        assertTrue(runCatching { repo().forPackage() }.isFailure)
    }

    @Test fun entireBundledSourceLibraryLoadsWithRelativePaths() {
        val assets=java.io.File("src/main/assets/kicad").takeIf { it.isDirectory }
            ?:java.io.File("app/src/main/assets/kicad")
        val directory=java.io.File(assets,"BlazeBasic.pretty")
        assertTrue(directory.isDirectory)
        val reader=KiCadAssetRepository(
            listAssets = { path -> if (path == root) directory.list()?.toList() else null },
            readAsset = { path -> java.io.File(assets,path.removePrefix("kicad/")).readBytes() }
        )
        val archive=reader.forPackage()
        assertTrue(archive.keys.count { it.startsWith("BlazeBasic.pretty/") } >= 18)
        assertTrue(archive.containsKey("fp-lib-table"))
        assertTrue(archive.containsKey("BlazeBasic.pretty/LED_D3.kicad_mod"))
    }
}
