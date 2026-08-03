package io.github.kelvinmcclean.sigil.ffmpeg

import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Path

@Component
class FfmpegUtils {

    fun preprocess(filterGraph: StringBuilder, inputFilePath: String, outputFilePath: String, workingDir: File): Int {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-y")
        ffmpeg.add("-i")
        ffmpeg.add(inputFilePath)
        // Use format filter for robust conversion
        ffmpeg.add("-vf")
        ffmpeg.add("format=yuv420p," + filterGraph.toString())
        ffmpeg.add("-c:v", "libx264")
        ffmpeg.add("-pix_fmt", "yuv420p") // Add this explicitly to handle HEVC/MOV compatibility
        ffmpeg.add("-c:a", "aac")
        ffmpeg.add("-ar", "48000")
        // Explicitly force mapping to ensure we don't carry over unsupported streams.
        // Audio is optional ('?') so that silent clips don't abort the whole render.
        ffmpeg.add("-map", "0:v:0")
        ffmpeg.add("-map", "0:a:0?")
        ffmpeg.add(outputFilePath)
        return ffmpeg.run()
    }

    fun stabalize(workingDir: File, inputFilePath: String, trfFilePath: String) {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-y")
        ffmpeg.add("-i")
        ffmpeg.add(inputFilePath)
        // Use format filter for robust conversion during stabilization
        ffmpeg.add("-vf")
        ffmpeg.add("format=yuv420p,vidstabdetect=stepsize=32:shakiness=5:accuracy=10:result=" + trfFilePath)
        ffmpeg.add("-f", "null")
        ffmpeg.add("-")
        ffmpeg.run()
    }

    fun repair(inputFilePath: String, outputFilePath: String, workingDir: File): Int {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-y")
        ffmpeg.add("-i", inputFilePath)
        ffmpeg.add("-c", "copy") // Just copy the streams, don't re-encode
        ffmpeg.add("-map", "0")
        ffmpeg.add("-movflags", "faststart") // This forces moov atom to the front
        ffmpeg.add(outputFilePath)
        return ffmpeg.run()
    }

    fun concat(concatFile: Path, finalOutputFilePath: String, workingDir: File): Int {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-y")
        ffmpeg.add("-f", "concat", "-safe", "0", "-i", concatFile.toString(),
            "-c", "copy",
            finalOutputFilePath
        )
        return ffmpeg.run()
    }
}
