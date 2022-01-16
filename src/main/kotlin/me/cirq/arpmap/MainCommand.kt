package me.cirq.arpmap

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlin.streams.toList


class MainCommand: CliktCommand() {
    private val sdkVersion: Int?
            by option("-v", help="the version of sdk")
                    .int()
    private val sdkPath: Path
            by option("-d", help="the path to sdk source")
                    .path(mustExist=true)
                    .required()
    private val outPath: Path
            by option("-o", help="the path to output directory")
                    .path()
                    .default(Paths.get("arpMappings"))
    private val numWorkers: Int
            by option("-n", help="number of workers")
                    .int()
                    .default(16)
    private val worker: Boolean
            by option("--worker-mode", help="run as worker thread")
                    .flag(default=false)
    private val local: Boolean
            by option("--local-mode", help="run in local machine")
                    .flag(default=false)
    private val extractPLevel: Boolean
            by option("--extract-plevel", help="extract permission by levels, if false, extract dp-mapping")
                    .flag(default=false)


    private fun listJavaSources(path: Path): List<File> {
        return Files.walk(path).filter {
            it.fileName.toString() == "java"
        }.map {
            it.toFile()
        }.toList()
    }

    override fun run() {
        when {
            local -> {
                // for debugging purpose only
                val sdk30Path = sdkPath.resolve("java-android11")
                if(extractPLevel) {
                    val xmlPath =  sdk30Path.resolve("core/res/AndroidManifest.xml")
                    ArpPermExtractor.extract(30, xmlPath.toFile(), outPath)
                }
                else {
                    val srcs = listJavaSources(sdk30Path)
                    ArpMapExtractor.extract(30, srcs, outPath)
                }
            }
            worker -> {
                val srcs = listJavaSources(sdkPath)
                ArpMapExtractor.extract(sdkVersion!!, srcs, outPath)
            }
            else -> {
                val executor = Executors.newFixedThreadPool(numWorkers)
                sdkPath.toFile().listFiles{ file ->
                    file.isDirectory
                }?.forEach { it ->
                    val match = """sources-(\d\d)""".toRegex().find(it.name)
                    val sdkVersion = match!!.groupValues[1].toInt()
                    executor.submit {
                        if(extractPLevel) {
                            val xmlPath =  it.resolve("core/res/AndroidManifest.xml")
                            ArpPermExtractor.extract(sdkVersion, xmlPath, outPath)
                        }
                        else {
                            val srcs = listJavaSources(it.toPath())
                            ArpMapExtractor.extract(sdkVersion, srcs, outPath)
                        }
                    }
                }
                executor.shutdown()
            }
        }
    }
}

fun main(args: Array<String>) = MainCommand().main(args)
