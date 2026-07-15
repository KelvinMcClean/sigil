package com.ceilbhin.sigil.ffmpeg

import ws.schild.jave.process.ProcessLocator
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.File

class Ffmpeg(private val workingDir: File) {
    private val ffmpegLocator: ProcessLocator = DefaultFFMPEGLocator()
    val command: MutableList<String?> = ArrayList<String?>()

    fun add(arg: String) {
        command.add(arg)
    }
    fun add(vararg args: String) {
        command.addAll(args)
    }

    fun addAll(args: List<String>) {
        command.addAll(args)
    }

    fun run(): Int {
        val exeLocation = ffmpegLocator.executablePath
        val commandWithExe = listOf(exeLocation) + command
        val renderBuilder = ProcessBuilder(commandWithExe).inheritIO().directory(workingDir)
        val process = renderBuilder.start()
        return process.waitFor()
    }
}