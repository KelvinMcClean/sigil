package com.ceilbhin.sigil.files

import com.ceilbhin.sigil.media.MediaConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Paths

@Service
class FileService(var mediaConfiguration: MediaConfiguration) {
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

        var fileName = "${mediaConfiguration.filenamePattern}${file.extension}"

        logger.info { "Transfering output to ${mediaConfiguration.baseDir}" }

    }
}