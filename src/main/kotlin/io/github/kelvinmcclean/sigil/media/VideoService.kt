package io.github.kelvinmcclean.sigil.media

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.ffmpeg.FfmpegUtils
import io.github.kelvinmcclean.sigil.files.FileService
import io.github.kelvinmcclean.sigil.files.FileUtils
import io.github.kelvinmcclean.sigil.timestamp.TimestampService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.File

@Service
class VideoService(val timestampService: TimestampService, val fileService: FileService, val ffmpegUtils: FfmpegUtils) {

    private final val logger = KotlinLogging.logger {}

    fun preprocess(videoJobContext: VideoJobContext, index: Int) {
        val stabilize = videoJobContext.stabilize
        val workingDir = File(videoJobContext.fileDirectory)
        val file = workingDir.listFiles()?.find { it.isFile && it.name == "_input_${index}.mp4" } ?: return
        logger.info {"Processing file: ${file.name}"}
        val inputFilePath = "_input_$index.mp4"
        val outputFilePath = "_processed_$index.mp4"
        val trfFilePath = "_transforms_$index.trf"
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

        if (videoJobContext.timestamps.isNotEmpty()) {
            logger.info { "Processing timestamps for file: ${file.name}" }
            val timestamps = timestampService.getTimestampFilter(videoJobContext, index)
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
        val files = workingDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") && it.name.startsWith("_processed_")} ?: emptyList()
        val concatFile = FileUtils.createConcatPath(workingDir.toPath(), files.size)
        logger.info { "Final output file path: $finalPath" }
        // The final concatenation command
        val exitCode = ffmpegUtils.concat(concatFile, finalPath, workingDir)
        logger.info { "FFmpeg concatenation process exited with code: $exitCode" }
    }
}
