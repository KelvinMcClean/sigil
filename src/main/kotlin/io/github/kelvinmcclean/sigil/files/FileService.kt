package io.github.kelvinmcclean.sigil.files

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.media.MediaConfiguration
import io.github.kelvinmcclean.sigil.timestamp.TimestampService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Paths
import java.text.SimpleDateFormat
import kotlin.io.path.createDirectories
import kotlin.time.Instant

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

    fun cleanupJob(directory: String) {
        val workingDir = File(directory)
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

        val fileName = mediaConfiguration.filenamePattern
            .replace("{date}", resolvedTimestamp)
            .replace("{title}", title)
            .trim() // Ensure no leading/trailing spaces
        val fileNameWithExtension = "$fileName.mp4"

        val earlyTimestampForPath = timestampService.getEarliestTimestamp()
        val earlyDateForPath = SimpleDateFormat(mediaConfiguration.subDirPattern)
            .format(Instant.fromEpochSeconds(earlyTimestampForPath).toEpochMilliseconds())

        val baseDir = Paths.get(mediaConfiguration.baseDir)
        val outputDir = baseDir.resolve(earlyDateForPath)
        outputDir.createDirectories()

        val finalPath = outputDir.resolve(fileNameWithExtension)
        logger.info { "Final path: $finalPath" }

        // Return as an absolute path to ensure FFmpeg can find it,
        // especially if working dir is different from output dir
        return finalPath.toAbsolutePath().toString()
    }
}
