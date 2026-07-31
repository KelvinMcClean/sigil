package io.github.kelvinmcclean.sigil.ffmpeg

import ws.schild.jave.process.ProcessLocator
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import io.github.oshai.kotlinlogging.KotlinLogging

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

        // Log the full command for debugging
        val logger = KotlinLogging.logger {}
        logger.info { "Executing FFmpeg command: ${commandWithExe.joinToString(" ")}" }

        val renderBuilder = ProcessBuilder(commandWithExe).redirectErrorStream(true).directory(workingDir)
        val process = renderBuilder.start()

        // Capture and log error output
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error { "FFmpeg process failed with exit code $exitCode. Output: $output" }
        } else {
            logger.debug { "FFmpeg process output: $output" }
        }

        return exitCode    }
}
