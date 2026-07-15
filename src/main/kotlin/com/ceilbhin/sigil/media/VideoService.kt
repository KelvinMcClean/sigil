package com.ceilbhin.sigil.media

import com.ceilbhin.sigil.batch.VideoJobContext
import com.ceilbhin.sigil.ffmpeg.FfmpegUtils
import com.ceilbhin.sigil.files.FileUtils
import com.ceilbhin.sigil.timestamp.TimestampService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.File

@Service
class VideoService(val timestampService: TimestampService) {

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
            FfmpegUtils.stabalize(workingDir, inputFilePath, trfFilePath)
            // Prepend the stabilization transform to the filtergraph
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
        FfmpegUtils.preprocess(filterGraph, inputFilePath, outputFilePath, workingDir)
        logger.info { "Completed render for file: ${file.name}" }
    }

    fun concat(videoJobContext: VideoJobContext) {
        val workingDir = File(videoJobContext.fileDirectory)
        val files = workingDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") && it.name.startsWith("_processed_")} ?: emptyList()
        val concatFile = FileUtils.createConcatPath(workingDir.toPath(), files.size)
        val finalOutputFilePath = workingDir.toPath().resolve("_final_output.mp4").toString()
        logger.info { "Final output file path: $finalOutputFilePath" }
        // The final concatenation command
        val exitCode = FfmpegUtils.concat(concatFile, finalOutputFilePath, workingDir)
        logger.info { "FFmpeg concatenation process exited with code: $exitCode" }
    }
}