package com.ceilbhin.sigil.ffmpeg

import ws.schild.jave.process.ProcessLocator
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.File

class Ffmpeg(private val workingDir: File) {
    val command: MutableList<String?> = ArrayList<String?>()

    private fun getFfmpegPath(): String {
        // 1. Try system-installed ffmpeg first (required for Docker)
        try {
            // Check if ffmpeg is in the PATH
            val os = System.getProperty("os.name").lowercase()
            val command = if (os.contains("win")) listOf("cmd", "/c", "ffmpeg -version") else listOf("ffmpeg", "-version")
            val process = ProcessBuilder(command).start()
            if (process.waitFor() == 0) return "ffmpeg"
        } catch (e: Exception) {
            // Fall through to JAVE locator
        }

        // 2. Fallback to JAVE's bundled binary
        return DefaultFFMPEGLocator().executablePath
    }

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
        val exeLocation = getFfmpegPath()
        val commandWithExe = listOf(exeLocation) + command.filterNotNull()
        val renderBuilder = ProcessBuilder(commandWithExe).inheritIO().directory(workingDir)
        val process = renderBuilder.start()
        return process.waitFor()
    }
}
