package io.github.kelvinmcclean.sigil.files

import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FileUtils {
    companion object {

        @JvmStatic
        fun createConcatPath(tmpPath: Path,  fileCount: Int): Path {
            val lines: MutableList<String?> = ArrayList()

            for (i in 0..<fileCount) {
                val processedPath = "_processed_$i.mp4"

                // Escape single quotes in filenames if they exist and wrap the path
                val formattedLine = "file '" + processedPath.replace("'", "'\\''") + "'"
                lines.add(formattedLine)
            }

            // Write the list file to the temporary directory
            val listFilePath = tmpPath.resolve("_fileList.txt")
            Files.write(listFilePath, lines)

            return listFilePath
        }

        @JvmStatic
        fun getTmpDir(jobId: String): String {
            // Resolve rather than concatenate: java.io.tmpdir only ends with a separator on some platforms
            val sigilDir = Paths.get(System.getProperty("java.io.tmpdir"), "sigil", jobId)
            if (!Files.exists(sigilDir)) {
                Files.createDirectories(sigilDir)
            }
            return "$sigilDir${File.separator}"
        }


        @JvmStatic
        @Throws(IOException::class)
        fun transfer(files: Array<MultipartFile>, tmpPath: Path) {
            for ((i, element) in files.withIndex()) {
                val file = element
                // Use sanitized original filename instead of numeric index
                val safeName = sanitizeFilename(file.originalFilename ?: "video_$i.mp4")
                val destinationPath: Path = tmpPath.resolve(safeName)

                // Transfer the file stream directly to disk
                file.transferTo(destinationPath)
            }
        }

        @JvmStatic
        fun sanitizeFilename(filename: String): String {
            // Remove path separators and invalid characters for cross-platform safety
            return filename.replace("[/\\:*?\"<>|]+".toRegex(), "_")
        }
    }
}