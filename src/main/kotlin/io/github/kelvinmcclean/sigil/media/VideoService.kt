package io.github.kelvinmcclean.sigil.media

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.ffmpeg.FfmpegUtils
import io.github.kelvinmcclean.sigil.files.FileService
import io.github.kelvinmcclean.sigil.files.FileUtils
import io.github.kelvinmcclean.sigil.timestamp.TimestampService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path

@Service
class VideoService(val timestampService: TimestampService, val fileService: FileService, val ffmpegUtils: FfmpegUtils) {

    private final val logger = KotlinLogging.logger {}

    fun preprocess(videoJobContext: VideoJobContext, filename: String) {
        val stabilize = videoJobContext.stabilize
        val workingDir = File(videoJobContext.fileDirectory)
        val file = workingDir.listFiles()?.find { it.isFile && it.name == filename } ?: return
        logger.info { "Processing file: ${file.name}" }
        val inputFilePath = filename
        val outputFilePath = "processed_${filename}"
        val trfFilePath = "transforms_${filename.replace(".mp4", ".trf")}"
        // Initialize the base filter string
        val filterGraph = StringBuilder()

        // If stabilization is requested, run the detection pass and add the transform filter
        if (stabilize) {
            ffmpegUtils.stabalize(workingDir, inputFilePath, trfFilePath)
            // Prepend the stabilization transform to the filtergraph
            // The format=yuv420p is now handled at the start of the chain in FfmpegUtils
            filterGraph.append("vidstabtransform=input=").append(trfFilePath)
                .append(":zoom=0:smoothing=10,")
            logger.info { "Stabilization transform added to filtergraph for file: ${file.name}" }
        }
        // Add the rest of the standard filters (scaling, padding, and text)
        filterGraph.append("scale=1920:1080:force_original_aspect_ratio=decrease,")
            .append("pad=1920:1080:(ow-iw)/2:(oh-ih)/2,")
            .append("setsar=1")

        if (videoJobContext.filenameToTimestamp.isNotEmpty()) {
            logger.info { "Processing timestamps for file: ${file.name}" }
            val timestamps = timestampService.getTimestampFilter(videoJobContext, filename)
            filterGraph.append(timestamps)
        }

        logger.debug { "Final filtergraph for file ${file.name}: $filterGraph" }

        logger.debug { "Running FFmpeg command for file ${file.name}: ffmpeg -y -i $inputFilePath -vf $filterGraph -c:v libx264 -r 30 -c:a aac -ar 48000 $outputFilePath" }
        // Run the render pass with the dynamically built filtergraph
        logger.info { "Processing render for file: ${file.name}" }
        var exitCode = ffmpegUtils.preprocess(filterGraph, inputFilePath, outputFilePath, workingDir)

        if (exitCode != 0) {
            logger.warn { "Initial processing failed for ${file.name}, attempting repair..." }
            val repairedPath = "repaired_$inputFilePath"
            val repairExitCode = ffmpegUtils.repair(inputFilePath, repairedPath, workingDir)

            if (repairExitCode == 0) {
                logger.info { "Repair successful, retrying processing..." }
                ffmpegUtils.preprocess(filterGraph, repairedPath, outputFilePath, workingDir)
            } else {
                logger.error { "Repair failed for file ${file.name}" }
            }
        }
        logger.info { "Completed render for file: ${file.name}" }
    }

    fun concat(videoJobContext: VideoJobContext) {
        val finalPath = fileService.getFinalPath()
        val workingDir = File(videoJobContext.fileDirectory)
        val files = workingDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") && it.name.startsWith("processed_") }?.toList() ?: emptyList()
        logger.info { "Final output file path: $finalPath" }
        
        // Check if we have all expected processed files
        val expectedCount = videoJobContext.fileCount.toInt()
        if (files.size < expectedCount) {
            logger.error { "Missing processed files: expected $expectedCount, found ${files.size}" }
            val missingFiles = videoJobContext.filenameToTimestamp.keys.filter { filename ->
                files.none { it.name == "processed_$filename" }
            }
            logger.error { "Missing files: ${missingFiles.joinToString(", ")}" }
            throw IllegalStateException("Missing processed files: expected $expectedCount, found ${files.size}. Missing: ${missingFiles.joinToString(", ")}")
        }
        
        // Sort files by timestamp for correct ordering
        val sortedFiles = files.sortedBy { file ->
            val originalName = file.name.removePrefix("processed_")
            videoJobContext.filenameToTimestamp[originalName] ?: Long.MAX_VALUE
        }
        
        // Create the file list for concat demuxer
        val fileListPath = workingDir.resolve("_fileList.txt")
        fileListPath.writeText(sortedFiles.joinToString("\n") { "file '${it.name}'" })
        
        // Use concat demuxer
        val exitCode = ffmpegUtils.concat(fileListPath.toPath(), finalPath, workingDir)
        logger.info { "FFmpeg concatenation process exited with code: $exitCode" }
    }
}
