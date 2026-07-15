package com.ceilbhin.sigil.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


class FileUtils {
    companion object {
        private final val logger = KotlinLogging.logger {}

        fun createConcatPath(tmpPath: Path, jobId: String, fileCount: Int): Path {
            val lines: MutableList<String?> = ArrayList<String?>()

            for (i in 0..<fileCount) {
                val processedPath = jobId + "_processed_" + i + ".mp4"


                // Escape single quotes in filenames if they exist, and wrap the path
                val formattedLine = "file '" + processedPath.replace("'", "'\\''") + "'"
                lines.add(formattedLine)
            }

            // Write the list file to the temporary directory
            val listFilePath = tmpPath.resolve(jobId + "_fileList.txt")
            Files.write(listFilePath, lines)

            return listFilePath
        }

        fun getTmpDir(jobId: String): String {
            val tmpDir = System.getProperty("java.io.tmpdir")
            val sigilDir = "${tmpDir}sigil/${jobId}"
            if (!Files.exists(Paths.get(sigilDir))) {
                Files.createDirectories(Paths.get(sigilDir))
            }
            return "$sigilDir/"
        }

        fun getWorkingDir(jobId: String): File {
            val tmpDir = getTmpDir(jobId)
            return File(tmpDir)
        }

        @Throws(IOException::class)
        fun transfer(files: Array<MultipartFile>, tmpPath: Path, jobId: String) {
            for ((i, element) in files.withIndex()) {
                val file = element
                // Construct the target path: e.g., /temp/12345-uuid_input_0.mp4
                val destinationPath: Path = tmpPath.resolve(jobId + "_input_" + i + ".mp4")

                // Transfer the file stream directly to disk
                file.transferTo(destinationPath)
            }
        }

        fun cleanupTempFiles(tmpDir: String, jobId: String, fileCount: Int, includeFinalOutput: Boolean) {
            try {
                // Delete original and processed clips, plus motion vector files
                logger.info { "Cleaning up temporary files for job: $jobId" }
                for (i in 0..<fileCount) {
                    logger.debug { "Cleaning temporary file with path: $tmpDir/${jobId}_input_$i.mp4" }
                    Files.deleteIfExists(Paths.get(tmpDir + "/"+ jobId + "_input_" + i + ".mp4"))
                    Files.deleteIfExists(Paths.get(tmpDir +"/"+ jobId + "_processed_" + i + ".mp4"))
                    Files.deleteIfExists(Paths.get(tmpDir +"/"+ jobId + "_transforms_" + i + ".trf"))
                }
                logger.info { "Intermediary temporary files cleaned for job $jobId" }

                // Delete the concatenation list
                Files.deleteIfExists(Paths.get(tmpDir +"/"+ jobId + "_fileList.txt"))

                logger.info { "Finished cleaning file list for job $jobId" }
                // Delete final output only if the job failed or after the user downloads it
                if (includeFinalOutput) {
                    Files.deleteIfExists(Paths.get(tmpDir +"/"+ jobId + "_final_output.mp4"))
                    logger.info { "Finished cleaning final output for job: $jobId" }
                }
            } catch (e: IOException) {
                // Log the warning, but don't crash the thread over a missed cleanup
                System.err.println("Failed to clean up some temporary files for job " + jobId + ": " + e.message)
            }
        }
    }
}