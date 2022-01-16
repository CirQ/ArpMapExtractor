package me.cirq.arpmap

import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.utils.SourceRoot
import java.io.File
import java.nio.file.Path


fun getSymbolSolver(srcRoot: File): JavaSymbolSolver {
    val solver1 = ReflectionTypeSolver()
    val solver2 = JavaParserTypeSolver(srcRoot)
    val combinedTypeSolver = CombinedTypeSolver(solver1, solver2)
    return JavaSymbolSolver(combinedTypeSolver)
}


object ArpMapExtractor {

    fun extract(sdkVersion: Int, sdkRoots: List<File>, outPath: Path) {
        val methods = mutableListOf<JMethod>()
        for(srcRoot in sdkRoots) {
            require(srcRoot.exists() && srcRoot.isDirectory)
            val sourceRoot = SourceRoot(srcRoot.toPath())
            val symbolSolver = getSymbolSolver(srcRoot)
            sourceRoot.parserConfiguration.setSymbolResolver(symbolSolver)
            for ((pkg, cls) in walkJavaSrc(srcRoot)) {
                println("parsing $sdkVersion: $pkg.$cls")
                val cu = sourceRoot.parse(pkg, cls)
                if("$pkg.$cls" == "com.android.server.notification.NotificationManagerService.java")
                    continue    // skip whole
                cu.accept(AperVisitor(), methods)

                val usedMem = Runtime.getRuntime().run { totalMemory() - freeMemory() }
                val maxMem = Runtime.getRuntime().maxMemory()
                val memUsage = usedMem / maxMem.toFloat()
                if (memUsage > 0.9) {
                    JavaParserFacade.clearInstances()
                    System.gc()
                }
//            if(methods.size > 10)
//                break
            }
        }
        val outDir = outPath.resolve("API$sdkVersion")
        outDir.toFile().mkdirs()
        writeMethod(methods, outDir)
    }

}
