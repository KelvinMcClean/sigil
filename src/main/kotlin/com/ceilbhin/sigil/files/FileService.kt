package com.ceilbhin.sigil.files

import com.ceilbhin.sigil.batch.VideoJobContext
import com.ceilbhin.sigil.media.MediaConfiguration
import com.ceilbhin.sigil.timestamp.TimestampService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.ZoneId
import kotlin.io.path.createDirectories
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@Service
class FileService(var mediaConfiguration: MediaConfiguration, var timestampService: TimestampService, val videoJobContext: VideoJobContext) {
    private final val logger = KotlinLogging.logger {}

    @Throws(FileNotFoundException::class, IOException::class)
    fun saveFilesToTemp(id: String, files: Array<MultipartFile> ): String {

        val tmpDir = FileUtils.getTmpDir(id)
        logger.info { "Temporary directory for job $id: $tmpDir" }

        val tmpPath = Paths.get(tmpDir)
        FileUtils.transfer(files, tmpPath, id)
        logger.info { "Files saved: $tmpPath" }

        return tmpDir
    }

    fun transferFilesToFinalDestination(file: File) {

        val filenamePattern = SimpleDateFormat(mediaConfiguration.filenamePattern).format(file.lastModified())

        val filename = file.nameWithoutExtension

        var fileName = "${filenamePattern} - ${filename}.${file.extension}"

        logger.info { "Transfering output to ${mediaConfiguration.baseDir}" }
        val finalPath = Paths.get(mediaConfiguration.baseDir, fileName)
        val success = file.renameTo(finalPath.toFile())
        if (success) {
            logger.info { "File transferred to: $finalPath" }
        } else {
            logger.error { "Failed to transfer file to: $finalPath" }
        }
    }

    fun cleanupJob(directory: String) {
        var workingDir = File(directory)
        if (workingDir.exists()) {
            logger.info { "Cleaning up working directory: $workingDir" }
            workingDir.deleteRecursively()
        } else {
            logger.warn { "Working directory does not exist: $workingDir" }
        }
    }

    fun getFinalPath(): String {

        val resolvedTimestamp = timestampService.resolveTextTimestamp()
        val title = videoJobContext.title

        var fileName = "${resolvedTimestamp} - ${title}.mp4"
        val earlyTimestampForPath = timestampService.getEarliestTimestamp()
        val earlyDateForPath = SimpleDateFormat(mediaConfiguration.subDirPattern).format(earlyTimestampForPath)
        val finalPath = Paths.get(mediaConfiguration.baseDir, earlyDateForPath)
        finalPath.createDirectories()
        return finalPath.resolve(fileName).toString()
    }
}