package com.ceilbhin.sigil.service

import com.ceilbhin.sigil.font.FontResolver
import com.ceilbhin.sigil.rest.status.StatusEnum
import com.ceilbhin.sigil.rest.status.StatusMapper
import com.ceilbhin.sigil.rest.status.StatusTracker
import com.ceilbhin.sigil.util.FfmpegUtils
import com.ceilbhin.sigil.util.FileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Paths


@Service
class VideoService(val statusTracker: StatusTracker) {

    private final val logger = KotlinLogging.logger {}

    @Async
    @Throws(IOException::class)
    fun processVideoJobAsync(jobId: String, files: Array<MultipartFile>, timestamps: Array<Long>?, stabilize: Boolean) {
        val statusMapper = StatusMapper(jobId, statusTracker)
        statusMapper.setStatus(StatusEnum.IN_PROGRESS, "Initializing")
        try {
            val tmpDir = FileUtils.getTmpDir(jobId)
            val tmpPath = Paths.get(tmpDir)
            val workingDir = tmpPath.toFile()
            logger.info {"Starting video processing job: $jobId"}

            preprocess(jobId, files, timestamps, stabilize, workingDir)
            concat(jobId, files, workingDir, statusMapper)

        } catch (e: Exception) {
            statusMapper.setStatus(StatusEnum.FAILED, error = e)
            val tmpDir = FileUtils.getTmpDir(jobId)
            logger.error(e) { "Error processing concatenation" }
            FileUtils.cleanupTempFiles(tmpDir, jobId, files.size, false);
        }
    }

    private fun concat(jobId: String, files: Array<MultipartFile>, workingDir: File, statusMapper: StatusMapper) {

        val concatFile = FileUtils.createConcatPath(workingDir.toPath(), jobId, files.size)
        val finalOutputFilePath = workingDir.toPath().resolve( jobId + "_final_output.mp4").toString()
        logger.info { "Final output file path: $finalOutputFilePath" }
        // The final concatenation command
        val exitCode = FfmpegUtils.concat(concatFile, finalOutputFilePath, statusMapper, workingDir)
        logger.info { "FFmpeg concatenation process exited with code: $exitCode" }

        if (exitCode == 0) {
            // Success! Proceed to make the file available for download and trigger cleanup
            completeJob(workingDir.toPath().toString(), jobId, finalOutputFilePath, files.size)
            statusMapper.setStatus(StatusEnum.COMPLETED, message = "Video processing completed successfully. Output: ${finalOutputFilePath.replace("\\\\", "\\")}")
        } else {
            statusMapper.setStatus(StatusEnum.FAILED, message = "FFmpeg concatenation failed", error = RuntimeException("FFmpeg concatenation failed with exit code: $exitCode"))
            throw RuntimeException("FFmpeg concatenation failed with exit code: $exitCode")
        }
    }

    private fun preprocess(jobId: String, files: Array<MultipartFile>, timestamps: Array<Long>?, stabilize: Boolean, workingDir: File) {
        for ((i, file) in files.withIndex()) {
            logger.info {"Processing file: ${file.originalFilename}"}
            val inputFilePath = jobId + "_input_" + i + ".mp4"
            val outputFilePath = jobId + "_processed_" + i + ".mp4"
            val trfFilePath = jobId + "_transforms_" + i + ".trf"
            // Initialize the base filter string
            val filterGraph = StringBuilder()

            // If stabilization is requested, run the detection pass and add the transform filter
            if (stabilize) {
                FfmpegUtils.stabalize(workingDir, inputFilePath, trfFilePath)
                // Prepend the stabilization transform to the filtergraph
                filterGraph.append("vidstabtransform=input=").append(trfFilePath)
                    .append(":zoom=0:smoothing=10,")
                logger.info { "Stabilization transform added to filtergraph for file: ${file.originalFilename}" }
            }
            // Add the rest of the standard filters (scaling, padding, and text)
            filterGraph.append("scale=1920:1080:force_original_aspect_ratio=decrease,")
                .append("pad=1920:1080:(ow-iw)/2:(oh-ih)/2,")
                .append("setsar=1")

            if (timestamps != null) {
                val fontOption: String? = FontResolver().resolveFont(workingDir.toPath())

                val timestamps = processTimestamps(file.originalFilename, timestamps[i], fontOption)
                filterGraph.append(timestamps)
            }

            logger.debug { "Final filtergraph for file ${file.originalFilename}: $filterGraph" }

            logger.debug { "Running FFmpeg command for file ${file.originalFilename}: ffmpeg -y -i $inputFilePath -vf $filterGraph -c:v libx264 -r 30 -c:a aac -ar 48000 $outputFilePath" }
            // Run the render pass with the dynamically built filtergraph

            logger.info { "Processing render for file: ${file.originalFilename}" }
            FfmpegUtils.preprocess(filterGraph, inputFilePath, outputFilePath, workingDir)
            logger.info { "Completed render for file: ${file.originalFilename}" }
        }
    }

    private fun completeJob(tmpDir: String, jobId: String, finalOutputPath: String, length: Int) {
        FileUtils.cleanupTempFiles(tmpDir, jobId, length, false)
        logger.info { "Completed job: $jobId" }
        logger.info { "Final output: ${finalOutputPath.replace("\\\\", "\\")}" }
    }

    fun processTimestamps(originalFilename: String?, timestamp: Long, fontOption: String?): String {
        logger.info { "Processing timestamps for file: ${originalFilename}" }
        return StringBuilder().append(",drawtext=fontfile=$fontOption: text='%{pts\\:localtime\\:")
            .append(timestamp)
            .append("\\:%a %Y-%m-%d %H\\\\\\:%M\\\\\\:%S}")
            .append("': x=w-tw-20: y=h-th-20: fontcolor=white: fontsize=48: box=1: boxcolor=black@0.5").toString()
    }
}