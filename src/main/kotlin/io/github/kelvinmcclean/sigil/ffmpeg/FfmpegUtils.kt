package io.github.kelvinmcclean.sigil.ffmpeg

import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Instant

@Component
class FfmpegUtils {

    fun getVideoDimensions(inputFilePath: String, workingDir: File): Pair<Int, Int> {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-v", "error")
        ffmpeg.add("-select_streams", "v:0")
        ffmpeg.add("-show_entries", "stream=width,height")
        ffmpeg.add("-of", "csv=s=x:p=0")
        ffmpeg.add(inputFilePath)
        val dimensions = ffmpeg.runProbe().trim().lineSequence().firstOrNull()
            ?.split('x')
            ?.mapNotNull { it.toIntOrNull() }
        require(dimensions != null && dimensions.size == 2 && dimensions.all { it > 0 }) {
            "Unable to read video dimensions for $inputFilePath"
        }
        return dimensions[0] to dimensions[1]
    }

    fun preprocess(filterGraph: StringBuilder, inputFilePath: String, outputFilePath: String, workingDir: File): Int {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-y")
        ffmpeg.add("-i")
        ffmpeg.add(inputFilePath)
        // Use format filter for robust conversion
        ffmpeg.add("-vf")
        // Give every intermediate the same frame cadence and start timestamps at zero.
        // The concat demuxer copies streams, so differing input frame rates/time bases
        // otherwise cause later clips to freeze or play at the wrong speed.
        ffmpeg.add("format=yuv420p," + filterGraph.toString() + ",fps=30")
        ffmpeg.add("-c:v", "libx264")
        ffmpeg.add("-pix_fmt", "yuv420p") // Add this explicitly to handle HEVC/MOV compatibility
        ffmpeg.add("-c:a", "aac")
        ffmpeg.add("-ar", "48000")
        ffmpeg.add("-r", "30")
        ffmpeg.add("-fps_mode", "cfr")
        ffmpeg.add("-avoid_negative_ts", "make_zero")
        // Explicitly force mapping to ensure we don't carry over unsupported streams.
        // Audio is optional ('?') so that silent clips don't abort the whole render.
        ffmpeg.add("-map", "0:v:0")
        ffmpeg.add("-map", "0:a:0?")
        ffmpeg.add(outputFilePath)
        return ffmpeg.run()
    }

    /**
     * Extracts a date from common video filename patterns.
     * Handles formats like: VID_20261004_WA0035, PXL-20261004-test, IMG_20261004_123456,
     * 2026-10-04_video, 2026/10/04_video, etc.
     * Returns the timestamp at midnight UTC for the extracted date, or null if no date found.
     */
    private fun extractDateFromFilename(filename: String): Long? {
        if (filename.isEmpty()) return null
        
        // Try to find YYYYMMDD pattern (8 consecutive digits)
        val yyyyMmDdPattern = Regex("\\b(\\d{4})(\\d{2})(\\d{2})\\b")
        yyyyMmDdPattern.find(filename)?.let { match ->
            try {
                val year = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val day = match.groupValues[3].toInt()
                val date = LocalDate.of(year, month, day)
                return date.atStartOfDay(ZoneId.of("UTC")).toInstant().epochSecond
            } catch (e: Exception) {
                // Invalid date, try next pattern
            }
        }
        
        // Try to find YYYY-MM-DD or YYYY/MM/DD pattern
        val yyyyMmDdSeparatedPattern = Regex("\\b(\\d{4})[-/](\\d{2})[-/](\\d{2})\\b")
        yyyyMmDdSeparatedPattern.find(filename)?.let { match ->
            try {
                val year = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val day = match.groupValues[3].toInt()
                val date = LocalDate.of(year, month, day)
                return date.atStartOfDay(ZoneId.of("UTC")).toInstant().epochSecond
            } catch (e: Exception) {
                // Invalid date
            }
        }
        
        return null
    }

    fun getTimestamp(inputFilePath: String, workingDir: File, originalFilename: String? = null): Long {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-v", "quiet")
        ffmpeg.add("-show_format")
        ffmpeg.add("-show_streams")
        ffmpeg.add("-of", "flat")
        ffmpeg.add(inputFilePath)
        val output = ffmpeg.runProbe()
        
        // Try to find creation_time in format tags or stream tags
        val creationTimeLine = output.split("\r\n", "\n").firstOrNull { 
            it.contains("creation_time") && it.contains("=")
        }
        
        if (creationTimeLine != null) {
            // Extract the value after the equals sign
            val value = creationTimeLine.split("=", limit = 2).getOrNull(1)?.trim('"')
            if (value != null) {
                return try {
                    Instant.parse(value).epochSeconds
                } catch (e: Exception) {
                    // If parsing fails, try other fallbacks
                    null
                } ?: run {
                    // If creation_time exists but can't be parsed, try filename
                    originalFilename?.let { extractDateFromFilename(it) }
                } ?: run {
                    // Fall back to file modification time
                    val file = File(workingDir, inputFilePath)
                    if (file.exists()) file.lastModified() / 1000 else null
                } ?: 0L
            }
        }
        
        // Try to extract date from original filename
        originalFilename?.let { 
            val filenameDate = extractDateFromFilename(it)
            if (filenameDate != null) {
                return filenameDate
            }
        }
        
        // Fallback: use file modification time
        val file = File(workingDir, inputFilePath)
        if (file.exists()) {
            return file.lastModified() / 1000
        }
        
        // Ultimate fallback: epoch (0)
        return 0L
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

    /**
     * Concatenate multiple files using the concat filter.
     * This is more robust than the concat demuxer when re-encoding is needed.
     */
    fun concatWithFilter(inputFiles: Array<String>, finalOutputFilePath: String, workingDir: File): Int {
        val ffmpeg = Ffmpeg(workingDir)
        ffmpeg.add("-y")
        
        // Add all input files
        for (file in inputFiles) {
            ffmpeg.add("-i", file)
        }
        
        // Build concat filter: [0:v][0:a][1:v][1:a]...concat=n=X:v=1:a=1[v][a]
        val filterGraph = StringBuilder("[0:v][0:a]")
        for (i in 1 until inputFiles.size) {
            filterGraph.append("[$i:v][${i}:a]")
        }
        filterGraph.append("concat=n=${inputFiles.size}:v=1:a=1[v][a]")
        
        ffmpeg.add("-filter_complex", filterGraph.toString())
        ffmpeg.add("-map", "[v]")
        ffmpeg.add("-map", "[a]")
        ffmpeg.add("-c:v", "libx264")
        ffmpeg.add("-crf", "23")
        ffmpeg.add("-preset", "fast")
        ffmpeg.add("-pix_fmt", "yuv420p")
        ffmpeg.add("-c:a", "aac")
        ffmpeg.add("-b:a", "128k")
        ffmpeg.add("-ar", "48000")
        ffmpeg.add(finalOutputFilePath)
        
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
        // Inputs can contain data/metadata streams with no codec (codec=none),
        // which MP4 cannot mux. Keep only the primary video and optional audio.
        ffmpeg.add("-map", "0:v:0")
        ffmpeg.add("-map", "0:a:0?")
        ffmpeg.add("-movflags", "faststart") // This forces moov atom to the front
        ffmpeg.add(outputFilePath)
        return ffmpeg.run()
    }
}
