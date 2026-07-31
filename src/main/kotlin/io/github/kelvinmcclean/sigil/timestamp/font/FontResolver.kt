package io.github.kelvinmcclean.sigil.timestamp.font

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale


class FontResolver {

    val BUNDLED_FONT_RESOURCE: String = "/fonts/RealVhsFont-Normalised.ttf"
    val EXTRACTED_FONT_NAME: String = "app-font.ttf"

    /**
     * Guarantees a font is available for FFmpeg.
     * Returns either the relative filename of the extracted font, or an escaped system fallback.
     */
    @Throws(IOException::class)
    fun resolveFont(workingDir: Path, userFont: String): String {
        val targetFontPath = workingDir.resolve(EXTRACTED_FONT_NAME)

        if (!Files.exists(targetFontPath)) {
            if (userFont.isNotEmpty() && Files.exists(Path.of(userFont))) {
                return Path.of(userFont).relativize(targetFontPath).toString()
            }
            try {
                return getBundledFont(targetFontPath)
            } catch (e: IOException) {
                System.err.println("Failed to extract bundled font, falling back to system fonts: " + e.message)
                throw e
            }
        } else {
            // Already extracted
            return EXTRACTED_FONT_NAME
        }
    }

    private fun getBundledFont(targetFontPath: Path): String {
        Files.createDirectories(targetFontPath.parent)
        FontResolver::class.java.getResourceAsStream(BUNDLED_FONT_RESOURCE).use { `is` ->
            if (`is` != null) {
                Files.copy(`is`, targetFontPath, StandardCopyOption.REPLACE_EXISTING)
                return EXTRACTED_FONT_NAME
            }
        }
        throw IOException("Failed to extract bundled font")
    }
}