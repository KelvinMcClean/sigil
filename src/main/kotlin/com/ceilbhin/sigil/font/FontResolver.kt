package com.ceilbhin.sigil.font

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*


class FontResolver {

    val BUNDLED_FONT_RESOURCE: String = "/fonts/RealVhsFontRegular-WyV0z.ttf"
    val EXTRACTED_FONT_NAME: String = "app-default-font.ttf"

    /**
     * Guarantees a font is available for FFmpeg.
     * Returns either the relative filename of the extracted font, or an escaped system fallback.
     */
    fun resolveFont(workingDir: Path): String {
        val targetFontPath = workingDir.resolve(EXTRACTED_FONT_NAME)

        // 1. Try to extract our bundled font from the JAR resources to our working directory
        if (!Files.exists(targetFontPath)) {
            try {
                Files.createDirectories(workingDir)
                FontResolver::class.java.getResourceAsStream(BUNDLED_FONT_RESOURCE).use { `is` ->
                    if (`is` != null) {
                        Files.copy(`is`, targetFontPath, StandardCopyOption.REPLACE_EXISTING)
                        // Since FFmpeg executes inside 'workingDir', we can return just the filename!
                        return EXTRACTED_FONT_NAME
                    }
                }
            } catch (e: IOException) {
                System.err.println("Failed to extract bundled font, falling back to system fonts: " + e.message)
            }
        } else {
            // Already extracted
            return EXTRACTED_FONT_NAME
        }

        // 2. Fallback: If no bundled font exists, guess the OS default
        return getSystemFallbackFontPath()
    }

    private fun getSystemFallbackFontPath(): String {
        val os = System.getProperty("os.name").lowercase(Locale.getDefault())

        if (os.contains("win")) {
            // TRAP: FFmpeg uses colons (:) to split parameters.
            // On Windows, you MUST escape the drive colon (C\:) so FFmpeg doesn't misparse the path.
            return "C\\:/Windows/Fonts/arial.ttf"
        } else if (os.contains("mac")) {
            return "/Library/Fonts/Arial.ttf"
        } else {
            // Standard Debian/Ubuntu path
            return "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        }
    }
}