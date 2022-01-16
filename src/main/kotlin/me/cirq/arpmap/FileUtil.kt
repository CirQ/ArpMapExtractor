package me.cirq.arpmap

import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Path
import java.util.LinkedList


class JavaSrc(private val pkg: String, private val cls: String) {
    operator fun component1() = pkg
    operator fun component2() = cls
}

fun walkJavaSrc(srcRoot: File) = sequence {
    require(srcRoot.isDirectory)

    fun isDesired(srcPath: File, debug: Boolean=false): Boolean {
        if(debug) {
            val pkgPath = srcPath.relativeTo(srcRoot).parent.toPkg()
            val cls = "$pkgPath.${srcPath.nameWithoutExtension}"
            return cls == "android.content.pm.PackageInstaller"
        }
        return true
    }

    srcRoot.walkTopDown()
    .filter {
        it.isFile && it.extension=="java" && isDesired(it)
    }
    .filter {
        val fullCls = it.relativeTo(srcRoot).path.toPkg()
        setOf("android.", "androidx.", "com.android.", "com.google.android")
        .any{ pkg ->
            fullCls.startsWith(pkg)
        }
    }
    .forEach {
        val pkgPath = it.relativeTo(srcRoot).parent.toPkg()
        val clsFile = it.name
        yield( JavaSrc(pkgPath, clsFile) )
    }
}

private fun String.toPkg() = replace(File.separatorChar, '.')


fun writeMethod(methods: List<JMethod>, outDir: Path){
    val byAnnotations = LinkedList<String>()
    val byJavadoc = LinkedList<String>()
    methods.forEach {
        if(it.aPermission.isNotEmpty()) {
            byAnnotations.add(it.joinAPermission())
        }
        if(it.jPermission.isNotEmpty()) {
            byJavadoc.add(it.joinJPermission())
        }
    }
    val aFile = outDir.resolve("annotations-Mappings.txt").toFile()
    FileUtils.writeLines(aFile, byAnnotations)
    val jFile = outDir.resolve("docs-Mappings.txt").toFile()
    FileUtils.writeLines(jFile, byJavadoc)
}

fun writePermission(np: Set<String>, dp: Set<String>, sp: Set<String>, outDir: Path){
    val nFile = outDir.resolve("normal-permissions.txt").toFile()
    FileUtils.writeLines(nFile, np)
    val dFile = outDir.resolve("dangerous-permissions.txt").toFile()
    FileUtils.writeLines(dFile, dp)
    val sFile = outDir.resolve("signature-permissions.txt").toFile()
    FileUtils.writeLines(sFile, sp)
}
