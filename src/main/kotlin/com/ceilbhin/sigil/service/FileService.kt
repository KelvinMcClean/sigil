package com.ceilbhin.sigil.service

import com.ceilbhin.sigil.util.FileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Paths

@Service
class FileService {
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
}