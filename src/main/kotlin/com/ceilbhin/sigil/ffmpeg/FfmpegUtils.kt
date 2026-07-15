package com.ceilbhin.sigil.ffmpeg

import java.io.File
import java.nio.file.Path


class FfmpegUtils {
    companion object {
        fun preprocess(filterGraph: StringBuilder, inputFilePath: String, outputFilePath: String, workingDir: File) {
            val ffmpeg = Ffmpeg(workingDir)
            ffmpeg.add("-y")
            ffmpeg.add("-i")
            ffmpeg.add(inputFilePath)
            ffmpeg.add("-vf")
            ffmpeg.add(filterGraph.toString())
            ffmpeg.add("-c:v", "libx264")
            ffmpeg.add("-r", "30")
            ffmpeg.add("-c:a", "aac")
            ffmpeg.add("-ar", "48000")
            ffmpeg.add(outputFilePath)
            ffmpeg.run()
        }

        fun stabalize(workingDir: File, inputFilePath: String, trfFilePath: String) {
            val ffmpeg = Ffmpeg(workingDir)
            ffmpeg.add("-y")
            ffmpeg.add("-i")
            ffmpeg.add(inputFilePath)
            ffmpeg.add("-vf")
            ffmpeg.add("vidstabdetect=stepsize=32:shakiness=5:accuracy=10:result=" + trfFilePath)
            ffmpeg.add("-f", "null")
            ffmpeg.add("-")
            ffmpeg.run()
        }

        fun concat(concatFile: Path, finalOutputFilePath: String, workingDir: File): Int {
            val ffmpeg = Ffmpeg(workingDir)
            ffmpeg.add("-y")
            ffmpeg.add("-f")
            ffmpeg.add("-y", "-f", "concat", "-safe", "0", "-i", concatFile.toString(),
                "-c", "copy",
                finalOutputFilePath
            )
            return ffmpeg.run()
        }
    }
}
